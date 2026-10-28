package app.safevault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safevault.logic.PasswordStrength
import app.safevault.platform.BiometricGate
import app.safevault.platform.BiometricKeys

@Composable
fun SetupScreen(vm: VaultViewModel) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var ack by remember { mutableStateOf(false) }
    val result = remember(pw) { PasswordStrength.evaluate(pw) }
    val acceptable = PasswordStrength.isAcceptableMaster(pw)
    val match = pw.isNotEmpty() && pw == pw2

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("🔐", style = MaterialTheme.typography.displayMedium)
        Text(Strings.SETUP_TITLE, style = MaterialTheme.typography.headlineMedium)
        Text(Strings.SETUP_INTRO, style = MaterialTheme.typography.bodyMedium)
        PasswordField(pw, { pw = it }, Strings.MASTER_PASSWORD)
        if (pw.isNotEmpty()) StrengthMeter(result)
        Text(Strings.MASTER_RULES, style = MaterialTheme.typography.bodySmall)
        PasswordField(pw2, { pw2 = it }, Strings.CONFIRM_PASSWORD)
        if (pw2.isNotEmpty() && !match) {
            Text(Strings.PASSWORDS_DONT_MATCH, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = ack, onCheckedChange = { ack = it })
            Text(Strings.SETUP_ACK, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        }
        Button(
            enabled = acceptable && match && ack,
            onClick = { vm.createVault(pw.toCharArray()); pw = ""; pw2 = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(Strings.CREATE_VAULT) }
    }
}

@Composable
fun UnlockScreen(vm: VaultViewModel) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var pw by remember { mutableStateOf("") }
    val activity = remember(ctx) { ctx.findActivity() }
    val canBio = settings.biometricEnabled && activity != null && BiometricKeys.isAvailable(ctx)

    fun tryBiometric() { activity?.let { vm.biometricUnlock(BiometricGate(it)) } }
    LaunchedEffect(Unit) { if (canBio) tryBiometric() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔐", style = MaterialTheme.typography.displayLarge)
        Text(Strings.APP_NAME, style = MaterialTheme.typography.headlineMedium)
        PasswordField(pw, { pw = it }, Strings.MASTER_PASSWORD)
        Button(
            enabled = pw.isNotEmpty(),
            onClick = { vm.unlock(pw.toCharArray()); pw = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(Strings.UNLOCK) }
        if (canBio) {
            OutlinedButton(onClick = { tryBiometric() }, modifier = Modifier.fillMaxWidth()) { Text(Strings.UNLOCK_BIO) }
        }
    }
}
