package app.safevault.logic

import kotlin.math.ln

enum class Strength { VERY_WEAK, WEAK, FAIR, STRONG }
enum class Reason { TOO_SHORT, COMMON, REPETITIVE, SEQUENTIAL, LOW_VARIETY }

class StrengthResult(val strength: Strength, val bits: Double, val reasons: Set<Reason>)

/**
 * Local heuristic estimator (length x character variety, minus repeats/sequences, plus a small common-password check).
 * It is NOT zxcvbn: it cannot detect dictionary phrases or personal info, so treat "STRONG" as "no obvious weakness".
 */
object PasswordStrength {
    private val COMMON = setOf(
        "password", "passw0rd", "123456", "12345678", "123456789", "qwerty", "qwertyuiop", "iloveyou", "admin",
        "welcome", "letmein", "monkey", "dragon", "abc123", "111111", "123123", "sunshine", "princess", "football",
        "baseball", "login", "starwars", "master", "shadow", "superman", "michael", "trustno", "hello", "freedom",
        "whatever", "qazwsx", "access", "flower", "mustang", "batman", "secret", "computer", "internet", "changeme",
        "default", "guest", "root", "test", "pass", "p4ssw0rd", "zaq12wsx", "asdfgh", "asdfghjkl", "zxcvbn",
    )

    fun evaluate(pw: CharSequence): StrengthResult {
        val reasons = mutableSetOf<Reason>()
        val s = pw.toString()
        if (s.isEmpty()) return StrengthResult(Strength.VERY_WEAK, 0.0, setOf(Reason.TOO_SHORT))

        val lettersOnly = s.lowercase().filter { it.isLetter() }
        val lower = s.lowercase()
        if (lower in COMMON || (lettersOnly.length >= 4 && lettersOnly in COMMON) ||
            COMMON.any { it.length >= 6 && lower.startsWith(it) && lower.length <= it.length + 4 }
        ) reasons += Reason.COMMON

        var pool = 0
        if (s.any { it in 'a'..'z' }) pool += 26
        if (s.any { it in 'A'..'Z' }) pool += 26
        if (s.any { it in '0'..'9' }) pool += 10
        if (s.any { it.code < 128 && !it.isLetterOrDigit() }) pool += 33
        if (s.any { it.code >= 128 }) pool += 80
        if (pool == 0) pool = 10
        if (listOf(s.any { it.isLowerCase() }, s.any { it.isUpperCase() }, s.any { it.isDigit() },
                s.any { !it.isLetterOrDigit() }).count { it } <= 1 && s.none { it.code >= 128 }
        ) reasons += Reason.LOW_VARIETY

        var effective = s.length
        val block = repeatedBlockLength(s)
        if (block != null) { reasons += Reason.REPETITIVE; effective = block }
        else {
            val rep = repeatedRunPenalty(s)
            val seq = sequentialPenalty(s)
            if (rep > 0) reasons += Reason.REPETITIVE
            if (seq > 0) reasons += Reason.SEQUENTIAL
            effective = (s.length - rep - seq).coerceAtLeast(0)
        }
        if (s.length < 12) reasons += Reason.TOO_SHORT

        val bits = effective * (ln(pool.toDouble()) / ln(2.0))
        var strength = when {
            bits < 28 -> Strength.VERY_WEAK
            bits < 36 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            else -> Strength.STRONG
        }
        if (Reason.COMMON in reasons) strength = Strength.VERY_WEAK
        return StrengthResult(strength, bits, reasons)
    }

    /** Master password: >= 12 chars, >= 60 estimated bits, and none of the obvious-weakness flags. */
    fun isAcceptableMaster(pw: CharSequence): Boolean {
        if (pw.length < 12) return false
        val r = evaluate(pw)
        return r.bits >= 60 && r.strength == Strength.STRONG &&
            r.reasons.none { it == Reason.COMMON || it == Reason.REPETITIVE || it == Reason.SEQUENTIAL }
    }

    private fun repeatedBlockLength(s: String): Int? {
        for (len in 1..s.length / 2) {
            if (s.length % len != 0) continue
            val block = s.substring(0, len)
            if (s.chunked(len).all { it == block }) return len
        }
        return null
    }

    private fun repeatedRunPenalty(s: String): Int {
        var penalty = 0; var run = 1
        for (i in 1..s.length) {
            if (i < s.length && s[i] == s[i - 1]) run++
            else { if (run >= 3) penalty += run - 1; run = 1 }
        }
        return penalty
    }

    private fun sequentialPenalty(s: String): Int {
        var penalty = 0; var i = 0
        while (i < s.length - 1) {
            val d = s[i + 1].code - s[i].code
            if (d == 1 || d == -1) {
                var j = i + 1
                while (j + 1 < s.length && s[j + 1].code - s[j].code == d) j++
                val runLen = j - i + 1
                if (runLen >= 4) penalty += runLen - 1
                i = j
            } else i++
        }
        return penalty
    }
}
