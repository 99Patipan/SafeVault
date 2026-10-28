package app.safevault.logic

import app.safevault.data.Categories
import app.safevault.data.EntryContent
import app.safevault.data.VaultEntry
import org.junit.Assert.*
import org.junit.Test

class EntrySearchTest {
    private fun e(id: Long, cat: String, service: String, user: String = "", url: String = "", notes: String = "") =
        VaultEntry(id, 0, 0, EntryContent(cat, service, url, user, "", "pw", notes))

    private val all = listOf(
        e(1, Categories.EMAIL, "Gmail", "somchai@example.com", "https://mail.google.com"),
        e(2, Categories.BANK, "ธนาคารกสิกร", "somying", notes = "บัญชีออมทรัพย์"),
        e(3, Categories.SOCIAL, "Facebook", "somchai.fb", "https://facebook.com"),
    )

    private fun ids(q: String, c: String? = null) = EntrySearch.filter(all, q, c).map { it.id }

    @Test fun blankQueryReturnsAllSortedByService() = assertEquals(listOf(3L, 1L, 2L), ids(""))
    @Test fun caseInsensitive() = assertEquals(listOf(1L), ids("GMAIL"))
    @Test fun matchesThai() = assertEquals(listOf(2L), ids("กสิกร"))
    @Test fun matchesNotesAndUrl() { assertEquals(listOf(2L), ids("ออมทรัพย์")); assertEquals(listOf(1L), ids("google")) }
    @Test fun allTermsMustMatch() { assertEquals(listOf(3L, 1L), ids("somchai")); assertEquals(listOf(3L), ids("somchai facebook")) }
    @Test fun categoryFilter() = assertEquals(listOf(2L), ids("som", Categories.BANK))
    @Test fun noMatch() = assertTrue(ids("zzz").isEmpty())

    @Test fun passwordIsNotSearchable() {
        assertTrue(EntrySearch.filter(all, "pw").isEmpty())
    }
}
