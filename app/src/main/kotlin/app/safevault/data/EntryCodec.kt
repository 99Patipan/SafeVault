package app.safevault.data

import app.safevault.crypto.VaultFormatException
import java.nio.ByteBuffer

/** Versioned, length-prefixed serialization of [EntryContent]. Strict decoder: no trailing data, bounded lengths. */
object EntryCodec {
    private const val VERSION = 1
    private const val FIELD_COUNT = 7
    const val MAX_FIELD_BYTES = 256 * 1024

    fun encode(c: EntryContent): ByteArray {
        val parts = c.fields().map { it.toByteArray(Charsets.UTF_8) }
        try {
            require(parts.all { it.size <= MAX_FIELD_BYTES }) { "Field too large" }
            val buf = ByteBuffer.allocate(1 + parts.sumOf { 4 + it.size })
            buf.put(VERSION.toByte())
            parts.forEach { buf.putInt(it.size).put(it) }
            return buf.array()
        } finally {
            parts.forEach { it.fill(0) }
        }
    }

    fun decode(bytes: ByteArray): EntryContent {
        val b = ByteBuffer.wrap(bytes)
        if (b.remaining() < 1 || b.get().toInt() != VERSION) throw VaultFormatException("Unsupported entry version")
        val f = List(FIELD_COUNT) {
            if (b.remaining() < 4) throw VaultFormatException("Truncated entry")
            val n = b.getInt()
            if (n < 0 || n > MAX_FIELD_BYTES || n > b.remaining()) throw VaultFormatException("Bad field length")
            val arr = ByteArray(n).also { b.get(it) }
            String(arr, Charsets.UTF_8).also { arr.fill(0) }
        }
        if (b.hasRemaining()) throw VaultFormatException("Trailing data")
        return EntryContent(f[0], f[1], f[2], f[3], f[4], f[5], f[6])
    }
}
