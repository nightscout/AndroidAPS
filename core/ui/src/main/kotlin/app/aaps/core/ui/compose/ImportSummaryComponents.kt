package app.aaps.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey

/**
 * A single metadata summary item row with status icon, category icon, and formatted text.
 * Reusable in both the import review screen and the legacy import summary dialog.
 */
@Composable
fun ImportSummaryItem(
    metaKey: PrefsMetadataKey,
    metaEntry: PrefMetadata,
    modifier: Modifier = Modifier,
    onSnackbarMessage: (SnackbarMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = AapsTheme.generalColors
    val textColor = when {
        metaEntry.status.isOk      -> colors.statusNormal
        metaEntry.status.isWarning -> colors.statusWarning
        metaEntry.status.isError   -> MaterialTheme.colorScheme.error
        else                       -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val msg = if (metaEntry.info != null) {
                    "[${context.getString(metaKey.label)}] ${metaEntry.info}"
                } else {
                    context.getString(metaKey.label)
                }
                val snackbarMessage = when {
                    metaEntry.status.isWarning -> SnackbarMessage.Warning(msg)
                    metaEntry.status.isError   -> SnackbarMessage.Error(msg)
                    else                       -> SnackbarMessage.Info(msg)
                }
                onSnackbarMessage(snackbarMessage)
            }
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            painter = painterResource(id = metaEntry.status.icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when {
                metaEntry.status.isOk      -> colors.statusNormal
                metaEntry.status.isWarning -> colors.statusWarning
                metaEntry.status.isError   -> MaterialTheme.colorScheme.error
                else                       -> MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            painter = painterResource(id = metaKey.icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = metaKey.formatForDisplay(context, metaEntry.value),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ImportDetailsDialog(
    details: List<Triple<String, String, String>>,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(android.R.string.dialog_alert_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(details) { (label, value, info) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$label: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}
