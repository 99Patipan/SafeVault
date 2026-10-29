package app.safevault.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.text.Normalizer
import java.util.Arrays

internal fun ByteArray.wipe() {
    Arrays.fill(this, 0.toByte())
}

internal fun CharArray.wipe() {
    Arrays.fill(this, '\u0000')
}

/**
 * Password -> UTF-8 bytes after NFKC normalization, so the same typed password yields the same key
 * regardless of Unicode composition (important for Thai combining marks / full-width input).
 *
 * Limitation: [Normalizer] and intermediate [String] cannot be wiped on the JVM; we still zero every
 * byte/char buffer we control (input copy, encoder output, CharBuffer backing array when available).
 */
internal fun CharArray.toUtf8Nfkc(): ByteArray {
    // Copy so we can wipe our working set even if the caller reuses the original array later.
    val working = this.clone()
    try {
        val normalized = Normalizer.normalize(String(working), Normalizer.Form.NFKC)
        val charBuf = CharBuffer.wrap(normalized)
        val buf = Charsets.UTF_8.encode(charBuf)
        val out = ByteArray(buf.remaining())
        buf.get(out)
        if (buf.hasArray()) Arrays.fill(buf.array(), 0.toByte())
        // Best-effort: clear the CharBuffer view range (String content itself is immutable).
        if (charBuf.hasArray()) {
            val arr = charBuf.array()
            val start = charBuf.arrayOffset() + charBuf.position()
            val end = start + charBuf.remaining()
            for (i in start until end) arr[i] = '\u0000'
        }
        return out
    } finally {
        working.wipe()
    }
}

/** Constant-time equality for equal-length secret arrays (avoids early-exit leaks on compares). */
internal fun ByteArray.secureEquals(other: ByteArray): Boolean {
    if (size != other.size) return false
    var diff = 0
    for (i in indices) diff = diff or (this[i].toInt() xor other[i].toInt())
    return diff == 0
}
