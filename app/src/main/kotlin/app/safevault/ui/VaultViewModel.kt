package app.safevault.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.safevault.SafeVaultApp
import app.safevault.crypto.*
import app.safevault.data.EntryContent
import app.safevault.data.VaultEntry
import app.safevault.logic.AppSettings
import app.safevault.logic.AuditResult
import app.safevault.logic.VaultAudit
import app.safevault.platform.BiometricGate
import app.safevault.platform.BiometricKeys
import app.safevault.platform.DeviceKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface AppState {
    data object Loading : AppState
    data object NeedSetup : AppState
    data object Locked : AppState
    data object Unlocked : AppState
}

sealed interface UiEvent {
    data class ExportReady(val fileName: String) : UiEvent
    data object PickImport : UiEvent
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafeVaultApp
    private val repo get() = app.repository
    private val session get() = app.session

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()
    private val _corrupted = MutableStateFlow<List<Long>>(emptyList())
    val corrupted: StateFlow<List<Long>> = _corrupted.asStateFlow()
    private val _audit = MutableStateFlow(AuditResult(emptyList(), emptyList()))
    val audit: StateFlow<AuditResult> = _audit.asStateFlow()
    private val _settings = MutableStateFlow(app.settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _hasPendingImport = MutableStateFlow(false)
    val hasPendingImport: StateFlow<Boolean> = _hasPendingImport.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var pendingImportBytes: ByteArray? = null
    private var pendingExportBytes: ByteArray? = null
    private var failedAttempts = 0
    private val lockListener: () -> Unit = { onSessionLocked() }

    init {
        session.addOnLockedListener(lockListener)
        launchSafe {
            if (session.isUnlocked()) { reload(); _state.value = AppState.Unlocked }
            else _state.value = if (repo.isInitialized()) AppState.Locked else AppState.NeedSetup
        }
    }

    override fun onCleared() {
        session.removeOnLockedListener(lockListener)
        super.onCleared()
    }

    // ---- lifecycle of the vault ----
    fun createVault(password: CharArray) {
        launchSafe {
            try {
                val key = withBusy { repo.createVault(password) }
                enter(key)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun unlock(password: CharArray) {
        launchSafe {
            try {
                if (_busy.value) return@launchSafe
                if (failedAttempts >= 5) delay(minOf(failedAttempts - 4, 10) * 1000L)
                val key = withBusy { repo.unlock(password) }
                failedAttempts = 0
                enter(key)
            } catch (e: UnlockFailedException) {
                failedAttempts++
                _message.value = Strings.WRONG_PASSWORD
            } catch (e: DeviceKeyUnavailableException) {
                _message.value = Strings.DEVICE_KEY_LOST
            } catch (e: VaultException) {
                _message.value = Strings.VAULT_CORRUPT
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun lockNow() = session.lock()

    private fun onSessionLocked() {
        _entries.value = emptyList()
        _corrupted.value = emptyList()
        _audit.value = AuditResult(emptyList(), emptyList())
        if (_state.value == AppState.Unlocked) _state.value = AppState.Locked
    }

    private suspend fun enter(key: VaultKey) {
        session.unlock(key)
        reload()
        _state.value = AppState.Unlocked
    }

    private suspend fun reload() {
        val r = repo.getAll(session.requireKey())
        val sorted = r.entries.sortedBy { it.content.service.lowercase() }
        _entries.value = sorted
        _corrupted.value = r.corruptedIds
        _audit.value = VaultAudit.analyze(sorted)
    }

    // ---- entries ----
    fun save(id: Long?, content: EntryContent, onDone: () -> Unit) {
        launchSafe {
            val key = session.requireKey()
            if (id == null) repo.add(key, content) else repo.update(key, id, content)
            session.touch()
            reload()
            onDone()
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        launchSafe {
            repo.delete(id)
            session.touch()
            reload()
            onDone()
        }
    }

    // ---- master password ----
    fun changeMasterPassword(oldPassword: CharArray, newPassword: CharArray) {
        launchSafe {
            try {
                val key = withBusy { repo.changePassword(oldPassword, newPassword) }
                session.unlock(key)
                _message.value = Strings.MASTER_CHANGED
            } catch (e: UnlockFailedException) {
                _message.value = Strings.OLD_WRONG
            } finally {
                oldPassword.fill('\u0000'); newPassword.fill('\u0000')
            }
        }
    }

    // ---- biometric ----
    fun enableBiometric(gate: BiometricGate) {
        launchSafe {
            val key = session.requireKey()
            gate.enable(
                key,
                onSuccess = { blob ->
                    launchSafe {
                        repo.putBiometricBlob(blob)
                        updateSettings(_settings.value.copy(biometricEnabled = true))
                        _message.value = Strings.BIO_ENABLED
                    }
                },
                onFail = { _message.value = Strings.BIO_ENABLE_FAILED },
            )
        }
    }

    fun disableBiometric() { launchSafe { disableBiometricInternal() } }

    private suspend fun disableBiometricInternal() {
        repo.deleteBiometricBlob()
        BiometricKeys.delete()
        updateSettings(_settings.value.copy(biometricEnabled = false))
    }

    fun biometricUnlock(gate: BiometricGate) {
        launchSafe {
            val blob = repo.getBiometricBlob() ?: return@launchSafe
            gate.unlock(
                blob,
                onSuccess = { key -> launchSafe { enter(key) } },
                onFail = { invalidated ->
                    if (invalidated) launchSafe {
                        disableBiometricInternal()
                        _message.value = Strings.BIO_INVALIDATED
                    }
                },
            )
        }
    }

    // ---- settings / messages ----
    fun updateSettings(s: AppSettings) {
        val clean = s.sanitized()
        app.settingsStore.save(clean)
        _settings.value = clean
    }

    fun showMessage(text: String) { _message.value = text }
    fun consumeMessage() { _message.value = null }

    // ---- backup ----
    fun export(backupPassword: CharArray) {
        launchSafe {
            try {
                val r = withBusy { app.backup.export(session.requireKey(), backupPassword) }
                pendingExportBytes = r.file
                if (r.skippedCorrupted > 0) _message.value = Strings.exportSkipped(r.skippedCorrupted)
                _events.emit(UiEvent.ExportReady("safevault-${LocalDate.now()}.svbk"))
            } finally {
                backupPassword.fill('\u0000')
            }
        }
    }

    /** Ciphertext only; safe to hold while the app is locked (the file picker backgrounds the app). */
    fun takeExportBytes(): ByteArray? = pendingExportBytes.also { pendingExportBytes = null }

    fun requestImportPicker() { _events.tryEmit(UiEvent.PickImport) }

    fun setPendingImport(bytes: ByteArray?) {
        pendingImportBytes = bytes
        _hasPendingImport.value = bytes != null
    }

    fun importBackup(backupPassword: CharArray) {
        launchSafe {
            val bytes = pendingImportBytes ?: return@launchSafe
            try {
                val r = withBusy { app.backup.import(session.requireKey(), bytes, backupPassword) }
                setPendingImport(null)
                reload()
                _message.value = Strings.imported(r.added, r.skippedDuplicates)
            } catch (e: AuthenticationFailedException) {
                _message.value = Strings.BACKUP_WRONG
            } catch (e: VaultFormatException) {
                _message.value = Strings.BACKUP_INVALID
                setPendingImport(null)
            } finally {
                backupPassword.fill('\u0000')
            }
        }
    }

    // ---- danger zone ----
    fun wipeAll() {
        launchSafe {
            repo.wipeEverything()
            BiometricKeys.delete()
            DeviceKey.delete()
            updateSettings(AppSettings())
            pendingExportBytes = null
            setPendingImport(null)
            session.lock()
            _state.value = AppState.NeedSetup
            _message.value = Strings.WIPED
        }
    }

    // ---- helpers ----
    private suspend fun <T> withBusy(block: suspend () -> T): T {
        _busy.value = true
        try { return block() } finally { _busy.value = false }
    }

    /** Never surfaces exception details (they could echo sensitive data); shows a generic message instead. */
    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _message.value = Strings.GENERIC_ERROR
        }
    }
}
