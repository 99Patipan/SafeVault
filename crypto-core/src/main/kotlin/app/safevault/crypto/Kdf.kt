package app.safevault.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        /**
         * Production default: Argon2id 64 MiB, t=4, p=1.
         * Above OWASP minimum (19 MiB / t=2) and slightly stronger than the previous t=3 default.
         * Low-end devices may take ~1–3s; that cost is intentional against offline guessing.
         */
        val DEFAULT = KdfParams(memoryKiB = 65536, iterations = 4, parallelism = 1)
    }
}

/** Bounds accepted for stored/imported parameters. Prevents downgrade attacks and memory-exhaustion DoS. */
class KdfPolicy(
    val minMemoryKiB: Int, val maxMemoryKiB: Int,
    val minIterations: Int, val maxIterations: Int,
    val maxParallelism: Int,
) {
    fun check(p: KdfParams) {
        if (p.memoryKiB !in minMemoryKiB..maxMemoryKiB ||
            p.iterations !in minIterations..maxIterations ||
            p.parallelism !in 1..maxParallelism
        ) throw VaultFormatException("KDF parameters outside allowed policy")
    }

    companion object {
        /**
         * Production floor raised: ≥ 19 MiB, ≥ 3 iterations (was 2).
         * Caps parallelism at 4 to limit cross-core DoS from crafted headers/backups.
         */
        val PRODUCTION = KdfPolicy(
            minMemoryKiB = 19_456,
            maxMemoryKiB = 262_144,
            minIterations = 3,
            maxIterations = 10,
            maxParallelism = 4,
        )
    }
}

interface Kdf {
    /** Returns a 32-byte key. */
    fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray
}

object Argon2idKdf : Kdf {
    override fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        val p = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKiB)
            .withParallelism(params.parallelism)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(p)
        val out = ByteArray(32)
        gen.generateBytes(password, out)
        return out
    }
}
