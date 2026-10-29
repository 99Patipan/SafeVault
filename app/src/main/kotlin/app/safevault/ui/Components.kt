package app.safevault.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import app.safevault.data.Categories
import app.safevault.logic.Reason
import app.safevault.logic.Strength
import app.safevault.logic.StrengthResult

fun Context.findActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

fun categoryEmoji(cat: String): String = when (cat) {
    "social" -> "💬"; "email" -> "✉️"; "bank" -> "🏦"; "work" -> "💼"; "web" -> "🌐"; else -> "🔑"
}

/** One hue per category so lists are scannable at a glance. */
fun categoryTint(cat: String): Color = when (cat) {
    Categories.SOCIAL -> Color(0xFFB388FF)   // neon violet
    Categories.EMAIL -> Color(0xFF40C4FF)    // sky cyan
    Categories.BANK -> Color(0xFF69F0AE)     // mint
    Categories.WORK -> Color(0xFFFFAB40)     // amber
    Categories.WEB -> Color(0xFF00E5FF)      // electric cyan
    else -> Color(0xFF78909C)
}

/** Rounded tile with an emoji on a soft tinted background (stronger glow in dark). */
@Composable
fun EmojiTile(emoji: String, tint: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(tint.copy(alpha = if (isDarkTheme()) 0.22f else 0.14f))
            .border(
                width = 1.dp,
                color = tint.copy(alpha = if (isDarkTheme()) 0.45f else 0.25f),
                shape = RoundedCornerShape(size * 0.28f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

@Composable
fun CategoryTile(cat: String, size: Dp = 44.dp, modifier: Modifier = Modifier) =
    EmojiTile(categoryEmoji(cat), categoryTint(cat), size, modifier)

@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(scheme.primary.copy(alpha = if (isDarkTheme()) 0.18f else 0.12f))
            .border(
                width = 1.5.dp,
                color = scheme.primary.copy(alpha = if (isDarkTheme()) 0.55f else 0.35f),
                shape = RoundedCornerShape(size * 0.28f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("🔐", fontSize = (size.value * 0.5f).sp)
    }
}

/** Glass-style surface for grouped content. Clickable when [onClick] is given. */
@Composable
fun SvCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = glassBorderColor(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val base = modifier
        .clip(shape)
        .background(color)
        .border(1.dp, borderColor, shape)
    val m = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Column(m, content = content)
}

@Composable
fun Pill(text: String, container: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun Banner(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(container).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, color = content, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmojiTile(emoji, MaterialTheme.colorScheme.primary, size = 72.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SvTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.BACK) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Masked field with a built-in show/hide toggle. Pass [show]/[onShowChange] to control visibility from outside,
 * and [onDone] to submit from the keyboard's action key.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    show: Boolean? = null,
    onShowChange: ((Boolean) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    var inner by remember { mutableStateOf(false) }
    val visible = show ?: inner
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            TextButton(onClick = { if (onShowChange != null) onShowChange(!visible) else inner = !visible }) {
                Text(if (visible) Strings.HIDE else Strings.SHOW)
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun StrengthMeter(r: StrengthResult) {
    val scheme = MaterialTheme.colorScheme
    val (level, label, color) = when (r.strength) {
        Strength.VERY_WEAK -> Triple(1, Strings.STRENGTH_VERY_WEAK, scheme.error)
        Strength.WEAK -> Triple(2, Strings.STRENGTH_WEAK, scheme.error)
        Strength.FAIR -> Triple(3, Strings.STRENGTH_FAIR, scheme.tertiary)
        Strength.STRONG -> Triple(4, Strings.STRENGTH_STRONG, successColor())
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (i < level) color else scheme.surfaceContainerHighest),
                    )
                }
            }
            Text(label, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
        }
        for (reason in r.reasons) {
            Text(
                "• " + reasonText(reason),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

fun reasonText(r: Reason): String = when (r) {
    Reason.TOO_SHORT -> Strings.R_TOO_SHORT
    Reason.COMMON -> Strings.R_COMMON
    Reason.REPETITIVE -> Strings.R_REPETITIVE
    Reason.SEQUENTIAL -> Strings.R_SEQUENTIAL
    Reason.LOW_VARIETY -> Strings.R_LOW_VARIETY
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.textButtonColors(),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp),
    )
}
