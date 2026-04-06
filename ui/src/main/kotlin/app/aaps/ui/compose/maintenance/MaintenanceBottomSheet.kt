package app.aaps.ui.compose.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.maintenance.ExportConfig
import app.aaps.core.ui.compose.TonalIcon
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceBottomSheet(
    onDismiss: () -> Unit,
    onLogSettingsClick: () -> Unit,
    onSendLogsClick: () -> Unit,
    onDeleteLogsClick: () -> Unit,
    onDirectoryClick: () -> Unit,
    onCloudDirectoryClick: () -> Unit,
    onClearCloudClick: () -> Unit,
    onExportSettingsClick: () -> Unit,
    onImportSettingsClick: (ImportSource) -> Unit,
    onExportCsvClick: () -> Unit,
    onResetApsResultsClick: () -> Unit,
    onCleanupDbClick: () -> Unit,
    onResetDbClick: () -> Unit,
    exportConfig: ExportConfig? = null,
    onToggleSettingsLocal: (Boolean) -> Unit = {},
    onToggleSettingsCloud: (Boolean) -> Unit = {},
    onToggleLogEmail: (Boolean) -> Unit = {},
    onToggleLogCloud: (Boolean) -> Unit = {},
    onToggleCsvLocal: (Boolean) -> Unit = {},
    onToggleCsvCloud: (Boolean) -> Unit = {},
    isDirectoryAccessGranted: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        MaintenanceBottomSheetContent(
            onDismiss = onDismiss,
            onLogSettingsClick = onLogSettingsClick,
            onSendLogsClick = onSendLogsClick,
            onDeleteLogsClick = onDeleteLogsClick,
            onDirectoryClick = onDirectoryClick,
            onCloudDirectoryClick = onCloudDirectoryClick,
            onClearCloudClick = onClearCloudClick,
            onExportSettingsClick = onExportSettingsClick,
            onImportSettingsClick = onImportSettingsClick,
            onExportCsvClick = onExportCsvClick,
            onResetApsResultsClick = onResetApsResultsClick,
            onCleanupDbClick = onCleanupDbClick,
            onResetDbClick = onResetDbClick,
            exportConfig = exportConfig,
            onToggleSettingsLocal = onToggleSettingsLocal,
            onToggleSettingsCloud = onToggleSettingsCloud,
            onToggleLogEmail = onToggleLogEmail,
            onToggleLogCloud = onToggleLogCloud,
            onToggleCsvLocal = onToggleCsvLocal,
            onToggleCsvCloud = onToggleCsvCloud,
            isDirectoryAccessGranted = isDirectoryAccessGranted
        )
    }
}

@Composable
internal fun MaintenanceBottomSheetContent(
    onDismiss: () -> Unit = {},
    onLogSettingsClick: () -> Unit = {},
    onSendLogsClick: () -> Unit = {},
    onDeleteLogsClick: () -> Unit = {},
    onDirectoryClick: () -> Unit = {},
    onCloudDirectoryClick: () -> Unit = {},
    onClearCloudClick: () -> Unit = {},
    onExportSettingsClick: () -> Unit = {},
    onImportSettingsClick: (ImportSource) -> Unit = {},
    onExportCsvClick: () -> Unit = {},
    onResetApsResultsClick: () -> Unit = {},
    onCleanupDbClick: () -> Unit = {},
    onResetDbClick: () -> Unit = {},
    exportConfig: ExportConfig? = null,
    onToggleSettingsLocal: (Boolean) -> Unit = {},
    onToggleSettingsCloud: (Boolean) -> Unit = {},
    onToggleLogEmail: (Boolean) -> Unit = {},
    onToggleLogCloud: (Boolean) -> Unit = {},
    onToggleCsvLocal: (Boolean) -> Unit = {},
    onToggleCsvCloud: (Boolean) -> Unit = {},
    isDirectoryAccessGranted: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    MaterialTheme.colorScheme.error
    val isCloudActive = exportConfig?.isCloudActive == true
    val hasCloudError = exportConfig?.isCloudError == true
    val hasCloudCredentials = exportConfig?.hasCloudCredentials == true

    Column(
        modifier = Modifier
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Section: Log Files
        SectionHeader(stringResource(CoreUiR.string.log_files))

        MaintenanceItem(
            text = stringResource(CoreUiR.string.nav_logsettings),
            description = stringResource(CoreUiR.string.maintenance_log_settings_desc),
            icon = Icons.Default.Settings,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onLogSettingsClick
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.send_logs),
            description = stringResource(CoreUiR.string.maintenance_send_logs_desc),
            icon = Icons.AutoMirrored.Filled.Send,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onSendLogsClick,
            trailingContent = exportConfig?.let {
                {
                    DestinationChips(
                        localSelected = it.logEmail,
                        cloudSelected = it.logCloud,
                        cloudEnabled = isCloudActive,
                        cloudLabel = stringResource(CoreUiR.string.chip_cloud),
                        useEmailLabel = true,
                        emailLabel = stringResource(CoreUiR.string.chip_email),
                        onLocalToggle = onToggleLogEmail,
                        onCloudToggle = onToggleLogCloud
                    )
                }
            }
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.delete_logs),
            description = stringResource(CoreUiR.string.maintenance_delete_logs_desc),
            icon = Icons.Default.Delete,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onDeleteLogsClick
        )

        // Section: File management
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SectionHeader(stringResource(CoreUiR.string.file_management))

        MaintenanceItem(
            text = stringResource(CoreUiR.string.aaps_directory),
            description = stringResource(CoreUiR.string.maintenance_aaps_directory_desc),
            icon = Icons.Default.Folder,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onDirectoryClick,
            leadingContent = {
                DirectoryStatusIcon(
                    isAccessGranted = isDirectoryAccessGranted,
                    color = primaryColor
                )
            }
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.cloud_directory),
            description = stringResource(CoreUiR.string.maintenance_cloud_directory_desc),
            icon = Icons.Default.Cloud,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onCloudDirectoryClick,
            leadingContent = {
                CloudStatusIcon(
                    hasCredentials = hasCloudCredentials,
                    hasError = hasCloudError,
                    isActive = isCloudActive,
                    color = primaryColor
                )
            },
            trailingContent = if (hasCloudCredentials) {
                {
                    IconButton(onClick = onClearCloudClick) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(CoreUiR.string.clear_cloud_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.nav_export),
            description = stringResource(CoreUiR.string.maintenance_export_desc),
            icon = Icons.Default.FileUpload,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onExportSettingsClick,
            trailingContent = exportConfig?.let {
                {
                    DestinationChips(
                        localSelected = it.settingsLocal,
                        cloudSelected = it.settingsCloud,
                        cloudEnabled = isCloudActive,
                        cloudLabel = stringResource(CoreUiR.string.chip_cloud),
                        onLocalToggle = onToggleSettingsLocal,
                        onCloudToggle = onToggleSettingsCloud
                    )
                }
            }
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.import_setting),
            description = stringResource(CoreUiR.string.maintenance_import_desc),
            icon = Icons.Default.FileDownload,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = {
                val source = when {
                    exportConfig?.settingsLocal == true && exportConfig.settingsCloud && isCloudActive -> ImportSource.BOTH
                    exportConfig?.settingsCloud == true && isCloudActive                               -> ImportSource.CLOUD
                    else                                                                               -> ImportSource.LOCAL
                }
                onImportSettingsClick(source)
            },
            trailingContent = exportConfig?.let {
                {
                    DestinationChips(
                        localSelected = it.settingsLocal,
                        cloudSelected = it.settingsCloud,
                        cloudEnabled = isCloudActive,
                        cloudLabel = stringResource(CoreUiR.string.chip_cloud),
                        onLocalToggle = onToggleSettingsLocal,
                        onCloudToggle = onToggleSettingsCloud
                    )
                }
            }
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.ue_export_to_csv),
            description = stringResource(CoreUiR.string.maintenance_export_csv_desc),
            icon = Icons.Default.TableChart,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onExportCsvClick,
            trailingContent = exportConfig?.let {
                {
                    DestinationChips(
                        localSelected = it.csvLocal,
                        cloudSelected = it.csvCloud,
                        cloudEnabled = isCloudActive,
                        cloudLabel = stringResource(CoreUiR.string.chip_cloud),
                        onLocalToggle = onToggleCsvLocal,
                        onCloudToggle = onToggleCsvCloud
                    )
                }
            }
        )

        // Section: Database management
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SectionHeader(stringResource(CoreUiR.string.database_management))

        MaintenanceItem(
            text = stringResource(CoreUiR.string.database_cleanup),
            description = stringResource(CoreUiR.string.maintenance_cleanup_db_desc),
            icon = Icons.Default.Delete,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onCleanupDbClick
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.reset_aps_results),
            description = stringResource(CoreUiR.string.maintenance_reset_aps_results_desc),
            icon = Icons.Default.DeleteForever,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onResetApsResultsClick,
            danger = true
        )
        MaintenanceItem(
            text = stringResource(CoreUiR.string.nav_resetdb),
            description = stringResource(CoreUiR.string.maintenance_reset_db_desc),
            icon = Icons.Default.DeleteForever,
            color = primaryColor,
            onDismiss = onDismiss,
            onClick = onResetDbClick,
            danger = true
        )
    }
}

@Composable
private fun DirectoryStatusIcon(
    isAccessGranted: Boolean,
    color: Color
) {
    val badgeColor = if (isAccessGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    BadgedBox(
        badge = {
            Badge(
                containerColor = badgeColor,
                modifier = Modifier.size(8.dp)
            )
        }
    ) {
        TonalIcon(
            painter = rememberVectorPainter(Icons.Default.Folder),
            color = color
        )
    }
}

@Composable
private fun CloudStatusIcon(
    hasCredentials: Boolean,
    hasError: Boolean,
    isActive: Boolean,
    color: Color
) {
    val badgeColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isActive -> Color(0xFF4CAF50) // green
        hasCredentials -> Color(0xFF4CAF50) // green (has credentials, active)
        else -> MaterialTheme.colorScheme.outlineVariant // gray
    }
    BadgedBox(
        badge = {
            Badge(
                containerColor = badgeColor,
                modifier = Modifier.size(8.dp)
            )
        }
    ) {
        TonalIcon(
            painter = rememberVectorPainter(Icons.Default.Cloud),
            color = color
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun MaintenanceItem(
    text: String,
    icon: ImageVector,
    color: Color,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    description: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    danger: Boolean = false
) {
    val containerColor = if (danger) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surface
    val contentColor = if (danger) MaterialTheme.colorScheme.onErrorContainer
    else color

    ListItem(
        headlineContent = { Text(text = text, color = contentColor) },
        supportingContent = description?.let {
            {
                Text(
                    text = it,
                    color = if (danger) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = leadingContent ?: {
            TonalIcon(painter = rememberVectorPainter(icon), color = contentColor)
        },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier.clickable {
            onDismiss()
            onClick()
        }
    )
}

/**
 * Inline destination FilterChips for export rows.
 * Both chips can be on, at least one must stay on (enforced by backend).
 * For logs: [Email] [Cloud]. For settings/CSV: [Local] [Cloud].
 */
@Composable
private fun DestinationChips(
    localSelected: Boolean = true,
    cloudSelected: Boolean = false,
    cloudEnabled: Boolean = true,
    cloudLabel: String,
    emailLabel: String = "",
    useEmailLabel: Boolean = false,
    onLocalToggle: (Boolean) -> Unit,
    onCloudToggle: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = localSelected,
            onClick = { onLocalToggle(!localSelected) },
            label = {
                Text(
                    text = if (useEmailLabel) emailLabel else stringResource(CoreUiR.string.chip_local),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
        FilterChip(
            selected = cloudSelected,
            onClick = { onCloudToggle(!cloudSelected) },
            enabled = cloudEnabled,
            label = {
                Text(
                    text = cloudLabel,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MaintenanceBottomSheetContentPreview() {
    MaterialTheme {
        MaintenanceBottomSheetContent(
            exportConfig = ExportConfig(
                isCloudActive = true,
                isCloudError = false,
                hasCloudCredentials = true,
                settingsLocal = true,
                settingsCloud = true,
                logEmail = true,
                logCloud = false,
                csvLocal = true,
                csvCloud = false,
                cloudDisplayName = "Google Drive"
            ),
            isDirectoryAccessGranted = true
        )
    }
}
