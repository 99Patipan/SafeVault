package app.safevault.crypto

import java.nio.ByteBuffer

/**
 * Encrypts one secret blob. AAD binds ciphertext to (format version, record id, field name),
 * so swapping blobs between rows or columns in the database fails authentication.
 */
object FieldCipher {
    const val FORMAT_VERSION = 1

    fun encrypt(key: VaultKey, recordId: Long, field: String, plaintext: String): ByteArray {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        try { return encryptBytes(key, recordId, field, bytes) } finally { bytes.wipe() }
    }

    fun decrypt(key: VaultKey, recordId: Long, field: String, blob: ByteArray): String {
        val bytes = decryptBytes(key, recordId, field, blob)
        try { return String(bytes, Charsets.UTF_8) } finally { bytes.wipe() }
    }

    fun encryptBytes(key: VaultKey, recordId: Long, field: String, plaintext: ByteArray): ByteArray =
        AesGcm.encrypt(key.raw(), plaintext, aad(recordId, field))

    /** Caller owns (and should zero) the returned plaintext. */
    fun decryptBytes(key: VaultKey, recordId: Long, field: String, blob: ByteArray): ByteArray =
        AesGcm.decrypt(key.raw(), blob, aad(recordId, field))

    private fun aad(recordId: Long, field: String): ByteArray {
        val f = field.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(1 + 8 + f.size).put(FORMAT_VERSION.toByte()).putLong(recordId).put(f).array()
    }
}
