package app.safevault

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.safevault.data.BackupService
import app.safevault.data.VaultDatabase
import app.safevault.data.VaultRepository
import app.safevault.logic.SettingsStore
import app.safevault.logic.SharedPrefsSettingsStore
import app.safevault.logic.VaultSession
import app.safevault.platform.ClipboardHelper
import app.safevault.platform.DeviceKey

class SafeVaultApp : Application() {
    val settingsStore: SettingsStore by lazy { SharedPrefsSettingsStore(this) }
    val repository: VaultRepository by lazy { VaultRepository(VaultDatabase.open(this), DeviceKey.protector()) }
    val backup: BackupService by lazy { BackupService(repository) }
    val clipboard: ClipboardHelper by lazy { ClipboardHelper(this) }
    val session: VaultSession by lazy {
        VaultSession({ settingsStore.load() }, SystemClock::elapsedRealtime).also { s ->
            s.addOnLockedListener { clipboard.clearNow() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { session.onAppForegrounded() }
            override fun onStop(owner: LifecycleOwner) { session.onAppBackgrounded() }
        })
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { session.onScreenOff() }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
