package app.safevault.logic

import android.content.Context

enum class BackgroundLock { IMMEDIATE, AFTER_TIMEOUT }

data class AppSettings(
    val idleTimeoutSeconds: Int = 60,
    val backgroundLock: BackgroundLock = BackgroundLock.IMMEDIATE,
    val lockOnScreenOff: Boolean = true,
    val clipboardClearSeconds: Int = 30,
    val biometricEnabled: Boolean = false,
) {
    /** Values outside the allowed menus fall back to defaults (there is deliberately no "never lock"). */
    fun sanitized() = copy(
        idleTimeoutSeconds = if (idleTimeoutSeconds in IDLE_OPTIONS) idleTimeoutSeconds else 60,
        clipboardClearSeconds = if (clipboardClearSeconds in CLIPBOARD_OPTIONS) clipboardClearSeconds else 30,
    )

    companion object {
        val IDLE_OPTIONS = listOf(15, 30, 60, 300, 900)
        val CLIPBOARD_OPTIONS = listOf(10, 20, 30, 60)
    }
}

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

/** Non-secret preferences only. Excluded from cloud backup/device transfer by the backup rules. */
class SharedPrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            idleTimeoutSeconds = prefs.getInt("idle", d.idleTimeoutSeconds),
            backgroundLock = runCatching { BackgroundLock.valueOf(prefs.getString("bg", "") ?: "") }
                .getOrDefault(d.backgroundLock),
            lockOnScreenOff = prefs.getBoolean("screenOff", d.lockOnScreenOff),
            clipboardClearSeconds = prefs.getInt("clip", d.clipboardClearSeconds),
            biometricEnabled = prefs.getBoolean("bio", d.biometricEnabled),
        ).sanitized()
    }

    override fun save(settings: AppSettings) {
        val s = settings.sanitized()
        prefs.edit()
            .putInt("idle", s.idleTimeoutSeconds).putString("bg", s.backgroundLock.name)
            .putBoolean("screenOff", s.lockOnScreenOff).putInt("clip", s.clipboardClearSeconds)
            .putBoolean("bio", s.biometricEnabled).apply()
    }
}
