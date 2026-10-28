package app.safevault.crypto

import java.nio.CharBuffer
import java.text.Normalizer

internal fun ByteArray.wipe() = fill(0)

/**
 * Password -> UTF-8 bytes after NFKC normalization, so the same typed password yields the same key
 * regardless of Unicode composition (important for Thai combining marks / full-width input).
 * Limitation: Normalizer needs a String, which the JVM cannot wipe.
 */
internal fun CharArray.toUtf8Nfkc(): ByteArray {
    val normalized = Normalizer.normalize(String(this), Normalizer.Form.NFKC)
    val buf = Charsets.UTF_8.encode(CharBuffer.wrap(normalized))
    val out = ByteArray(buf.remaining())
    buf.get(out)
    if (buf.hasArray()) buf.array().fill(0)
    return out
}
