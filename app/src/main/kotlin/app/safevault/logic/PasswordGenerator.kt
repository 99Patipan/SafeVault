package app.safevault.logic

import java.security.SecureRandom

data class GeneratorOptions(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
)

object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "O0oIl1|"

    /** Uniform (unbiased) selection from a CSPRNG; guarantees at least one character of every selected class. */
    fun generate(o: GeneratorOptions, random: SecureRandom = SecureRandom()): String {
        require(o.length in MIN_LENGTH..MAX_LENGTH) { "Length out of range" }
        val classes = listOfNotNull(
            LOWER.takeIf { o.lower }, UPPER.takeIf { o.upper },
            DIGITS.takeIf { o.digits }, SYMBOLS.takeIf { o.symbols },
        ).map { set -> if (o.excludeAmbiguous) set.filter { it !in AMBIGUOUS } else set }
        require(classes.isNotEmpty()) { "Select at least one character type" }
        val all = classes.joinToString("")
        val out = CharArray(o.length)
        classes.forEachIndexed { i, set -> out[i] = set[random.nextInt(set.length)] }
        for (i in classes.size until out.size) out[i] = all[random.nextInt(all.length)]
        for (i in out.size - 1 downTo 1) { // Fisher-Yates
            val j = random.nextInt(i + 1)
            val t = out[i]; out[i] = out[j]; out[j] = t
        }
        return String(out).also { out.fill('\u0000') }
    }
}
