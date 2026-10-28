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
import org.robolectric.shadows.ShadowLog
import java.io.File
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultRepositoryTest {
    // Tiny KDF cost only so tests are fast; production defaults are covered by crypto-core tests.
    private val keyring = VaultKeyring(policy = KdfPolicy(64, 1024, 1, 3, 2))
    private val fast = KdfParams(64, 1, 1)
    private val pw = "รหัสหลัก-Strong-Pass-123!".toCharArray()
    private lateinit var ctx: Context
    private lateinit var db: VaultDatabase
    private lateinit var repo: VaultRepository

    private val secret = EntryContent(
        Categories.EMAIL, "Gmail-บัญชีหลัก", "https://mail.example", "somchai.user", "somchai@example.com",
        "hunter2-ปลอดภัย", "รหัสกู้คืน: alpha-bravo-charlie",
    )

    private fun protector(b: Int = 1): HeaderProtector =
        KeyHeaderProtector({ SecretKeySpec(ByteArray(32) { b.toByte() }, "AES") })

    private fun newRepo(d: VaultDatabase, p: HeaderProtector = protector()) = VaultRepository(d, p, keyring, fast)

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        repo = newRepo(db)
    }

    @After fun tearDown() { db.close() }

    @Test fun createUnlockAndRejectWrongPassword() {
        blocking {
            assertFalse(repo.isInitialized())
            repo.createVault(pw).close()
            assertTrue(repo.isInitialized())
            repo.unlock(pw).close()
            try { repo.unlock("wrong-password".toCharArray()); fail() } catch (_: UnlockFailedException) {}
        }
    }

    @Test fun secondCreateVaultIsRefusedAndKeepsOriginal() {
        blocking {
            repo.createVault(pw).close()
            try { repo.createVault("other-pass-1".toCharArray()); fail() } catch (_: IllegalStateException) {}
            repo.unlock(pw).close()
        }
    }

    @Test fun unlockWithoutVaultFails() {
        blocking { try { repo.unlock(pw); fail() } catch (_: IllegalStateException) {} }
    }

    @Test fun addGetUpdateDeleteRoundTrip() {
        blocking {
            val key = repo.createVault(pw)
            val id = repo.add(key, secret)
            assertEquals(secret, repo.get(key, id)!!.content)
            assertEquals(1, repo.getAll(key).entries.size)

            val edited = secret.copy(password = "new-ลับ-pass")
            repo.update(key, id, edited)
            val got = repo.get(key, id)!!
            assertEquals(edited, got.content)
            assertTrue(got.updatedAt >= got.createdAt)

            assertTrue(repo.delete(id))
            assertNull(repo.get(key, id))
            assertFalse(repo.delete(id))
        }
    }

    @Test fun updateKeepsCreatedAtAndUsesFreshCiphertext() {
        blocking {
            var t = 1000L
            val r = VaultRepository(db, protector(), keyring, fast, clock = { t })
            val key = r.createVault(pw)
            val id = r.add(key, secret)
            val before = db.dao().entry(id)!!
            t = 2000L
            r.update(key, id, secret)
            val after = db.dao().entry(id)!!
            assertEquals(1000L, after.createdAt)
            assertEquals(2000L, after.updatedAt)
            assertFalse(before.blob.contentEquals(after.blob))
        }
    }

    @Test fun failedUpdateLeavesOldValueIntact() {
        blocking {
            val key = repo.createVault(pw)
            val id = repo.add(key, secret)
            val poison = secret.copy(notes = "x".repeat(EntryCodec.MAX_FIELD_BYTES + 1))
            try { repo.update(key, id, poison); fail() } catch (_: IllegalArgumentException) {}
            assertEquals(secret, repo.get(key, id)!!.content)
        }
    }

    @Test fun updateOfMissingEntryFails() {
        blocking {
            val key = repo.createVault(pw)
            try { repo.update(key, 12345, secret); fail() } catch (_: NoSuchElementException) {}
        }
    }

    @Test fun idsAreRandomAndUnique() {
        blocking {
            val key = repo.createVault(pw)
            val ids = repo.addAll(key, List(50) { secret })
            assertEquals(50, ids.toSet().size)
            assertTrue(ids.all { it > 0 })
            assertNotEquals(listOf(1L, 2L, 3L), ids.take(3))
        }
    }

    @Test fun storedRowsContainNoPlaintext() {
        blocking {
            val key = repo.createVault(pw)
            val id = repo.add(key, secret)
            val blob = String(db.dao().entry(id)!!.blob, Charsets.ISO_8859_1)
            for (s in secret.fields().drop(1)) {
                assertFalse("leaked: $s", blob.contains(String(s.toByteArray(), Charsets.ISO_8859_1)))
            }
        }
    }

    @Test fun modifiedRowIsDetectedAndReportedNotDropped() {
        blocking {
            val key = repo.createVault(pw)
            val good = repo.add(key, secret)
            val victim = repo.add(key, secret.copy(service = "Bank"))
            val row = db.dao().entry(victim)!!
            val flipped = row.blob.clone().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            db.dao().update(EntryEntity(victim, flipped, row.createdAt, row.updatedAt))

            val r = repo.getAll(key)
            assertEquals(listOf(victim), r.corruptedIds)
            assertEquals(listOf(good), r.entries.map { it.id })
            try { repo.get(key, victim); fail() } catch (_: AuthenticationFailedException) {}
        }
    }

    @Test fun swappingBlobsBetweenRowsIsDetected() {
        blocking {
            val key = repo.createVault(pw)
            val a = repo.add(key, secret.copy(service = "A"))
            val b = repo.add(key, secret.copy(service = "B"))
            val rowA = db.dao().entry(a)!!
            val rowB = db.dao().entry(b)!!
            db.dao().update(EntryEntity(a, rowB.blob, rowA.createdAt, rowA.updatedAt))
            db.dao().update(EntryEntity(b, rowA.blob, rowB.createdAt, rowB.updatedAt))
            assertEquals(setOf(a, b), repo.getAll(key).corruptedIds.toSet())
        }
    }

    @Test fun storedHeaderIsDeviceProtectedAndTamperBlocksUnlock() {
        blocking {
            repo.createVault(pw).close()
            val h = db.dao().header()!!
            assertFalse(String(h, Charsets.ISO_8859_1).contains("SVLT"))
            for (i in h.indices) {
                val m = h.clone().also { it[i] = (it[i].toInt() xor 1).toByte() }
                db.dao().putMeta(VaultMetaEntity(headerBytes = m))
                try { repo.unlock(pw).close(); fail("byte $i accepted") } catch (_: VaultException) {}
            }
        }
    }

    @Test fun differentDeviceKeyCannotUnlock() {
        blocking {
            repo.createVault(pw).close()
            val other = newRepo(db, protector(2))
            try { other.unlock(pw); fail() } catch (_: DeviceKeyUnavailableException) {}
            repo.unlock(pw).close() // original device key still works
        }
    }

    @Test fun changePasswordKeepsAllEntriesReadable() {
        blocking {
            val key = repo.createVault(pw)
            val id = repo.add(key, secret)
            val oldHeader = db.dao().header()!!
            val newPw = "NEW-ผ่านใหม่-456!".toCharArray()

            repo.changePassword(pw, newPw).close()
            assertFalse(oldHeader.contentEquals(db.dao().header()!!))
            try { repo.unlock(pw); fail() } catch (_: UnlockFailedException) {}
            val k2 = repo.unlock(newPw)
            assertEquals(secret, repo.get(k2, id)!!.content)
        }
    }

    @Test fun failedPasswordChangeLeavesHeaderUntouched() {
        blocking {
            repo.createVault(pw).close()
            val before = db.dao().header()!!
            try { repo.changePassword("nope-nope".toCharArray(), "x-new-pass".toCharArray()); fail() }
            catch (_: UnlockFailedException) {}
            assertArrayEquals(before, db.dao().header()!!)
            repo.unlock(pw).close()
        }
    }

    @Test fun addAllIsAtomicAndRollsBackOnFailure() {
        blocking {
            val key = repo.createVault(pw)
            repo.add(key, secret)
            val poison = EntryContent(notes = "x".repeat(EntryCodec.MAX_FIELD_BYTES + 1))
            try { repo.addAll(key, listOf(secret, secret, poison)); fail() } catch (_: IllegalArgumentException) {}
            assertEquals(1, repo.count())
        }
    }

    @Test fun importAllKeepsTimestampsAndRollsBack() {
        blocking {
            val key = repo.createVault(pw)
            val ids = repo.importAll(key, listOf(ImportItem(secret, 111L, 222L)))
            val e = repo.get(key, ids.single())!!
            assertEquals(111L, e.createdAt)
            assertEquals(222L, e.updatedAt)
            val poison = ImportItem(EntryContent(notes = "x".repeat(EntryCodec.MAX_FIELD_BYTES + 1)), 1, 1)
            try { repo.importAll(key, listOf(ImportItem(secret, 1, 1), poison)); fail() }
            catch (_: IllegalArgumentException) {}
            assertEquals(1, repo.count())
        }
    }

    @Test fun lockedKeyCannotBeUsed() {
        blocking {
            val key = repo.createVault(pw)
            val id = repo.add(key, secret)
            key.close()
            try { repo.add(key, secret); fail() } catch (_: IllegalStateException) {}
            try { repo.get(key, id); fail() } catch (_: IllegalStateException) {}
            assertEquals(1, repo.count())
        }
    }

    @Test fun biometricBlobRoundTripAndWipe() {
        blocking {
            repo.createVault(pw).close()
            assertNull(repo.getBiometricBlob())
            repo.putBiometricBlob(byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), repo.getBiometricBlob())
            repo.unlock(pw).close() // header row unaffected
            repo.deleteBiometricBlob()
            assertNull(repo.getBiometricBlob())
            repo.putBiometricBlob(byteArrayOf(9))
            repo.wipeEverything()
            assertNull(repo.getBiometricBlob())
        }
    }

    @Test fun wipeEverythingRemovesEntriesAndHeader() {
        blocking {
            val key = repo.createVault(pw)
            repo.add(key, secret)
            repo.wipeEverything()
            assertEquals(0, repo.count())
            assertFalse(repo.isInitialized())
        }
    }

    @Test fun nothingSecretIsWrittenToLog() {
        blocking {
            val key = repo.createVault(pw)
            val id = repo.add(key, secret)
            repo.update(key, id, secret.copy(password = "another-ลับ"))
            repo.getAll(key)
            repo.changePassword(pw, "second-Pass-789!".toCharArray()).close()
        }
        val all = ShadowLog.getLogs().joinToString("\n") { it.msg ?: "" }
        for (s in secret.fields().drop(1) + String(pw)) assertFalse("logged: $s", all.contains(s))
    }

    // ---- real on-disk database ----
    @Test fun dataSurvivesReopenAndFileHasNoPlaintext() {
        val name = "file-test.db"
        ctx.deleteDatabase(name)
        val id = blocking {
            val d = VaultDatabase.open(ctx, name)
            val r = newRepo(d)
            val key = r.createVault(pw)
            val newId = r.add(key, secret)
            d.close()
            newId
        }
        blocking {
            val d = VaultDatabase.open(ctx, name)
            val r = newRepo(d)
            val key = r.unlock(pw)
            assertEquals(secret, r.get(key, id)!!.content)
            d.close()
        }
        val dbFile = ctx.getDatabasePath(name)
        val raw = dbFile.parentFile!!.listFiles()!!.filter { it.name.startsWith(name) }
            .map(File::readBytes).fold(ByteArray(0)) { acc, b -> acc + b }
        assertTrue("scan must see a real SQLite file", indexOf(raw, "SQLite format 3".toByteArray()) >= 0)
        assertEquals("header must be device-protected on disk", -1, indexOf(raw, "SVLT".toByteArray()))
        for (s in secret.fields().drop(1).filter { it.length > 3 }) {
            assertEquals("plaintext found on disk: $s", -1, indexOf(raw, s.toByteArray()))
        }
        ctx.deleteDatabase(name)
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
