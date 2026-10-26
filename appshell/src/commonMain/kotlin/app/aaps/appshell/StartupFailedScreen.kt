package app.aaps.appshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.AapsSpacing

/**
 * Shown instead of the app when the start failed, in the desktop and the iOS shell.
 *
 * The text is English only: this screen shows before the graph exists, so before the translated strings
 * are loaded. The error itself is always shown in full, because for a failure nobody expected it is the
 * useful part.
 *
 * @param explanation what happened and what to do, for a failure that is a normal event rather than a
 *   bug - such as settings that cannot be read. Empty for any other failure.
 * @param onRetry when not null, a "Try again" button starts the app again, for a failure that can go
 *   away by itself, such as a key store that was locked.
 * @param onStartFresh when not null, a "Start fresh" button runs it after a confirmation. It sets the
 *   unreadable settings aside and starts with empty ones.
 */
@Composable
fun StartupFailedScreen(
    title: String,
    explanation: List<String>,
    error: Throwable,
    onRetry: (() -> Unit)? = null,
    onStartFresh: (() -> Unit)? = null
) {
    var confirmStartFresh by remember { mutableStateOf(false) }
    // A surface for the background and text colour, and the safe area because iOS draws the app under
    // the status bar. On the desktop the safe area is empty.
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(AapsSpacing.extraLarge).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            explanation.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Try again") }
                if (onStartFresh != null) Button(onClick = { confirmStartFresh = true }) { Text("Start fresh") }
            }
            Text(error::class.simpleName ?: "error", style = MaterialTheme.typography.titleMedium)
            Text(error.message ?: "no message", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (confirmStartFresh && onStartFresh != null) AlertDialog(
        onDismissRequest = { confirmStartFresh = false },
        title = { Text("Start with empty settings?") },
        text = { Text("The unreadable settings file is renamed and kept. All settings start from their defaults.") },
        confirmButton = {
            TextButton(onClick = {
                confirmStartFresh = false
                onStartFresh()
            }) { Text("Start fresh") }
        },
        dismissButton = { TextButton(onClick = { confirmStartFresh = false }) { Text("Cancel") } }
    )
}
