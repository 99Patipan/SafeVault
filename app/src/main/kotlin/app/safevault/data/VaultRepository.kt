package app.safevault.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import app.safevault.crypto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * All secrets are encrypted before they reach Room and are decrypted only with an unlocked [VaultKey].
 * The vault header is additionally wrapped by a device-bound key ([HeaderProtector]).
 * Single-row writes are atomic in SQLite; multi-row writes run in one transaction (all-or-nothing).
 */
class VaultRepository(
    private val db: VaultDatabase,
    private val protector: HeaderProtector,
    private val keyring: VaultKeyring = VaultKeyring(),
    private val kdfParams: KdfParams = KdfParams.DEFAULT,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val dao get() = db.dao()

    suspend fun isInitialized(): Boolean = dao.header() != null

    /** @throws IllegalStateException if a vault already exists. */
    suspend fun createVault(password: CharArray): VaultKey {
        val created = withContext(Dispatchers.Default) { keyring.create(password, kdfParams) }
        try {
            db.withTransaction {
                check(dao.header() == null) { "Vault already exists" }
                dao.putMeta(headerRow(created.header))
            }
        } catch (e: Throwable) {
            created.key.close(); throw e
        }
        return created.key
    }

    /**
     * @throws UnlockFailedException wrong password or tampered header
     * @throws DeviceKeyUnavailableException device key lost/invalidated or stored header modified
     * @throws VaultFormatException header malformed
     */
    suspend fun unlock(password: CharArray): VaultKey {
        val header = loadHeader()
        return withContext(Dispatchers.Default) { keyring.unlock(password, header) }
    }

    /** Re-wraps the same DEK under a new password. Entries and biometric blob stay valid. Returns the unlocked key. */
    suspend fun changePassword(oldPassword: CharArray, newPassword: CharArray): VaultKey {
        val header = loadHeader()
        val changed = withContext(Dispatchers.Default) {
            keyring.changePassword(oldPassword, newPassword, header, kdfParams)
        }
        try {
            dao.putMeta(headerRow(changed.header)) // one atomic row replace: old or new header, never a mix
        } catch (e: Throwable) {
            changed.key.close(); throw e
        }
        return changed.key
    }

    suspend fun add(key: VaultKey, content: EntryContent): Long = addAll(key, listOf(content)).single()

    /** Atomic: if any item fails, nothing is stored. */
    suspend fun addAll(key: VaultKey, contents: List<EntryContent>): List<Long> {
        val now = clock()
        return db.withTransaction { contents.map { insertWithFreshId(key, it, now, now) } }
    }

    /** Atomic import preserving original timestamps; every item gets a fresh random id. */
    suspend fun importAll(key: VaultKey, items: List<ImportItem>): List<Long> =
        db.withTransaction { items.map { insertWithFreshId(key, it.content, it.createdAt, it.updatedAt) } }

    suspend fun update(key: VaultKey, id: Long, content: EntryContent) {
        db.withTransaction {
            val old = dao.entry(id) ?: throw NoSuchElementException("No entry $id")
            dao.update(seal(key, id, content, old.createdAt, clock()))
        }
    }

    suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    /** @throws AuthenticationFailedException / [VaultFormatException] if the row was modified. */
    suspend fun get(key: VaultKey, id: Long): VaultEntry? = dao.entry(id)?.let { open(key, it) }

    /** Rows that fail authentication are NOT silently dropped: their ids are reported in [LoadResult.corruptedIds]. */
    suspend fun getAll(key: VaultKey): LoadResult {
        val ok = ArrayList<VaultEntry>()
        val bad = ArrayList<Long>()
        for (e in dao.allEntries()) {
            try { ok += open(key, e) } catch (_: VaultException) { bad += e.id }
        }
        return LoadResult(ok, bad)
    }

    suspend fun count(): Int = dao.count()

    // ---- biometric-wrapped DEK copy (opaque blob produced by the platform layer) ----
    suspend fun getBiometricBlob(): ByteArray? = dao.meta(BIO_META_ID)
    suspend fun putBiometricBlob(blob: ByteArray) = dao.putMeta(VaultMetaEntity(BIO_META_ID, blob))
    suspend fun deleteBiometricBlob() = dao.deleteMetaById(BIO_META_ID)

    /** Irreversibly deletes every entry, the vault header and the biometric blob (UI must confirm first). */
    suspend fun wipeEverything() {
        db.withTransaction { dao.deleteAllEntries(); dao.deleteMeta() }
    }

    // ---- internals ----
    private fun headerRow(h: VaultHeader) = VaultMetaEntity(HEADER_META_ID, protector.protect(h.serialize()))

    private suspend fun loadHeader(): VaultHeader {
        val stored = dao.header() ?: throw IllegalStateException("No vault has been created")
        val plain = protector.unprotect(stored)
        try { return VaultHeader.parse(plain) } finally { plain.fill(0) }
    }

    private suspend fun insertWithFreshId(key: VaultKey, c: EntryContent, createdAt: Long, updatedAt: Long): Long {
        repeat(MAX_ID_ATTEMPTS) {
            val id = random.nextLong() and Long.MAX_VALUE
            if (id == 0L) return@repeat
            try {
                dao.insert(seal(key, id, c, createdAt, updatedAt))
                return id
            } catch (_: SQLiteConstraintException) { /* id collision: retry */ }
        }
        throw IllegalStateException("Could not allocate a record id")
    }

    private fun seal(key: VaultKey, id: Long, c: EntryContent, createdAt: Long, updatedAt: Long): EntryEntity {
        val plain = EntryCodec.encode(c)
        try {
            return EntryEntity(id, FieldCipher.encryptBytes(key, id, FIELD, plain), createdAt, updatedAt)
        } finally {
            plain.fill(0)
        }
    }

    private fun open(key: VaultKey, e: EntryEntity): VaultEntry {
        val plain = FieldCipher.decryptBytes(key, e.id, FIELD, e.blob)
        try {
            return VaultEntry(e.id, e.createdAt, e.updatedAt, EntryCodec.decode(plain))
        } finally {
            plain.fill(0)
        }
    }

    private companion object {
        const val FIELD = "entry"
        const val MAX_ID_ATTEMPTS = 8
        const val HEADER_META_ID = 0
        const val BIO_META_ID = 1
    }
}
