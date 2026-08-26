package app.aaps.plugins.sync.nfcCommands.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.compose.icons.IcPluginNfc
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcCommand
import app.aaps.plugins.sync.nfcCommands.NfcCommandCode
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcCreatedTag

@Composable
fun NfcExecutionConfirmationDialog(
    tag: NfcCreatedTag,
    plugin: NfcCommandsPlugin,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = IcPluginNfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = tag.name,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                tag.commands.forEach { cmdJson ->
                    NfcCommandDisplay(commandJson = cmdJson, tagName = tag.name, plugin = plugin)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(CoreUiR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

@Composable
fun NfcCommandDisplay(
    commandJson: String,
    tagName: String,
    plugin: NfcCommandsPlugin
) {
    val decoded = remember(commandJson) { NfcCommand.decode(commandJson) }
    if (decoded == null) {
        Text(text = commandJson, style = MaterialTheme.typography.bodySmall)
        return
    }
    val code = decoded.code
    val params = decoded.params

    val action = remember(code, params) { 
        plugin.getAction(code).apply { this.params = params }
    }

    val detail by produceState<String?>(initialValue = null, action) {
        value = action.formatParams(tagName)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = action.customIconColor?.invoke() ?: action.elementType.color(),
            modifier = Modifier.size(16.dp)
        )
        action.secondaryIcon?.let { secondaryIcon ->
            Icon(
                imageVector = secondaryIcon,
                contentDescription = null,
                tint = action.secondaryIconColor?.invoke() ?: action.elementType.color(),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(" ")
                    append(stringResource(action.labelResId))
                }
                if (detail != null) {
                    append(" ")
                    append(detail!!)
                }
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}
