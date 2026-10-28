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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.safevault.SafeVaultApp
import app.safevault.data.Categories
import app.safevault.data.EntryContent
import app.safevault.logic.GeneratorOptions
import app.safevault.logic.PasswordGenerator
import app.safevault.logic.PasswordStrength
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: VaultViewModel, nav: NavController, id: Long) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as SafeVaultApp
    val entry = entries.firstOrNull { it.id == id }
    var revealed by remember(id) { mutableStateOf(false) }
    var confirmReveal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(revealed) { if (revealed) { delay(15_000); revealed = false } }
    if (entry == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val c = entry.content

    fun copy(text: String) {
        app.clipboard.copy(text, settings.clipboardClearSeconds)
        vm.showMessage(Strings.copied(settings.clipboardClearSeconds))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c.service) },
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
            Text(categoryEmoji(c.category) + " " + Strings.categoryName(c.category))
            if (c.url.isNotEmpty()) FieldRow(Strings.URL, c.url, null)
            if (c.username.isNotEmpty()) FieldRow(Strings.USERNAME, c.username) { copy(c.username) }
            if (c.email.isNotEmpty()) FieldRow(Strings.EMAIL, c.email) { copy(c.email) }

            Column {
                Text(Strings.PASSWORD, style = MaterialTheme.typography.labelMedium)
                Text(
                    if (revealed) c.password else "••••••••••",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (revealed) revealed = false else confirmReveal = true }) {
                        Text(if (revealed) Strings.HIDE else Strings.SHOW)
                    }
                    TextButton(enabled = c.password.isNotEmpty(), onClick = { copy(c.password) }) { Text(Strings.COPY) }
                }
            }
            if (c.notes.isNotEmpty()) FieldRow(Strings.NOTES, c.notes, null)
            Text(Strings.CREATED + ": " + dateFormat.format(Instant.ofEpochMilli(entry.createdAt)), style = MaterialTheme.typography.bodySmall)
            Text(Strings.UPDATED + ": " + dateFormat.format(Instant.ofEpochMilli(entry.updatedAt)), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { nav.navigate("edit/$id") }) { Text(Strings.EDIT) }
                OutlinedButton(onClick = { confirmDelete = true }) { Text(Strings.DELETE) }
            }
        }
    }

    if (confirmReveal) {
        ConfirmDialog(Strings.CONFIRM_REVEAL_TITLE, Strings.CONFIRM_REVEAL_BODY, Strings.SHOW,
            onConfirm = { confirmReveal = false; revealed = true }, onDismiss = { confirmReveal = false })
    }
    if (confirmDelete) {
        ConfirmDialog(Strings.CONFIRM_DELETE_TITLE, Strings.CONFIRM_DELETE_BODY, Strings.DELETE,
            onConfirm = { confirmDelete = false; vm.delete(id) { nav.popBackStack() } }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun FieldRow(label: String, value: String, onCopy: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (onCopy != null) TextButton(onClick = onCopy) { Text(Strings.COPY) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: VaultViewModel, nav: NavController, id: Long) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val existing = entries.firstOrNull { it.id == id }
    val e = existing?.content
    // Plain remember (not rememberSaveable): secrets must not be written into saved-instance-state bundles.
    var category by remember(id) { mutableStateOf(e?.category ?: Categories.OTHER) }
    var service by remember(id) { mutableStateOf(e?.service ?: "") }
    var url by remember(id) { mutableStateOf(e?.url ?: "") }
    var username by remember(id) { mutableStateOf(e?.username ?: "") }
    var email by remember(id) { mutableStateOf(e?.email ?: "") }
    var password by remember(id) { mutableStateOf(e?.password ?: "") }
    var notes by remember(id) { mutableStateOf(e?.notes ?: "") }
    var showPw by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) Strings.ADD_TITLE else Strings.EDIT_TITLE) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.BACK) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).imePadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in Categories.ALL) {
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(categoryEmoji(c) + " " + Strings.categoryName(c)) },
                    )
                }
            }
            OutlinedTextField(service, { service = it }, label = { Text(Strings.FIELD_SERVICE) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text(Strings.URL) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text(Strings.USERNAME) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text(Strings.EMAIL) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PasswordField(password, { password = it }, Strings.PASSWORD, show = showPw)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) Strings.HIDE else Strings.SHOW) }
                TextButton(onClick = { showGenerator = true }) { Text(Strings.GENERATE) }
            }
            if (password.isNotEmpty()) StrengthMeter(PasswordStrength.evaluate(password))
            OutlinedTextField(notes, { notes = it }, label = { Text(Strings.NOTES) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = service.isNotBlank(),
                onClick = {
                    val content = EntryContent(category, service.trim(), url.trim(), username.trim(), email.trim(), password, notes)
                    vm.save(if (id == 0L) null else id, content) { nav.popBackStack() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(Strings.SAVE) }
        }
    }

    if (showGenerator) {
        GeneratorDialog(onDismiss = { showGenerator = false }, onUse = { password = it; showPw = true; showGenerator = false })
    }
}

@Composable
fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var opts by remember { mutableStateOf(GeneratorOptions()) }
    var pw by remember { mutableStateOf(PasswordGenerator.generate(GeneratorOptions())) }

    fun regen(o: GeneratorOptions) {
        opts = o
        pw = if (o.lower || o.upper || o.digits || o.symbols) PasswordGenerator.generate(o) else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.GENERATE_PASSWORD) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pw, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                Text(Strings.LENGTH + ": " + opts.length)
                Slider(
                    value = opts.length.toFloat(),
                    onValueChange = { regen(opts.copy(length = it.roundToInt())) },
                    valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..PasswordGenerator.MAX_LENGTH.toFloat(),
                )
                OptionRow(Strings.OPT_LOWER, opts.lower) { regen(opts.copy(lower = it)) }
                OptionRow(Strings.OPT_UPPER, opts.upper) { regen(opts.copy(upper = it)) }
                OptionRow(Strings.OPT_DIGITS, opts.digits) { regen(opts.copy(digits = it)) }
                OptionRow(Strings.OPT_SYMBOLS, opts.symbols) { regen(opts.copy(symbols = it)) }
                OptionRow(Strings.OPT_NO_AMBIGUOUS, opts.excludeAmbiguous) { regen(opts.copy(excludeAmbiguous = it)) }
            }
        },
        confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { onUse(pw) }) { Text(Strings.USE_THIS) } },
        dismissButton = {
            Row {
                TextButton(onClick = { regen(opts) }) { Text(Strings.REGENERATE) }
                TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
            }
        },
    )
}

@Composable
fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
