package app.safevault.crypto

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM via the platform JCA (no custom crypto). Output: nonce(12) || ciphertext || tag(16). */
object AesGcm {
    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_SIZE = 16
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    // One SecureRandom per call site is unnecessary; the JRE instance is seeded and thread-safe enough.
    // Still avoid a shared mutable counter of our own — only nextBytes is used.
    private val random = SecureRandom()

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be 256-bit" }
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(key, "AES")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE * 8, nonce))
            cipher.updateAAD(aad)
            return nonce + cipher.doFinal(plaintext)
        } finally {
            // SecretKeySpec does not expose destroy() on all API levels; best-effort only.
        }
    }

    /** Throws [AuthenticationFailedException] if the tag does not verify; never returns unauthenticated data. */
    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "Key must be 256-bit" }
        if (blob.size < NONCE_SIZE + TAG_SIZE) throw AuthenticationFailedException()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_SIZE * 8, blob, 0, NONCE_SIZE),
            )
            cipher.updateAAD(aad)
            return cipher.doFinal(blob, NONCE_SIZE, blob.size - NONCE_SIZE)
        } catch (e: AEADBadTagException) {
            throw AuthenticationFailedException()
        }
    }
}
