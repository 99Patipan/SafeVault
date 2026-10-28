package app.safevault.logic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTest {
    @Test fun sanitizedRejectsValuesOutsideMenus() {
        val s = AppSettings(idleTimeoutSeconds = 0, clipboardClearSeconds = 99999).sanitized()
        assertEquals(60, s.idleTimeoutSeconds); assertEquals(30, s.clipboardClearSeconds)
    }

    @Test fun neverLockIsNotAnOption() {
        assertTrue(AppSettings.IDLE_OPTIONS.all { it > 0 })
    }

    @Test fun storeRoundTripsAndSanitizesCorruptValues() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val store = SharedPrefsSettingsStore(ctx)
        val s = AppSettings(300, BackgroundLock.AFTER_TIMEOUT, false, 10, true)
        store.save(s)
        assertEquals(s, store.load())
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putInt("idle", -5).putString("bg", "garbage").apply()
        val loaded = store.load()
        assertEquals(60, loaded.idleTimeoutSeconds); assertEquals(BackgroundLock.IMMEDIATE, loaded.backgroundLock)
    }
}
