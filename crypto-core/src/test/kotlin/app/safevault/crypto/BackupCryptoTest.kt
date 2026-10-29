package app.safevault.crypto

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class BackupCryptoTest {
    // Tiny parameters only so tests are fast; never used in production code.
    private val policy = KdfPolicy(64, 1024, 1, 3, 2)
    private val fast = KdfParams(64, 1, 1)
    private val pw = "รหัสไฟล์สำรอง-Backup-77!".toCharArray()
    private val data = "ข้อมูลลับ secret payload 🔐".toByteArray()

    private fun seal(p: CharArray = pw, d: ByteArray = data) = BackupCrypto.seal(p, d, fast, policy)
    private fun open(f: ByteArray, p: CharArray = pw) = BackupCrypto.open(p, f, policy)

    @Test fun roundTrip() = assertArrayEquals(data, open(seal()))
    @Test fun emptyPayloadRoundTrip() = assertArrayEquals(ByteArray(0), open(seal(d = ByteArray(0))))

    @Test fun fileDoesNotContainPlaintext() {
        assertFalse(String(seal(), Charsets.ISO_8859_1).contains("secret payload"))
    }

    @Test fun wrongPasswordRejected() {
        assertThrows(AuthenticationFailedException::class.java) { open(seal(), "wrong-password".toCharArray()) }
        assertThrows(AuthenticationFailedException::class.java) { open(seal(), CharArray(0)) }
    }

    @Test fun twoBackupsDifferInSaltAndCiphertext() {
        val a = seal(); val b = seal()
        assertFalse(a.copyOfRange(16, 32).contentEquals(b.copyOfRange(16, 32)))
        assertFalse(a.contentEquals(b))
    }

    @Test fun everySingleBitFlipRejected() {
        val f = seal()
        for (i in f.indices) for (bit in 0..7) {
            val m = f.clone(); m[i] = (m[i].toInt() xor (1 shl bit)).toByte()
            try { open(m); fail("byte $i bit $bit accepted") } catch (_: VaultException) {}
        }
    }

    @Test fun truncationAndExtensionRejected() {
        val f = seal()
        for (n in 0 until f.size) {
            try { open(f.copyOf(n)); fail("len $n accepted") } catch (_: VaultException) {}
        }
        try { open(f + byteArrayOf(0)); fail() } catch (_: VaultException) {}
    }

    @Test fun badMagicAndVersionRejected() {
        val f = seal()
        assertThrows(VaultFormatException::class.java) { open(f.clone().also { it[0] = 'X'.code.toByte() }) }
        assertThrows(VaultFormatException::class.java) { open(f.clone().also { it[4] = 2 }) }
    }

    @Test fun outOfPolicyParametersRejectedBeforeKdfRuns() {
        val f = seal()
        val huge = f.clone()
        ByteBuffer.wrap(huge).putInt(6, Int.MAX_VALUE) // memKiB field
        assertThrows(VaultFormatException::class.java) { open(huge) }
        val weak = f.clone()
        ByteBuffer.wrap(weak).putInt(6, 8)
        assertThrows(VaultFormatException::class.java) { open(weak) }
    }

    @Test fun productionPolicyRejectsWeakSealParams() {
        assertThrows(VaultFormatException::class.java) { BackupCrypto.seal(pw, data, KdfParams(1024, 1, 1)) }
    }

    @Test fun emptyPasswordRejectedOnSeal() {
        assertThrows(IllegalArgumentException::class.java) { seal(CharArray(0)) }
    }

    @Test fun realArgon2idDefaultsWork() {
        val f = BackupCrypto.seal(pw, data)
        assertArrayEquals(data, BackupCrypto.open(pw, f))
    }
}
