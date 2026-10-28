package app.safevault.logic

import app.safevault.data.VaultEntry

/** In-memory search over decrypted entries (there is no plaintext index on disk). All terms must match. */
object EntrySearch {
    fun filter(entries: List<VaultEntry>, query: String, category: String? = null): List<VaultEntry> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries
            .filter { category == null || it.content.category == category }
            .filter { e ->
                val c = e.content
                val hay = listOf(c.service, c.url, c.username, c.email, c.notes).joinToString("\n").lowercase()
                terms.all { hay.contains(it) }
            }
            .sortedBy { it.content.service.lowercase() }
    }
}
