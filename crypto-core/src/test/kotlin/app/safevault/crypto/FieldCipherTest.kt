package app.safevault.crypto

import org.junit.Assert.*
import org.junit.Test

class FieldCipherTest {
    private val keyring = VaultKeyring(policy = KdfPolicy(64, 1024, 1, 3, 2))
    private val key = keyring.create("pw-123456".toCharArray(), KdfParams(64, 1, 1)).key

    @Test fun roundTripThai() {
        val b = FieldCipher.encrypt(key, 7, "username", "ผู้ใช้ทดสอบ")
        assertEquals("ผู้ใช้ทดสอบ", FieldCipher.decrypt(key, 7, "username", b))
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val b = FieldCipher.encrypt(key, 1, "password", "hunter2hunter2")
        assertFalse(String(b, Charsets.ISO_8859_1).contains("hunter2"))
    }

    @Test fun swappingBetweenRecordsIsDetected() {
        val b = FieldCipher.encrypt(key, 1, "password", "a")
        assertThrows(AuthenticationFailedException::class.java) { FieldCipher.decrypt(key, 2, "password", b) }
    }

    @Test fun swappingBetweenFieldsIsDetected() {
        val b = FieldCipher.encrypt(key, 1, "password", "a")
        assertThrows(AuthenticationFailedException::class.java) { FieldCipher.decrypt(key, 1, "username", b) }
    }

    @Test fun tamperedBlobIsDetected() {
        val b = FieldCipher.encrypt(key, 1, "notes", "note")
        b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte()
        assertThrows(AuthenticationFailedException::class.java) { FieldCipher.decrypt(key, 1, "notes", b) }
    }

    @Test fun wrongKeyIsDetected() {
        val other = keyring.create("pw-other-1".toCharArray(), KdfParams(64, 1, 1)).key
        val b = FieldCipher.encrypt(key, 1, "x", "v")
        assertThrows(AuthenticationFailedException::class.java) { FieldCipher.decrypt(other, 1, "x", b) }
    }

    @Test fun bytesRoundTripAndAadBinding() {
        val b = FieldCipher.encryptBytes(key, 5, "entry", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), FieldCipher.decryptBytes(key, 5, "entry", b))
        assertThrows(AuthenticationFailedException::class.java) { FieldCipher.decryptBytes(key, 6, "entry", b) }
    }
}

    @Test fun legacyV1AadStillDecrypts() {
        // Manually seal with v1 AAD shape to simulate pre-upgrade ciphertext.
        val field = "entry"
        val fBytes = field.toByteArray(Charsets.UTF_8)
        val aadV1 = java.nio.ByteBuffer.allocate(1 + 8 + fBytes.size)
            .put(1.toByte()).putLong(42L).put(fBytes).array()
        val blob = AesGcm.encrypt(key.raw(), "legacy-secret".toByteArray(), aadV1)
        assertEquals("legacy-secret", FieldCipher.decrypt(key, 42, field, blob))
    }

    @Test fun v2RejectsV1StyleFieldAmbiguitySwap() {
        val b = FieldCipher.encrypt(key, 1, "password", "a")
        assertThrows(AuthenticationFailedException::class.java) {
            FieldCipher.decrypt(key, 1, "passwor", b) // different field name must fail
        }
    }
