package app.safevault.platform

import app.safevault.crypto.VaultFormatException

/** Layout: version(1) | iv(12) | AES-GCM(DEK)(32 + 16 tag). The DEK copy is wrapped by a biometric-bound Keystore key. */
object BiometricBlob {
    private const val VERSION: Byte = 1
    private const val IV_SIZE = 12
    private const val CT_SIZE = 32 + 16
    const val SIZE = 1 + IV_SIZE + CT_SIZE

    fun build(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size == IV_SIZE && ciphertext.size == CT_SIZE) { "Bad biometric blob parts" }
        return byteArrayOf(VERSION) + iv + ciphertext
    }

    fun parse(blob: ByteArray): Pair<ByteArray, ByteArray> {
        if (blob.size != SIZE || blob[0] != VERSION) throw VaultFormatException("Bad biometric blob")
        return blob.copyOfRange(1, 1 + IV_SIZE) to blob.copyOfRange(1 + IV_SIZE, SIZE)
    }
}
