package app.safevault.logic

import org.junit.Assert.*
import org.junit.Test

class PasswordGeneratorTest {
    private val lower = ('a'..'z').toSet(); private val upper = ('A'..'Z').toSet(); private val digits = ('0'..'9').toSet()

    @Test fun respectsLength() {
        for (n in listOf(8, 16, 20, 64)) assertEquals(n, PasswordGenerator.generate(GeneratorOptions(length = n)).length)
    }

    @Test fun containsEverySelectedClassEveryTime() {
        repeat(300) {
            val p = PasswordGenerator.generate(GeneratorOptions(length = 8))
            assertTrue(p.any { it in lower }); assertTrue(p.any { it in upper })
            assertTrue(p.any { it in digits }); assertTrue(p.any { !it.isLetterOrDigit() })
        }
    }

    @Test fun neverUsesUnselectedClasses() {
        repeat(100) {
            val p = PasswordGenerator.generate(GeneratorOptions(length = 30, upper = false, symbols = false))
            assertTrue(p.all { it in lower || it in digits })
        }
    }

    @Test fun excludesAmbiguousCharacters() {
        repeat(100) {
            val p = PasswordGenerator.generate(GeneratorOptions(length = 64, excludeAmbiguous = true))
            assertTrue(p.none { it in "O0oIl1|" })
        }
    }

    @Test fun usesWholeAlphabet() {
        val seen = HashSet<Char>()
        repeat(200) { PasswordGenerator.generate(GeneratorOptions(length = 30, upper = false, digits = false, symbols = false)).forEach(seen::add) }
        assertEquals(lower, seen)
    }

    @Test fun outputsDiffer() {
        assertNotEquals(PasswordGenerator.generate(GeneratorOptions()), PasswordGenerator.generate(GeneratorOptions()))
    }

    @Test fun rejectsInvalidOptions() {
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(GeneratorOptions(length = 7)) }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(GeneratorOptions(length = 65)) }
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generate(GeneratorOptions(lower = false, upper = false, digits = false, symbols = false))
        }
    }
}
