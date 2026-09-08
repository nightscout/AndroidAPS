package app.aaps.core.ui.compose

import app.aaps.core.ui.compose.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.ui.CoreUiStrings

/**
 * A single metadata summary item row with status icon, category icon, and formatted text.
 * Reusable in both the import review screen and the legacy import summary dialog.
 */
@Composable
fun ImportSummaryItem(
    metaKey: PrefsMetadataKey,
    metaEntry: PrefMetadata,
    rxBus: RxBus,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.generalColors
    val textColor = when {
        metaEntry.status.isOk      -> colors.statusNormal
        metaEntry.status.isWarning -> colors.statusWarning
        metaEntry.status.isError   -> MaterialTheme.colorScheme.error
        else                       -> MaterialTheme.colorScheme.onSurface
    }
    // Resolved here rather than in the click handler: stringResource is a composable call and the
    // lambda below is not composable.
    val label = stringResource(metaKey.label)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val msg = if (metaEntry.info != null) {
                    "[$label] ${metaEntry.info}"
                } else {
                    label
                }
                val type = when {
                    metaEntry.status.isWarning -> EventShowSnackbar.Type.Warning
                    metaEntry.status.isError   -> EventShowSnackbar.Type.Error
                    else                       -> EventShowSnackbar.Type.Info
                }
                rxBus.send(EventShowSnackbar(msg, type))
            }
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = metaEntry.status.icon,
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
            imageVector = metaKey.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = stringResource(metaKey.formatForDisplay(metaEntry.value)),
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(CoreUiStrings.alert),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
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
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiStrings.ok))
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}
