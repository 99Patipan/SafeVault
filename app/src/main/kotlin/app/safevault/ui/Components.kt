package app.safevault.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
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

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    show: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun StrengthMeter(r: StrengthResult) {
    val scheme = MaterialTheme.colorScheme
    val (fraction, label, color) = when (r.strength) {
        Strength.VERY_WEAK -> Triple(0.15f, Strings.STRENGTH_VERY_WEAK, scheme.error)
        Strength.WEAK -> Triple(0.4f, Strings.STRENGTH_WEAK, scheme.error)
        Strength.FAIR -> Triple(0.7f, Strings.STRENGTH_FAIR, scheme.tertiary)
        Strength.STRONG -> Triple(1f, Strings.STRENGTH_STRONG, scheme.primary)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { fraction }, color = color, modifier = Modifier.fillMaxWidth())
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        for (reason in r.reasons) {
            Text("• " + reasonText(reason), style = MaterialTheme.typography.bodySmall)
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
