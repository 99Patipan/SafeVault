package app.safevault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
    val scheme = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandMark(72.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Strings.SETUP_TITLE, style = MaterialTheme.typography.headlineMedium)
                Text(Strings.SETUP_INTRO, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            PasswordField(pw, { pw = it }, Strings.MASTER_PASSWORD)
            if (pw.isNotEmpty()) StrengthMeter(result)
            Text(Strings.MASTER_RULES, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            PasswordField(pw2, { pw2 = it }, Strings.CONFIRM_PASSWORD)
            if (pw2.isNotEmpty() && !match) {
                Text(Strings.PASSWORDS_DONT_MATCH, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            }
            SvCard(Modifier.fillMaxWidth(), color = scheme.tertiaryContainer, borderColor = scheme.tertiaryContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = ack, role = Role.Checkbox, onValueChange = { ack = it })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = ack, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(
                        Strings.SETUP_ACK,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                    )
                }
            }
            Button(
                enabled = acceptable && match && ack,
                onClick = { vm.createVault(pw.toCharArray()); pw = ""; pw2 = "" },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(Strings.CREATE_VAULT, style = MaterialTheme.typography.titleSmall) }
        }
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
    fun submit() { if (pw.isNotEmpty()) { vm.unlock(pw.toCharArray()); pw = "" } }
    LaunchedEffect(Unit) { if (canBio) tryBiometric() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(96.dp)
            Text(Strings.APP_NAME, style = MaterialTheme.typography.headlineMedium)
            Text(
                Strings.UNLOCK_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            PasswordField(pw, { pw = it }, Strings.MASTER_PASSWORD, onDone = { submit() })
            Button(
                enabled = pw.isNotEmpty(),
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(Strings.UNLOCK, style = MaterialTheme.typography.titleSmall) }
            if (canBio) {
                OutlinedButton(
                    onClick = { tryBiometric() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(Strings.UNLOCK_BIO) }
            }
        }
    }
}
