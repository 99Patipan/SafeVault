package app.safevault.crypto

import java.io.Closeable

/** Holds the 256-bit data-encryption key (DEK) in memory only. close() zeroes it. */
class VaultKey internal constructor(bytes: ByteArray) : Closeable {
    private var material: ByteArray? = bytes

    @get:Synchronized
    val isClosed: Boolean get() = material == null

    @Synchronized
    internal fun raw(): ByteArray = material ?: throw IllegalStateException("Vault is locked")

    /**
     * Gives [block] a **copy** of the raw DEK. The copy is zeroed after [block] returns.
     * Callers must not retain the array beyond the callback (biometric wrap is done inside the block).
     */
    @Synchronized
    fun <T> withKeyBytes(block: (ByteArray) -> T): T {
        val copy = raw().clone()
        try {
            return block(copy)
        } finally {
            copy.wipe()
        }
    }

    @Synchronized
    override fun close() {
        material?.wipe()
        material = null
    }

    companion object {
        /** Rebuilds a key from raw DEK bytes (copied; the caller should zero its own copy). */
        fun fromRaw(bytes: ByteArray): VaultKey {
            require(bytes.size == AesGcm.KEY_SIZE) { "DEK must be 256-bit" }
            return VaultKey(bytes.clone())
        }
    }
}
