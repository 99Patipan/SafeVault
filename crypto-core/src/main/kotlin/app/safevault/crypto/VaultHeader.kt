package app.safevault.crypto

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * Binary layout (big-endian):
 *  "SVLT"(4) | version(1) | kdfId(1)=1 Argon2id | memKiB(4) | iterations(4) | parallelism(1)
 *  | saltLen(1) | salt | wrappedLen(2) | wrappedDek (nonce||ct||tag, always 60 bytes)
 * Everything before wrappedLen is used as AAD when wrapping the DEK, so parameters are authenticated.
 */
class VaultHeader(
    val version: Int,
    val params: KdfParams,
    val salt: ByteArray,
    val wrappedDek: ByteArray,
) {
    fun serialize(): ByteArray {
        val prefix = prefix(params, salt)
        return ByteBuffer.allocate(prefix.size + 2 + wrappedDek.size)
            .put(prefix).putShort(wrappedDek.size.toShort()).put(wrappedDek).array()
    }

    internal fun aad(): ByteArray = prefix(params, salt)

    companion object {
        const val VERSION = 1
        const val SALT_SIZE = 16
        const val WRAPPED_DEK_SIZE = AesGcm.NONCE_SIZE + AesGcm.KEY_SIZE + AesGcm.TAG_SIZE
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
        private const val KDF_ARGON2ID = 1

        internal fun prefix(params: KdfParams, salt: ByteArray): ByteArray =
            ByteBuffer.allocate(MAGIC.size + 1 + 1 + 4 + 4 + 1 + 1 + salt.size)
                .put(MAGIC).put(VERSION.toByte()).put(KDF_ARGON2ID.toByte())
                .putInt(params.memoryKiB).putInt(params.iterations).put(params.parallelism.toByte())
                .put(salt.size.toByte()).put(salt).array()

        fun parse(bytes: ByteArray): VaultHeader {
            try {
                val b = ByteBuffer.wrap(bytes)
                val magic = ByteArray(4).also { b.get(it) }
                if (!magic.contentEquals(MAGIC)) throw VaultFormatException("Bad magic")
                val version = b.get().toInt() and 0xFF
                if (version != VERSION) throw VaultFormatException("Unsupported version $version")
                if ((b.get().toInt() and 0xFF) != KDF_ARGON2ID) throw VaultFormatException("Unsupported KDF")
                val mem = b.getInt()
                val iter = b.getInt()
                val par = b.get().toInt() and 0xFF
                val saltLen = b.get().toInt() and 0xFF
                if (saltLen != SALT_SIZE) throw VaultFormatException("Bad salt length")
                val salt = ByteArray(saltLen).also { b.get(it) }
                val wrappedLen = b.getShort().toInt() and 0xFFFF
                if (wrappedLen != WRAPPED_DEK_SIZE || b.remaining() != wrappedLen) {
                    throw VaultFormatException("Bad wrapped key length")
                }
                val wrapped = ByteArray(wrappedLen).also { b.get(it) }
                return VaultHeader(version, KdfParams(mem, iter, par), salt, wrapped)
            } catch (e: BufferUnderflowException) {
                throw VaultFormatException("Truncated header")
            }
        }
    }
}
