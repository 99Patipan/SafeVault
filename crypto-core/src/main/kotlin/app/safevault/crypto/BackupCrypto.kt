package app.safevault.crypto

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * Encrypted backup container, independent of the master password and the device key.
 *
 * Layout (big-endian): "SVBK"(4) | version(1)=1 | kdfId(1)=1 Argon2id | memKiB(4) | iterations(4)
 *   | parallelism(1) | saltLen(1)=16 | salt | AES-256-GCM blob (nonce||ciphertext||tag)
 * Every byte before the GCM blob is passed as AAD, so parameters/salt/version cannot be altered unnoticed.
 */
object BackupCrypto {
    const val VERSION = 1
    const val SALT_SIZE = 16
    const val MAX_PLAINTEXT_BYTES = 64 * 1024 * 1024
    const val MAX_FILE_BYTES = MAX_PLAINTEXT_BYTES + 1024
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val KDF_ARGON2ID = 1
    private const val HEADER_SIZE = 4 + 1 + 1 + 4 + 4 + 1 + 1 + SALT_SIZE
    private const val MIN_FILE = HEADER_SIZE + AesGcm.NONCE_SIZE + AesGcm.TAG_SIZE

    fun seal(
        password: CharArray, plaintext: ByteArray,
        params: KdfParams = KdfParams.DEFAULT, policy: KdfPolicy = KdfPolicy.PRODUCTION,
        kdf: Kdf = Argon2idKdf, random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(password.isNotEmpty()) { "Backup password must not be empty" }
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Backup too large" }
        policy.check(params)
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val header = header(params, salt)
        val key = derive(kdf, password, salt, params)
        try {
            return header + AesGcm.encrypt(key, plaintext, header)
        } finally {
            key.wipe()
        }
    }

    /**
     * @throws VaultFormatException malformed/unsupported file or out-of-policy parameters (checked before any KDF work)
     * @throws AuthenticationFailedException wrong password or modified file
     */
    fun open(
        password: CharArray, file: ByteArray,
        policy: KdfPolicy = KdfPolicy.PRODUCTION, kdf: Kdf = Argon2idKdf,
    ): ByteArray {
        if (file.size < MIN_FILE || file.size > MAX_FILE_BYTES) throw VaultFormatException("Bad backup size")
        val (params, salt) = parseHeader(file)
        policy.check(params)
        if (password.isEmpty()) throw AuthenticationFailedException()
        val key = derive(kdf, password, salt, params)
        try {
            return AesGcm.decrypt(key, file.copyOfRange(HEADER_SIZE, file.size), file.copyOfRange(0, HEADER_SIZE))
        } finally {
            key.wipe()
        }
    }

    private fun parseHeader(file: ByteArray): Pair<KdfParams, ByteArray> {
        try {
            val b = ByteBuffer.wrap(file)
            val magic = ByteArray(4).also { b.get(it) }
            if (!magic.contentEquals(MAGIC)) throw VaultFormatException("Not a SafeVault backup")
            if ((b.get().toInt() and 0xFF) != VERSION) throw VaultFormatException("Unsupported backup version")
            if ((b.get().toInt() and 0xFF) != KDF_ARGON2ID) throw VaultFormatException("Unsupported KDF")
            val mem = b.getInt()
            val iter = b.getInt()
            val par = b.get().toInt() and 0xFF
            if ((b.get().toInt() and 0xFF) != SALT_SIZE) throw VaultFormatException("Bad salt length")
            val salt = ByteArray(SALT_SIZE).also { b.get(it) }
            return KdfParams(mem, iter, par) to salt
        } catch (e: BufferUnderflowException) {
            throw VaultFormatException("Truncated backup")
        }
    }

    private fun derive(kdf: Kdf, password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val pw = password.toUtf8Nfkc()
        try { return kdf.derive(pw, salt, params) } finally { pw.wipe() }
    }

    private fun header(p: KdfParams, salt: ByteArray): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC).put(VERSION.toByte()).put(KDF_ARGON2ID.toByte())
            .putInt(p.memoryKiB).putInt(p.iterations).put(p.parallelism.toByte())
            .put(SALT_SIZE.toByte()).put(salt).array()
}
