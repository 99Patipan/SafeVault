package app.safevault.crypto

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class HeaderProtectorTest {
    private fun keyOf(b: Int) = SecretKeySpec(ByteArray(32) { b.toByte() }, "AES")
    private val p = KeyHeaderProtector({ keyOf(1) })
    private val data = "header-bytes-ไทย".toByteArray()

    @Test fun roundTrip() = assertArrayEquals(data, p.unprotect(p.protect(data)))

    @Test fun freshIvEveryTime() = assertFalse(p.protect(data).contentEquals(p.protect(data)))

    @Test fun outputDoesNotContainPlaintext() {
        assertFalse(String(p.protect(data), Charsets.ISO_8859_1).contains("header-bytes"))
    }

    @Test fun everyBitFlipRejected() {
        val blob = p.protect(data)
        for (i in blob.indices) for (bit in 0..7) {
            val m = blob.clone(); m[i] = (m[i].toInt() xor (1 shl bit)).toByte()
            assertThrows("byte $i bit $bit", DeviceKeyUnavailableException::class.java) { p.unprotect(m) }
        }
    }

    @Test fun otherDeviceKeyRejected() {
        val blob = p.protect(data)
        assertThrows(DeviceKeyUnavailableException::class.java) { KeyHeaderProtector({ keyOf(2) }).unprotect(blob) }
    }

    @Test fun truncatedAndEmptyRejected() {
        val blob = p.protect(data)
        assertThrows(DeviceKeyUnavailableException::class.java) { p.unprotect(ByteArray(0)) }
        assertThrows(DeviceKeyUnavailableException::class.java) { p.unprotect(blob.copyOf(blob.size - 1)) }
    }

    @Test fun differentAadRejected() {
        val blob = p.protect(data)
        val other = KeyHeaderProtector({ keyOf(1) }, "other".toByteArray())
        assertThrows(DeviceKeyUnavailableException::class.java) { other.unprotect(blob) }
    }
}
