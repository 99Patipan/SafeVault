package app.safevault.logic

import app.safevault.data.EntryContent
import app.safevault.data.VaultEntry
import org.junit.Assert.*
import org.junit.Test

class VaultAuditTest {
    private fun e(id: Long, pw: String) = VaultEntry(id, 0, 0, EntryContent(service = "s$id", password = pw))

    @Test fun findsReusedPasswordsInGroups() {
        val r = VaultAudit.analyze(listOf(e(3, "Gk7#mQ2x9Lp!vR"), e(1, "Same-Pass-#9xQ!"), e(2, "Same-Pass-#9xQ!"), e(4, "Same-Pass-#9xQ!")))
        assertEquals(listOf(listOf(1L, 2L, 4L)), r.duplicateGroups)
        assertEquals(setOf(1L, 2L, 4L), r.duplicateIds)
    }

    @Test fun findsWeakPasswords() {
        val r = VaultAudit.analyze(listOf(e(1, "123456"), e(2, "Gk7#mQ2x9Lp!vR"), e(3, "abc")))
        assertEquals(listOf(1L, 3L), r.weakIds)
        assertTrue(r.hasIssues)
    }

    @Test fun emptyPasswordsAreIgnored() {
        val r = VaultAudit.analyze(listOf(e(1, ""), e(2, "")))
        assertTrue(r.duplicateGroups.isEmpty()); assertTrue(r.weakIds.isEmpty()); assertFalse(r.hasIssues)
    }

    @Test fun cleanVaultHasNoIssues() {
        assertFalse(VaultAudit.analyze(listOf(e(1, "Gk7#mQ2x9Lp!vR"), e(2, "Zr4\$wN8p!Tq5#cB"))).hasIssues)
    }
}
