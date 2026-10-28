package app.safevault.crypto

import org.junit.Assert.*
import org.junit.Test

class VaultKeyTest {
    @Test fun fromRawCopiesAndExposesBytes() {
        val raw = ByteArray(32) { it.toByte() }
        val k = VaultKey.fromRaw(raw)
        raw.fill(0)
        assertArrayEquals(ByteArray(32) { it.toByte() }, k.withKeyBytes { it.clone() })
    }

    @Test fun fromRawRejectsWrongSize() {
        assertThrows(IllegalArgumentException::class.java) { VaultKey.fromRaw(ByteArray(16)) }
    }

    @Test fun withKeyBytesFailsWhenClosed() {
        val k = VaultKey.fromRaw(ByteArray(32)); k.close()
        assertThrows(IllegalStateException::class.java) { k.withKeyBytes { } }
    }
}
