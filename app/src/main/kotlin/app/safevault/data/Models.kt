package app.safevault.data

object Categories {
    const val SOCIAL = "social"; const val EMAIL = "email"; const val BANK = "bank"
    const val WORK = "work"; const val WEB = "web"; const val OTHER = "other"
    val ALL = listOf(SOCIAL, EMAIL, BANK, WORK, WEB, OTHER)
}

/** Everything secret about an entry. toString() is redacted so it cannot leak into logs. */
data class EntryContent(
    val category: String = Categories.OTHER,
    val service: String = "",
    val url: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val notes: String = "",
) {
    internal fun fields() = listOf(category, service, url, username, email, password, notes)
    override fun toString() = "EntryContent(<redacted>)"
}

class VaultEntry(val id: Long, val createdAt: Long, val updatedAt: Long, val content: EntryContent) {
    override fun toString() = "VaultEntry(id=$id, <redacted>)"
}

class LoadResult(val entries: List<VaultEntry>, val corruptedIds: List<Long>)

/** An entry to import with its original timestamps. */
class ImportItem(val content: EntryContent, val createdAt: Long, val updatedAt: Long) {
    override fun toString() = "ImportItem(<redacted>)"
}
