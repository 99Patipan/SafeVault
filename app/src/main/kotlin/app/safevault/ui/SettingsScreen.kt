package app.safevault.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.safevault.logic.AppSettings
import app.safevault.logic.BackgroundLock
import app.safevault.logic.PasswordStrength
import app.safevault.platform.BiometricGate
import app.safevault.platform.BiometricKeys

private enum class SettingsDialog { NONE, CHANGE_MASTER, EXPORT, WIPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: VaultViewModel, nav: NavController) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.SETTINGS) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.BACK) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(Strings.S_AUTOLOCK)
            ChipRow(AppSettings.IDLE_OPTIONS, s.idleTimeoutSeconds, { Strings.duration(it) }) {
                vm.updateSettings(s.copy(idleTimeoutSeconds = it))
            }
            Text(Strings.S_BG)
            ChipRow(BackgroundLock.entries, s.backgroundLock, {
                if (it == BackgroundLock.IMMEDIATE) Strings.BG_IMMEDIATE else Strings.BG_TIMEOUT
            }) { vm.updateSettings(s.copy(backgroundLock = it)) }
            OptionRow(Strings.S_SCREEN_OFF, s.lockOnScreenOff) { vm.updateSettings(s.copy(lockOnScreenOff = it)) }
            OptionRow(Strings.S_BIO, s.biometricEnabled) { on ->
                if (!on) vm.disableBiometric()
                else if (activity == null || !BiometricKeys.isAvailable(ctx)) vm.showMessage(Strings.BIO_UNAVAILABLE)
                else vm.enableBiometric(BiometricGate(activity))
            }

            SectionTitle(Strings.S_CLIPBOARD)
            ChipRow(AppSettings.CLIPBOARD_OPTIONS, s.clipboardClearSeconds, { Strings.duration(it) }) {
                vm.updateSettings(s.copy(clipboardClearSeconds = it))
            }

            SectionTitle(Strings.S_MASTER)
            OutlinedButton(onClick = { dialog = SettingsDialog.CHANGE_MASTER }) { Text(Strings.CHANGE_MASTER) }

            SectionTitle(Strings.S_BACKUP)
            Text(Strings.BACKUP_NOTE, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { dialog = SettingsDialog.EXPORT }) { Text(Strings.EXPORT) }
                OutlinedButton(onClick = { vm.requestImportPicker() }) { Text(Strings.IMPORT) }
            }

            SectionTitle(Strings.S_DANGER)
            Button(
                onClick = { dialog = SettingsDialog.WIPE },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(Strings.WIPE_ALL) }

            SectionTitle(Strings.S_ABOUT)
            Text(Strings.ABOUT_TEXT, style = MaterialTheme.typography.bodySmall)
        }
    }

    when (dialog) {
        SettingsDialog.CHANGE_MASTER -> ChangeMasterDialog(
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { old, new -> dialog = SettingsDialog.NONE; vm.changeMasterPassword(old, new) },
        )
        SettingsDialog.EXPORT -> BackupPasswordDialog(
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { pw -> dialog = SettingsDialog.NONE; vm.export(pw) },
        )
        SettingsDialog.WIPE -> WipeDialog(
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { dialog = SettingsDialog.NONE; vm.wipeAll() },
        )
        SettingsDialog.NONE -> Unit
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
    }
}

@Composable
private fun ChangeMasterDialog(onDismiss: () -> Unit, onConfirm: (CharArray, CharArray) -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val ok = old.isNotEmpty() && PasswordStrength.isAcceptableMaster(new) && new == again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.CHANGE_MASTER) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(old, { old = it }, Strings.OLD_PASSWORD)
                PasswordField(new, { new = it }, Strings.NEW_PASSWORD)
                if (new.isNotEmpty()) StrengthMeter(PasswordStrength.evaluate(new))
                PasswordField(again, { again = it }, Strings.CONFIRM_PASSWORD)
                if (again.isNotEmpty() && new != again) {
                    Text(Strings.PASSWORDS_DONT_MATCH, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(old.toCharArray(), new.toCharArray()) }) { Text(Strings.CONFIRM) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun BackupPasswordDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val ok = PasswordStrength.isAcceptableMaster(pw) && pw == again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.EXPORT) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.BACKUP_NOTE, style = MaterialTheme.typography.bodySmall)
                PasswordField(pw, { pw = it }, Strings.BACKUP_PASSWORD)
                if (pw.isNotEmpty()) StrengthMeter(PasswordStrength.evaluate(pw))
                PasswordField(again, { again = it }, Strings.CONFIRM_PASSWORD)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(pw.toCharArray()) }) { Text(Strings.EXPORT) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
fun ImportPasswordDialog(vm: VaultViewModel) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.setPendingImport(null) },
        title = { Text(Strings.IMPORT_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.IMPORT_BODY)
                PasswordField(pw, { pw = it }, Strings.BACKUP_PASSWORD)
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty(), onClick = { vm.importBackup(pw.toCharArray()); pw = "" }) { Text(Strings.IMPORT) }
        },
        dismissButton = { TextButton(onClick = { vm.setPendingImport(null) }) { Text(Strings.CANCEL) } },
    )
}

@Composable
private fun WipeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.WIPE_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.WIPE_BODY)
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = typed.trim() == Strings.WIPE_WORD, onClick = onConfirm) {
                Text(Strings.WIPE_ALL, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}
