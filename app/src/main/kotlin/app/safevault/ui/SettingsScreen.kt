package app.safevault.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.safevault.logic.AppSettings
import app.safevault.logic.BackgroundLock
import app.safevault.logic.PasswordStrength
import app.safevault.platform.BiometricGate
import app.safevault.platform.BiometricKeys

private enum class SettingsDialog { NONE, CHANGE_MASTER, ROTATE_DEK, EXPORT, WIPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: VaultViewModel, nav: NavController) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }

    Scaffold(
        topBar = { SvTopBar(Strings.SETTINGS, onBack = { nav.popBackStack() }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(Strings.S_AUTOLOCK) {
                ChipRow(AppSettings.IDLE_OPTIONS, s.idleTimeoutSeconds, { Strings.duration(it) }) {
                    vm.updateSettings(s.copy(idleTimeoutSeconds = it))
                }
                Text(Strings.S_BG, style = MaterialTheme.typography.labelLarge)
                ChipRow(BackgroundLock.entries, s.backgroundLock, {
                    if (it == BackgroundLock.IMMEDIATE) Strings.BG_IMMEDIATE else Strings.BG_TIMEOUT
                }) { vm.updateSettings(s.copy(backgroundLock = it)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OptionRow(Strings.S_SCREEN_OFF, s.lockOnScreenOff) { vm.updateSettings(s.copy(lockOnScreenOff = it)) }
                OptionRow(Strings.S_BIO, s.biometricEnabled) { on ->
                    if (!on) vm.disableBiometric()
                    else if (activity == null || !BiometricKeys.isAvailable(ctx)) vm.showMessage(Strings.BIO_UNAVAILABLE)
                    else vm.enableBiometric(BiometricGate(activity))
                }
            }

            SettingsGroup(Strings.S_CLIPBOARD) {
                ChipRow(AppSettings.CLIPBOARD_OPTIONS, s.clipboardClearSeconds, { Strings.duration(it) }) {
                    vm.updateSettings(s.copy(clipboardClearSeconds = it))
                }
            }

            SettingsGroup(Strings.S_MASTER) {
                OutlinedButton(onClick = { dialog = SettingsDialog.CHANGE_MASTER }, modifier = Modifier.fillMaxWidth()) {
                    Text(Strings.CHANGE_MASTER)
                }
                OutlinedButton(onClick = { dialog = SettingsDialog.ROTATE_DEK }, modifier = Modifier.fillMaxWidth()) {
                    Text(Strings.ROTATE_DEK)
                }
            }

            SettingsGroup(Strings.S_BACKUP) {
                Text(Strings.BACKUP_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { dialog = SettingsDialog.EXPORT }, modifier = Modifier.weight(1f)) { Text(Strings.EXPORT) }
                    OutlinedButton(onClick = { vm.requestImportPicker() }, modifier = Modifier.weight(1f)) { Text(Strings.IMPORT) }
                }
            }

            SettingsGroup(
                Strings.S_DANGER,
                titleColor = MaterialTheme.colorScheme.error,
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            ) {
                Button(
                    onClick = { dialog = SettingsDialog.WIPE },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(Strings.WIPE_ALL) }
            }

            SettingsGroup(Strings.S_ABOUT) {
                Text(Strings.ABOUT_TEXT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    when (dialog) {
        SettingsDialog.CHANGE_MASTER -> ChangeMasterDialog(
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { old, new -> dialog = SettingsDialog.NONE; vm.changeMasterPassword(old, new) },
        )
        SettingsDialog.ROTATE_DEK -> RotateDekDialog(
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { pw -> dialog = SettingsDialog.NONE; vm.rotateDek(pw) },
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
private fun SettingsGroup(
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
            modifier = Modifier.padding(start = 4.dp),
        )
        SvCard(Modifier.fillMaxWidth(), borderColor = borderColor) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
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
private fun RotateDekDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.ROTATE_DEK) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Strings.ROTATE_DEK_HINT, style = MaterialTheme.typography.bodyMedium)
                PasswordField(pw, { pw = it }, Strings.MASTER_PASSWORD)
            }
        },
        confirmButton = {
            TextButton(
                enabled = pw.isNotEmpty(),
                onClick = { val copy = pw.toCharArray(); pw = ""; onConfirm(copy) },
            ) { Text(Strings.ROTATE_DEK_CONFIRM) }
        },
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
