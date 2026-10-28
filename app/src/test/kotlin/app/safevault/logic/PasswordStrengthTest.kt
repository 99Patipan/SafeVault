package app.safevault.logic

import org.junit.Assert.*
import org.junit.Test

class PasswordStrengthTest {
    private fun r(p: String) = PasswordStrength.evaluate(p)

    @Test fun emptyIsVeryWeak() = assertEquals(Strength.VERY_WEAK, r("").strength)

    @Test fun commonPasswordsAreVeryWeakEvenWithDecoration() {
        for (p in listOf("password", "123456", "qwerty", "Password123!", "iloveyou99")) {
            assertEquals(p, Strength.VERY_WEAK, r(p).strength)
            assertTrue(p, Reason.COMMON in r(p).reasons)
        }
    }

    @Test fun repetitionIsFlagged() {
        assertTrue(Reason.REPETITIVE in r("aaaaaaaaaaaa").reasons)
        assertEquals(Strength.VERY_WEAK, r("aaaaaaaaaaaa").strength)
        assertTrue(Reason.REPETITIVE in r("xyzxyzxyzxyz").reasons)
    }

    @Test fun sequencesAreFlagged() {
        assertTrue(Reason.SEQUENTIAL in r("abcd1234efgh").reasons)
        assertTrue(r("abcd1234efgh").strength <= Strength.WEAK)
    }

    @Test fun shortPasswordsAreWeak() {
        assertTrue(Reason.TOO_SHORT in r("Ab1!").reasons)
        assertTrue(r("Ab1!").strength <= Strength.WEAK)
    }

    @Test fun randomLookingLongPasswordIsStrong() {
        assertEquals(Strength.STRONG, r("Gk7#mQ2x9Lp!vR").strength)
    }

    @Test fun masterRequirements() {
        assertTrue(PasswordStrength.isAcceptableMaster("Gk7#mQ2x9Lp!vR"))
        assertTrue(PasswordStrength.isAcceptableMaster("correct horse battery staple"))
        assertFalse("too short", PasswordStrength.isAcceptableMaster("Gk7#mQ2x9L!"))
        assertFalse("common", PasswordStrength.isAcceptableMaster("Password123!!!"))
        assertFalse("repetitive", PasswordStrength.isAcceptableMaster("aaaaaaaaaaaaaaaa"))
        assertFalse("empty", PasswordStrength.isAcceptableMaster(""))
    }

    @Test fun thaiPassphraseIsAccepted() {
        assertTrue(PasswordStrength.isAcceptableMaster("ฉันชอบกินข้าวผัดกะเพราไข่ดาว"))
    }
}
