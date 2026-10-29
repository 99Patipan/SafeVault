package app.safevault.crypto

import org.junit.Assert.*
import org.junit.Test

class VaultKeyringTest {
    // Tiny parameters purely so tests run fast. Never used in production code.
    private val testPolicy = KdfPolicy(64, 1024, 1, 3, 2)
    private val fast = KdfParams(64, 1, 1)
    private val keyring = VaultKeyring(policy = testPolicy)
    private val pw = "รหัสหลัก-Correct-Horse-9!".toCharArray()

    @Test fun createThenUnlockGivesSameKey() {
        val v = keyring.create(pw, fast)
        val k = keyring.unlock(pw, v.header)
        assertArrayEquals(v.key.raw(), k.raw())
    }

    @Test fun wrongPasswordRejected() {
        val v = keyring.create(pw, fast)
        assertThrows(UnlockFailedException::class.java) { keyring.unlock("wrong".toCharArray(), v.header) }
        assertThrows(UnlockFailedException::class.java) { keyring.unlock((String(pw) + " ").toCharArray(), v.header) }
    }

    @Test fun saltsAndKeysAreUniquePerVault() {
        val a = keyring.create(pw, fast); val b = keyring.create(pw, fast)
        assertFalse(a.header.salt.contentEquals(b.header.salt))
        assertFalse(a.key.raw().contentEquals(b.key.raw()))
        assertFalse(a.header.wrappedDek.contentEquals(b.header.wrappedDek))
    }

    @Test fun unicodeNormalizationGivesSamePasswordEquivalence() {
        val v = keyring.create("ｐａｓｓｗｏｒｄ１２３".toCharArray(), fast) // full-width
        keyring.unlock("password123".toCharArray(), v.header).close()
    }

    @Test fun headerSerializationRoundTrip() {
        val v = keyring.create(pw, fast)
        val parsed = VaultHeader.parse(v.header.serialize())
        assertEquals(fast, parsed.params)
        assertArrayEquals(v.key.raw(), keyring.unlock(pw, parsed).raw())
    }

    @Test fun everySingleBitFlipInHeaderIsRejected() {
        val v = keyring.create(pw, fast)
        val bytes = v.header.serialize()
        for (i in bytes.indices) {
            val m = bytes.clone(); m[i] = (m[i].toInt() xor 0x01).toByte()
            try {
                keyring.unlock(pw, VaultHeader.parse(m)).close()
                fail("Tampered byte $i was accepted")
            } catch (e: VaultException) { /* expected */ }
        }
    }

    @Test fun malformedHeadersRejected() {
        val b = keyring.create(pw, fast).header.serialize()
        assertThrows(VaultFormatException::class.java) { VaultHeader.parse(ByteArray(0)) }
        assertThrows(VaultFormatException::class.java) { VaultHeader.parse(b.copyOf(b.size - 1)) }
        assertThrows(VaultFormatException::class.java) { VaultHeader.parse(b + byteArrayOf(0)) }
    }

    @Test fun kdfDowngradeInHeaderRejected() {
        val v = keyring.create(pw, fast)
        val weak = VaultHeader(1, KdfParams(8, 1, 1), v.header.salt, v.header.wrappedDek)
        assertThrows(VaultFormatException::class.java) { keyring.unlock(pw, weak) }
    }

    @Test fun excessiveKdfCostRejectedBeforeRunning() {
        val v = keyring.create(pw, fast)
        val dos = VaultHeader(1, KdfParams(Int.MAX_VALUE, 3, 2), v.header.salt, v.header.wrappedDek)
        assertThrows(VaultFormatException::class.java) { keyring.unlock(pw, dos) }
    }

    @Test fun productionPolicyRejectsWeakParams() {
        assertThrows(VaultFormatException::class.java) { VaultKeyring().create(pw, KdfParams(1024, 1, 1)) }
    }

    @Test fun emptyPasswordRejected() {
        assertThrows(IllegalArgumentException::class.java) { keyring.create(CharArray(0), fast) }
    }

    @Test fun changePasswordKeepsDataReadable() {
        val v = keyring.create(pw, fast)
        val blob = FieldCipher.encrypt(v.key, 1, "password", "s3cret")
        val newPw = "new-Password-456".toCharArray()

        val changed = keyring.changePassword(pw, newPw, v.header)
        assertFalse(changed.header.salt.contentEquals(v.header.salt))
        assertThrows(UnlockFailedException::class.java) { keyring.unlock(pw, changed.header) }
        val k = keyring.unlock(newPw, changed.header)
        assertEquals("s3cret", FieldCipher.decrypt(k, 1, "password", blob))
    }

    @Test fun changePasswordWithWrongOldPasswordFails() {
        val v = keyring.create(pw, fast)
        assertThrows(UnlockFailedException::class.java) {
            keyring.changePassword("nope".toCharArray(), "x1".toCharArray(), v.header)
        }
    }

    @Test fun closedKeyIsUnusableAndZeroed() {
        val v = keyring.create(pw, fast)
        val ref = v.key.raw()
        v.key.close()
        assertTrue(v.key.isClosed)
        assertTrue(ref.all { it == 0.toByte() })
        assertThrows(IllegalStateException::class.java) { FieldCipher.encrypt(v.key, 1, "f", "x") }
    }

    @Test fun realArgon2idDefaultsWork() {
        val real = VaultKeyring()
        val v = real.create(pw)
        assertArrayEquals(v.key.raw(), real.unlock(pw, VaultHeader.parse(v.header.serialize())).raw())
        assertThrows(UnlockFailedException::class.java) { real.unlock("bad".toCharArray(), v.header) }
    }

    @Test fun rotateDekIssuesFreshKeyAndOldBlobsStopOpening() {
        val v0 = keyring.create(pw, fast)
        val blob = FieldCipher.encrypt(v0.key, 1, "password", "s3cret")
        val rot = keyring.beginRotateDek(pw, v0.header, fast)
        try {
            assertEquals("s3cret", FieldCipher.decrypt(rot.oldKey, 1, "password", blob))
            val unlocked = keyring.unlock(pw, rot.header)
            assertFalse(rot.oldKey.raw().contentEquals(unlocked.raw()))
            assertArrayEquals(rot.newKey.raw(), unlocked.raw())
            assertThrows(AuthenticationFailedException::class.java) { FieldCipher.decrypt(rot.newKey, 1, "password", blob) }
            unlocked.close()
        } finally {
            rot.close(); v0.key.close()
        }
    }

    @Test fun rotateDekRejectsWrongPassword() {
        val v0 = keyring.create(pw, fast)
        assertThrows(UnlockFailedException::class.java) { keyring.beginRotateDek("wrong-wrong-1".toCharArray(), v0.header, fast) }
        v0.key.close()
    }

    @Test fun withKeyBytesHandsOutACopyThatIsWiped() {
        val v = keyring.create(pw, fast)
        var leaked: ByteArray? = null
        val before = v.key.raw().clone()
        v.key.withKeyBytes { leaked = it }
        assertTrue(leaked!!.all { it == 0.toByte() })
        assertArrayEquals(before, v.key.raw())
        v.key.close()
    }
}
