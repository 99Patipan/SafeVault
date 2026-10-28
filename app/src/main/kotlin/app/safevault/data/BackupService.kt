package app.safevault.data

import app.safevault.crypto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

class ExportResult(val file: ByteArray, val exported: Int, val skippedCorrupted: Int)
class ImportResult(val added: Int, val skippedDuplicates: Int)

/** Payload inside the encrypted backup: version | count | repeated (createdAt, updatedAt, len, EntryCodec bytes). */
internal object BackupPayload {
    private const val VERSION = 1
    private const val MAX_ENTRIES = 100_000
    private const val MAX_ENTRY_BYTES = 8 * EntryCodec.MAX_FIELD_BYTES
    private const val PER_ENTRY_OVERHEAD = 8 + 8 + 4

    fun encode(entries: List<VaultEntry>): ByteArray {
        val blobs = entries.map { EntryCodec.encode(it.content) }
        try {
            val buf = ByteBuffer.allocate(1 + 4 + blobs.sumOf { PER_ENTRY_OVERHEAD + it.size })
            buf.put(VERSION.toByte()).putInt(entries.size)
            entries.forEachIndexed { i, e -> buf.putLong(e.createdAt).putLong(e.updatedAt).putInt(blobs[i].size).put(blobs[i]) }
            return buf.array()
        } finally {
            blobs.forEach { it.fill(0) }
        }
    }

    fun decode(bytes: ByteArray): List<ImportItem> {
        try {
            val b = ByteBuffer.wrap(bytes)
            if (b.get().toInt() != VERSION) throw VaultFormatException("Unsupported backup payload version")
            val count = b.getInt()
            if (count < 0 || count > MAX_ENTRIES || count.toLong() * PER_ENTRY_OVERHEAD > b.remaining()) {
                throw VaultFormatException("Bad entry count")
            }
            val out = ArrayList<ImportItem>(count)
            repeat(count) {
                val created = b.getLong(); val updated = b.getLong(); val len = b.getInt()
                if (len < 0 || len > MAX_ENTRY_BYTES || len > b.remaining()) throw VaultFormatException("Bad entry length")
                val raw = ByteArray(len).also { b.get(it) }
                try { out += ImportItem(EntryCodec.decode(raw), created, updated) } finally { raw.fill(0) }
            }
            if (b.hasRemaining()) throw VaultFormatException("Trailing data")
            return out
        } catch (e: BufferUnderflowException) {
            throw VaultFormatException("Truncated backup payload")
        }
    }
}

/** Export/import of the whole vault as one encrypted file protected by its own backup password. */
class BackupService(
    private val repo: VaultRepository,
    private val kdfParams: KdfParams = KdfParams.DEFAULT,
    private val policy: KdfPolicy = KdfPolicy.PRODUCTION,
) {
    suspend fun export(key: VaultKey, backupPassword: CharArray): ExportResult {
        val all = repo.getAll(key)
        val plain = BackupPayload.encode(all.entries)
        try {
            val file = withContext(Dispatchers.Default) { BackupCrypto.seal(backupPassword, plain, kdfParams, policy) }
            return ExportResult(file, all.entries.size, all.corruptedIds.size)
        } finally {
            plain.fill(0)
        }
    }

    /**
     * All-or-nothing: the file is fully authenticated and decoded before anything is written, and the write is one
     * transaction. Entries identical to existing ones (or repeated inside the file) are skipped.
     * @throws AuthenticationFailedException wrong backup password or modified file
     * @throws VaultFormatException not a valid/supported backup
     */
    suspend fun import(key: VaultKey, file: ByteArray, backupPassword: CharArray): ImportResult {
        val plain = withContext(Dispatchers.Default) { BackupCrypto.open(backupPassword, file, policy) }
        val items = try { BackupPayload.decode(plain) } finally { plain.fill(0) }
        val seen = repo.getAll(key).entries.map { it.content }.toHashSet()
        val fresh = items.filter { seen.add(it.content) }
        repo.importAll(key, fresh)
        return ImportResult(added = fresh.size, skippedDuplicates = items.size - fresh.size)
    }
}
