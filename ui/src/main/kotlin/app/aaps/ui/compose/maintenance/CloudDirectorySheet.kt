package app.aaps.ui.compose.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.ui.compose.dialogs.ErrorDialog
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.ui.compose.maintenance.MaintenanceViewModel.CloudDirectoryState
import app.aaps.core.ui.R as CoreUiR

@Composable
fun CloudDirectorySheet(
    state: CloudDirectoryState,
    onConnectGoogleDrive: () -> Unit,
    onConfirmClear: () -> Unit,
    onCancelClear: () -> Unit,
    onReauthorize: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is CloudDirectoryState.Hidden -> { /* nothing */
        }

        is CloudDirectoryState.Shown -> {
            CloudDirectoryDialog(
                info = state.info,
                onConnectGoogleDrive = onConnectGoogleDrive,
                onDismiss = onDismiss
            )
        }

        is CloudDirectoryState.ConfirmClear -> {
            OkCancelDialog(
                title = stringResource(CoreUiR.string.clear_cloud_settings),
                message = stringResource(CoreUiR.string.clear_cloud_settings_message),
                onConfirm = onConfirmClear,
                onDismiss = onCancelClear
            )
        }

        is CloudDirectoryState.Reauthorize -> {
            ErrorDialog(
                title = stringResource(CoreUiR.string.cloud_connection_failed),
                message = stringResource(CoreUiR.string.cloud_reauthorize_message),
                positiveButton = stringResource(CoreUiR.string.reauthorize),
                onPositive = onReauthorize,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun CloudDirectoryDialog(
    info: CloudDirectoryInfo,
    onConnectGoogleDrive: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(CoreUiR.string.select_storage_type),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                // Google Drive row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConnectGoogleDrive() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(info.providerIconResId),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.providerDisplayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = info.providerDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = info.isCloudActive,
                        onClick = { onConnectGoogleDrive() }
                    )
                }

                // Authorization status section
                if (info.hasCredentials) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(CoreUiR.string.authorization_status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = if (info.hasConnectionError)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                        val statusIcon = if (info.hasConnectionError)
                            Icons.Default.Warning
                        else
                            Icons.Default.CheckCircle
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = statusColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = info.authorizedStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = info.cloudPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}
