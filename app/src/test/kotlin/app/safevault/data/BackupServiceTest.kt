package app.safevault.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.safevault.crypto.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupServiceTest {
    private val policy = KdfPolicy(64, 1024, 1, 3, 2)
    private val fast = KdfParams(64, 1, 1)
    private val keyring = VaultKeyring(policy = policy)
    private val pw = "master-ผ่านหลัก-123!".toCharArray()
    private val bpw = "backup-ไฟล์สำรอง-456!".toCharArray()
    private lateinit var srcDb: VaultDatabase
    private lateinit var dstDb: VaultDatabase
    private lateinit var src: VaultRepository
    private lateinit var dst: VaultRepository
    private lateinit var srcKey: VaultKey
    private lateinit var dstKey: VaultKey
    private lateinit var srcSvc: BackupService
    private lateinit var dstSvc: BackupService

    private val e1 = EntryContent(Categories.BANK, "ธนาคาร", "https://bank.example", "u1", "a@b.co", "pw-ลับ-1", "โน้ต\nสอง")
    private val e2 = EntryContent(Categories.SOCIAL, "Facebook", "", "u2", "", "pw-2", "")
    private val e3 = EntryContent(Categories.WORK, "Slack 💬", "", "u3", "w@x.co", "pw-3", "n3")

    private fun prot(b: Int): HeaderProtector = KeyHeaderProtector({ SecretKeySpec(ByteArray(32) { b.toByte() }, "AES") })
    private fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        srcDb = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        dstDb = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        src = VaultRepository(srcDb, prot(1), keyring, fast)
        dst = VaultRepository(dstDb, prot(2), keyring, fast)
        srcSvc = BackupService(src, fast, policy)
        dstSvc = BackupService(dst, fast, policy)
        blocking {
            srcKey = src.createVault(pw)
            dstKey = dst.createVault("different-master-9!".toCharArray())
        }
    }

    @After fun tearDown() { srcDb.close(); dstDb.close() }

    private suspend fun seedSource() { src.importAll(srcKey, listOf(ImportItem(e1, 10, 20), ImportItem(e2, 11, 21), ImportItem(e3, 12, 22))) }

    @Test fun exportThenImportOnAnotherVaultRoundTrips() {
        blocking {
            seedSource()
            val out = srcSvc.export(srcKey, bpw)
            assertEquals(3, out.exported)
            assertEquals(0, out.skippedCorrupted)

            val r = dstSvc.import(dstKey, out.file, bpw)
            assertEquals(3, r.added)
            val got = dst.getAll(dstKey).entries
            assertEquals(setOf(e1, e2, e3), got.map { it.content }.toSet())
            assertEquals(setOf(10L to 20L, 11L to 21L, 12L to 22L), got.map { it.createdAt to it.updatedAt }.toSet())
        }
    }

    @Test fun exportedFileHasNoPlaintext() {
        blocking {
            seedSource()
            val s = String(srcSvc.export(srcKey, bpw).file, Charsets.ISO_8859_1)
            for (f in listOf("Facebook", "pw-2", "bank.example")) assertFalse(s.contains(f))
        }
    }

    @Test fun wrongBackupPasswordImportsNothing() {
        blocking {
            seedSource()
            val file = srcSvc.export(srcKey, bpw).file
            try { dstSvc.import(dstKey, file, "wrong-wrong-1".toCharArray()); fail() }
            catch (_: AuthenticationFailedException) {}
            assertEquals(0, dst.count())
        }
    }

    @Test fun anyModifiedByteIsRejectedAndNothingImported() {
        blocking {
            seedSource()
            val file = srcSvc.export(srcKey, bpw).file
            for (i in file.indices) {
                val m = file.clone().also { it[i] = (it[i].toInt() xor 0x04).toByte() }
                try { dstSvc.import(dstKey, m, bpw); fail("byte $i accepted") } catch (_: VaultException) {}
            }
            assertEquals(0, dst.count())
        }
    }

    @Test fun importingTwiceSkipsDuplicates() {
        blocking {
            seedSource()
            val file = srcSvc.export(srcKey, bpw).file
            assertEquals(3, dstSvc.import(dstKey, file, bpw).added)
            val second = dstSvc.import(dstKey, file, bpw)
            assertEquals(0, second.added)
            assertEquals(3, second.skippedDuplicates)
            assertEquals(3, dst.count())
        }
    }

    @Test fun validContainerWithGarbagePayloadIsRejectedBeforeAnyWrite() {
        blocking {
            val bogus = BackupCrypto.seal(bpw, byteArrayOf(1, 0, 0, 0, 5, 9, 9), fast, policy)
            try { dstSvc.import(dstKey, bogus, bpw); fail() } catch (_: VaultFormatException) {}
            assertEquals(0, dst.count())
        }
    }

    @Test fun nonBackupFileRejected() {
        blocking {
            try { dstSvc.import(dstKey, ByteArray(200) { 7 }, bpw); fail() } catch (_: VaultFormatException) {}
        }
    }

    @Test fun exportReportsCorruptedRowsInsteadOfHidingThem() {
        blocking {
            seedSource()
            val row = srcDb.dao().allEntries().first()
            val flipped = row.blob.clone().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            srcDb.dao().update(EntryEntity(row.id, flipped, row.createdAt, row.updatedAt))
            val out = srcSvc.export(srcKey, bpw)
            assertEquals(2, out.exported)
            assertEquals(1, out.skippedCorrupted)
        }
    }

    @Test fun emptyVaultExportsAndImportsCleanly() {
        blocking {
            val out = srcSvc.export(srcKey, bpw)
            assertEquals(0, out.exported)
            assertEquals(0, dstSvc.import(dstKey, out.file, bpw).added)
        }
    }

    @Test fun payloadCodecRejectsMalformedInput() {
        assertThrows(VaultFormatException::class.java) { BackupPayload.decode(byteArrayOf(2)) }
        assertThrows(VaultFormatException::class.java) { BackupPayload.decode(byteArrayOf(1, 127, 127, 127, 127)) }
        assertThrows(VaultFormatException::class.java) { BackupPayload.decode(byteArrayOf(1, 0, 0, 0)) }
        val ok = BackupPayload.encode(emptyList())
        assertEquals(0, BackupPayload.decode(ok).size)
        assertThrows(VaultFormatException::class.java) { BackupPayload.decode(ok + byteArrayOf(0)) }
    }
}
