package app.safevault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun DetailScreen(vm: VaultViewModel, nav: NavController, id: Long) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as SafeVaultApp
    val entry = entries.firstOrNull { it.id == id }
    var revealed by remember(id) { mutableStateOf(false) }
    var confirmReveal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

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
        topBar = { SvTopBar("", onBack = { nav.popBackStack() }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryTile(c.category, 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.service, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Strings.categoryName(c.category), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }

            val fields = buildList {
                if (c.url.isNotEmpty()) add(Triple(Strings.URL, c.url, false))
                if (c.username.isNotEmpty()) add(Triple(Strings.USERNAME, c.username, true))
                if (c.email.isNotEmpty()) add(Triple(Strings.EMAIL, c.email, true))
            }
            if (fields.isNotEmpty()) {
                SectionTitle(Strings.SECTION_ACCOUNT)
                SvCard(Modifier.fillMaxWidth()) {
                    fields.forEachIndexed { i, (label, value, copyable) ->
                        if (i > 0) HorizontalDivider(color = scheme.outlineVariant)
                        FieldRow(label, value, if (copyable) ({ copy(value) }) else null)
                    }
                }
            }

            SectionTitle(Strings.PASSWORD)
            SvCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .background(scheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Text(
                            if (revealed) c.password else "••••••••••",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (revealed) {
                        Text(Strings.REVEAL_AUTO_HIDE, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { if (revealed) revealed = false else confirmReveal = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (revealed) Strings.HIDE else Strings.SHOW) }
                        Button(
                            enabled = c.password.isNotEmpty(),
                            onClick = { copy(c.password) },
                            modifier = Modifier.weight(1f),
                        ) { Text(Strings.COPY) }
                    }
                }
            }

            if (c.notes.isNotEmpty()) {
                SectionTitle(Strings.NOTES)
                SvCard(Modifier.fillMaxWidth()) {
                    Text(c.notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    Strings.CREATED + ": " + dateFormat.format(Instant.ofEpochMilli(entry.createdAt)),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
                Text(
                    Strings.UPDATED + ": " + dateFormat.format(Instant.ofEpochMilli(entry.updatedAt)),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                Button(onClick = { nav.navigate("edit/$id") }, modifier = Modifier.weight(1f).height(48.dp)) { Text(Strings.EDIT) }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                ) { Text(Strings.DELETE) }
            }
        }
    }

    if (confirmReveal) {
        ConfirmDialog(Strings.CONFIRM_REVEAL_TITLE, Strings.CONFIRM_REVEAL_BODY, Strings.SHOW,
            onConfirm = { confirmReveal = false; revealed = true }, onDismiss = { confirmReveal = false })
    }
    if (confirmDelete) {
        ConfirmDialog(Strings.CONFIRM_DELETE_TITLE, Strings.CONFIRM_DELETE_BODY, Strings.DELETE,
            onConfirm = { confirmDelete = false; vm.delete(id) { nav.popBackStack() } },
            onDismiss = { confirmDelete = false }, destructive = true)
    }
}

@Composable
private fun FieldRow(label: String, value: String, onCopy: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        topBar = { SvTopBar(if (id == 0L) Strings.ADD_TITLE else Strings.EDIT_TITLE, onBack = { nav.popBackStack() }) },
    ) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(Strings.CATEGORIES)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in Categories.ALL) {
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(categoryEmoji(c) + " " + Strings.categoryName(c)) },
                    )
                }
            }

            SectionTitle(Strings.SECTION_SERVICE)
            SvCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SvTextField(service, { service = it }, Strings.FIELD_SERVICE)
                    SvTextField(url, { url = it }, Strings.URL, keyboardType = KeyboardType.Uri)
                }
            }

            SectionTitle(Strings.SECTION_LOGIN)
            SvCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SvTextField(username, { username = it }, Strings.USERNAME)
                    SvTextField(email, { email = it }, Strings.EMAIL, keyboardType = KeyboardType.Email)
                    PasswordField(password, { password = it }, Strings.PASSWORD, show = showPw, onShowChange = { showPw = it })
                    if (password.isNotEmpty()) StrengthMeter(PasswordStrength.evaluate(password))
                    OutlinedButton(onClick = { showGenerator = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(Strings.GENERATE_PASSWORD)
                    }
                }
            }

            SectionTitle(Strings.NOTES)
            SvCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    SvTextField(notes, { notes = it }, Strings.NOTES, singleLine = false, minLines = 3)
                }
            }

            Button(
                enabled = service.isNotBlank(),
                onClick = {
                    val content = EntryContent(category, service.trim(), url.trim(), username.trim(), email.trim(), password, notes)
                    vm.save(if (id == 0L) null else id, content) { nav.popBackStack() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(Strings.SAVE, style = MaterialTheme.typography.titleSmall) }
            Spacer(Modifier.height(8.dp))
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
                Box(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                ) {
                    Text(
                        pw.ifEmpty { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (pw.isNotEmpty()) StrengthMeter(PasswordStrength.evaluate(pw))
                Text(
                    Strings.LENGTH + ": " + opts.length,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
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

/** Label on the left, switch on the right; the whole row toggles. */
@Composable
fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
