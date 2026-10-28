package app.safevault.crypto

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class AesGcmTest {
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val aad = "ctx".toByteArray()

    @Test fun roundTripThai() {
        val pt = "รหัสผ่านลับ 🔐 p@ss".toByteArray()
        assertArrayEquals(pt, AesGcm.decrypt(key, AesGcm.encrypt(key, pt, aad), aad))
    }

    @Test fun nonceNeverRepeatsAcrossManyEncryptions() {
        val seen = HashSet<List<Byte>>()
        repeat(20_000) { seen += AesGcm.encrypt(key, byteArrayOf(1), aad).copyOf(12).toList() }
        assertEquals(20_000, seen.size)
    }

    @Test fun samePlaintextGivesDifferentCiphertext() {
        val a = AesGcm.encrypt(key, byteArrayOf(1, 2, 3), aad)
        val b = AesGcm.encrypt(key, byteArrayOf(1, 2, 3), aad)
        assertFalse(a.contentEquals(b))
    }

    @Test fun anyBitFlipIsRejected() {
        val blob = AesGcm.encrypt(key, "hello".toByteArray(), aad)
        for (i in blob.indices) for (bit in 0..7) {
            val m = blob.clone(); m[i] = (m[i].toInt() xor (1 shl bit)).toByte()
            assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, m, aad) }
        }
    }

    @Test fun wrongKeyWrongAadAndTruncationRejected() {
        val blob = AesGcm.encrypt(key, "x".toByteArray(), aad)
        val other = key.clone().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(other, blob, aad) }
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, blob, "other".toByteArray()) }
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, blob.copyOf(blob.size - 1), aad) }
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, ByteArray(5), aad) }
    }

    @Test fun rejectsNon256BitKey() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.encrypt(ByteArray(16), byteArrayOf(), aad) }
    }
}
