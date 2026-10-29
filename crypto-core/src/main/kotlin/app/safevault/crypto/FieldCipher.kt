package app.safevault.crypto

import java.nio.ByteBuffer

/**
 * Encrypts one secret blob under the vault DEK.
 *
 * AAD binds ciphertext to (format version, record id, field name) so swapping blobs between rows
 * or columns fails authentication.
 *
 * v2 AAD (current writes): version(1)=2 | recordId(8) | fieldLen(2 BE) | field UTF-8
 * v1 AAD (read fallback):  version(1)=1 | recordId(8) | field UTF-8
 * Length-prefixing the field name removes theoretical ambiguity if field labels were ever concatenated.
 */
object FieldCipher {
    const val FORMAT_VERSION = 2
    private const val LEGACY_VERSION = 1
    private const val MAX_FIELD_NAME_BYTES = 256

    fun encrypt(key: VaultKey, recordId: Long, field: String, plaintext: String): ByteArray {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        try {
            return encryptBytes(key, recordId, field, bytes)
        } finally {
            bytes.wipe()
        }
    }

    fun decrypt(key: VaultKey, recordId: Long, field: String, blob: ByteArray): String {
        val bytes = decryptBytes(key, recordId, field, blob)
        try {
            return String(bytes, Charsets.UTF_8)
        } finally {
            bytes.wipe()
        }
    }

    fun encryptBytes(key: VaultKey, recordId: Long, field: String, plaintext: ByteArray): ByteArray =
        AesGcm.encrypt(key.raw(), plaintext, aadV2(recordId, field))

    /**
     * Decrypts a blob written by this or the previous format.
     * Caller owns (and should zero) the returned plaintext.
     */
    fun decryptBytes(key: VaultKey, recordId: Long, field: String, blob: ByteArray): ByteArray {
        return try {
            AesGcm.decrypt(key.raw(), blob, aadV2(recordId, field))
        } catch (e: AuthenticationFailedException) {
            // Migration path: entries sealed before the v2 AAD change.
            AesGcm.decrypt(key.raw(), blob, aadV1(recordId, field))
        }
    }

    private fun aadV2(recordId: Long, field: String): ByteArray {
        val f = field.toByteArray(Charsets.UTF_8)
        require(f.size in 1..MAX_FIELD_NAME_BYTES) { "Field name length out of range" }
        return ByteBuffer.allocate(1 + 8 + 2 + f.size)
            .put(FORMAT_VERSION.toByte())
            .putLong(recordId)
            .putShort(f.size.toShort())
            .put(f)
            .array()
    }

    private fun aadV1(recordId: Long, field: String): ByteArray {
        val f = field.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(1 + 8 + f.size)
            .put(LEGACY_VERSION.toByte())
            .putLong(recordId)
            .put(f)
            .array()
    }
}
