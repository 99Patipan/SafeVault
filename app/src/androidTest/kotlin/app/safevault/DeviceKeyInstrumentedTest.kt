package app.safevault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.safevault.crypto.*
import app.safevault.data.EntryContent
import app.safevault.data.VaultDatabase
import app.safevault.data.VaultRepository
import app.safevault.platform.DeviceKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** Runs on a real device/emulator: exercises the real AndroidKeyStore (not available to JVM/Robolectric tests). */
@RunWith(AndroidJUnit4::class)
class DeviceKeyInstrumentedTest {
    @Test fun protectorRoundTripsAndRejectsTampering() {
        DeviceKey.delete()
        val p = DeviceKey.protector()
        val data = "header-bytes".toByteArray()
        val blob = p.protect(data)
        assertArrayEquals(data, p.unprotect(blob))
        val bad = blob.clone().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(DeviceKeyUnavailableException::class.java) { p.unprotect(bad) }
    }

    @Test fun keyIsNonExtractable() {
        DeviceKey.delete()
        DeviceKey.protector().protect(byteArrayOf(1))
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertNull(ks.getKey("safevault_device_key_v1", null)!!.encoded)
    }

    @Test fun deletedDeviceKeyCannotOpenOldData() {
        DeviceKey.delete()
        val blob = DeviceKey.protector().protect(byteArrayOf(1, 2, 3))
        DeviceKey.delete()
        assertThrows(DeviceKeyUnavailableException::class.java) { DeviceKey.protector().unprotect(blob) }
    }

    @Test fun fullStackVaultOnRealKeystoreAndSqlite() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.deleteDatabase("it.db"); DeviceKey.delete()
        val keyring = VaultKeyring(policy = KdfPolicy(64, 1024, 1, 3, 2))
        val pw = "integration-ผ่าน-123!".toCharArray()
        runBlocking {
            val db = VaultDatabase.open(ctx, "it.db")
            val repo = VaultRepository(db, DeviceKey.protector(), keyring, KdfParams(64, 1, 1))
            val key = repo.createVault(pw)
            val id = repo.add(key, EntryContent(service = "ทดสอบ", password = "p@ss"))
            db.close()

            val db2 = VaultDatabase.open(ctx, "it.db")
            val repo2 = VaultRepository(db2, DeviceKey.protector(), keyring, KdfParams(64, 1, 1))
            assertEquals("p@ss", repo2.get(repo2.unlock(pw), id)!!.content.password)
            db2.close()
        }
        ctx.deleteDatabase("it.db"); DeviceKey.delete()
    }
}
