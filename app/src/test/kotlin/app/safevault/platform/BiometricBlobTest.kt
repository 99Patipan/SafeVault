package app.safevault.platform

import app.safevault.crypto.VaultFormatException
import org.junit.Assert.*
import org.junit.Test

class BiometricBlobTest {
    private val iv = ByteArray(12) { it.toByte() }
    private val ct = ByteArray(48) { (it + 50).toByte() }

    @Test fun roundTrip() {
        val blob = BiometricBlob.build(iv, ct)
        assertEquals(BiometricBlob.SIZE, blob.size)
        val (i, c) = BiometricBlob.parse(blob)
        assertArrayEquals(iv, i); assertArrayEquals(ct, c)
    }

    @Test fun rejectsWrongSizeAndVersion() {
        val blob = BiometricBlob.build(iv, ct)
        assertThrows(VaultFormatException::class.java) { BiometricBlob.parse(blob.copyOf(blob.size - 1)) }
        assertThrows(VaultFormatException::class.java) { BiometricBlob.parse(blob + byteArrayOf(0)) }
        assertThrows(VaultFormatException::class.java) { BiometricBlob.parse(blob.clone().also { it[0] = 9 }) }
        assertThrows(VaultFormatException::class.java) { BiometricBlob.parse(ByteArray(0)) }
    }

    @Test fun rejectsBadParts() {
        assertThrows(IllegalArgumentException::class.java) { BiometricBlob.build(ByteArray(8), ct) }
        assertThrows(IllegalArgumentException::class.java) { BiometricBlob.build(iv, ByteArray(10)) }
    }
}
