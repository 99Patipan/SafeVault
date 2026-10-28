package app.safevault.logic

import app.safevault.data.VaultEntry

class AuditResult(val duplicateGroups: List<List<Long>>, val weakIds: List<Long>) {
    val duplicateIds: Set<Long> get() = duplicateGroups.flatten().toSet()
    val hasIssues: Boolean get() = duplicateGroups.isNotEmpty() || weakIds.isNotEmpty()
}

/** Local-only analysis of reused and weak passwords. Nothing leaves memory. */
object VaultAudit {
    fun analyze(entries: List<VaultEntry>): AuditResult {
        val withPw = entries.filter { it.content.password.isNotEmpty() }
        val groups = withPw.groupBy { it.content.password }.values
            .filter { it.size >= 2 }
            .map { g -> g.map { it.id }.sorted() }
            .sortedBy { it.first() }
        val weak = withPw
            .filter { PasswordStrength.evaluate(it.content.password).strength <= Strength.WEAK }
            .map { it.id }.sorted()
        return AuditResult(groups, weak)
    }
}
