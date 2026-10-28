package app.safevault.data

import androidx.room.*

/**
 * One row = one entry. [blob] = nonce||AES-256-GCM(ciphertext)||tag over ALL secret fields
 * (including category), AAD bound to (id, "entry"). Only id and timestamps are plaintext metadata.
 * IDs are random 63-bit values (not sequential) so they can be used as AAD before insert.
 */
@Entity(tableName = "entries")
class EntryEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val blob: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Single row (id = 0) holding the serialized [app.safevault.crypto.VaultHeader]. */
@Entity(tableName = "vault_meta")
class VaultMetaEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val headerBytes: ByteArray,
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM entries") suspend fun allEntries(): List<EntryEntity>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: Long): EntryEntity?
    @Query("SELECT COUNT(*) FROM entries") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: EntryEntity)
    @Update suspend fun update(e: EntryEntity): Int
    @Query("DELETE FROM entries WHERE id = :id") suspend fun delete(id: Long): Int
    @Query("DELETE FROM entries") suspend fun deleteAllEntries()

    @Query("SELECT headerBytes FROM vault_meta WHERE id = 0") suspend fun header(): ByteArray?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeta(m: VaultMetaEntity)
    @Query("DELETE FROM vault_meta") suspend fun deleteMeta()

    // Extra meta rows (id 1 = biometric-wrapped DEK copy). id 0 stays the vault header.
    @Query("SELECT headerBytes FROM vault_meta WHERE id = :id") suspend fun meta(id: Int): ByteArray?
    @Query("DELETE FROM vault_meta WHERE id = :id") suspend fun deleteMetaById(id: Int)
}
