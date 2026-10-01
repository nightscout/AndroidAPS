package app.aaps.ui.compose.maintenance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.dialogs.QueryAnyPasswordDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.maintenance.MaintenanceViewModel.ExportState

/**
 * Encapsulates all maintenance-related dialogs and sheets from MainScreen:
 * - Maintenance bottom sheet with export/import/cleanup actions
 * - Log settings bottom sheet
 * - Cloud directory sheet
 * - 5 confirmation dialogs (reset APS, reset DB, cleanup DB, send logs, export CSV)
 * - Export state machine dialogs (missing password, confirm, ask password)
 * - Cleanup result dialog
 *
 * Owns its own dialog state — MainScreen only needs to pass the ViewModel and visibility flag.
 */
@Composable
fun MaintenanceDialogs(
    maintenanceViewModel: MaintenanceViewModel,
    showMaintenanceSheet: Boolean,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onSnackbar: suspend (String) -> Unit,
) {
    // Confirmation dialog states
    var showLogSettings by remember { mutableStateOf(false) }
    var showConfirmResetAps by remember { mutableStateOf(false) }
    var showConfirmResetDb by remember { mutableStateOf(false) }
    var showConfirmCleanupDb by remember { mutableStateOf(false) }
    var showConfirmExportCsv by remember { mutableStateOf(false) }
    var showConfirmSendLogs by remember { mutableStateOf(false) }
    var cleanupResultText by remember { mutableStateOf<String?>(null) }

    // ViewModel state
    val exportState by maintenanceViewModel.exportState.collectAsStateWithLifecycle()
    val cloudDirectoryState by maintenanceViewModel.cloudDirectoryState.collectAsStateWithLifecycle()
    val exportConfig by maintenanceViewModel.exportConfig.collectAsStateWithLifecycle()
    val isDirectoryAccessGranted by maintenanceViewModel.isDirectoryAccessGranted.collectAsStateWithLifecycle()

    // Collect maintenance events
    LaunchedEffect(Unit) {
        maintenanceViewModel.events.collect { event ->
            when (event) {
                is MaintenanceEvent.RecreateActivity -> onRecreateActivity()
                is MaintenanceEvent.CleanupResult -> cleanupResultText = event.result
                is MaintenanceEvent.Snackbar -> onSnackbar(event.message)
                is MaintenanceEvent.Error -> onSnackbar(event.message)
                is MaintenanceEvent.LaunchBrowser -> onLaunchBrowser(event.url)
                is MaintenanceEvent.BringToForeground -> onBringToForeground()
            }
        }
    }

    // Maintenance bottom sheet
    if (showMaintenanceSheet) {
        LaunchedEffect(Unit) {
            maintenanceViewModel.refreshExportConfig()
        }

        MaintenanceBottomSheet(
            onDismiss = onMaintenanceSheetDismiss,
            onLogSettingsClick = { showLogSettings = true },
            onSendLogsClick = { showConfirmSendLogs = true },
            onDeleteLogsClick = { maintenanceViewModel.deleteLogs() },
            onDirectoryClick = {
                maintenanceViewModel.logSelectDirectory()
                onDirectoryClick()
            },
            onCloudDirectoryClick = { maintenanceViewModel.showCloudDirectory() },
            onClearCloudClick = { maintenanceViewModel.requestClearCloud() },
            onExportSettingsClick = { maintenanceViewModel.startExport() },
            // No audit entry here: opening the picker is not an import. ImportViewModel logs
            // IMPORT_SETTINGS when an import is actually applied, and logging it here as well wrote
            // the entry twice for a real import and once for one the user walked away from.
            onImportSettingsClick = { source -> onImportSettingsNavigate(source) },
            onExportCsvClick = { showConfirmExportCsv = true },
            onResetApsResultsClick = { showConfirmResetAps = true },
            onCleanupDbClick = { showConfirmCleanupDb = true },
            onResetDbClick = { showConfirmResetDb = true },
            exportConfig = exportConfig,
            isDirectoryAccessGranted = isDirectoryAccessGranted,
            onToggleSettingsLocal = { maintenanceViewModel.toggleSettingsLocal(it) },
            onToggleSettingsCloud = { maintenanceViewModel.toggleSettingsCloud(it) },
            onToggleLogEmail = { maintenanceViewModel.toggleLogEmail(it) },
            onToggleLogCloud = { maintenanceViewModel.toggleLogCloud(it) },
            onToggleCsvLocal = { maintenanceViewModel.toggleCsvLocal(it) },
            onToggleCsvCloud = { maintenanceViewModel.toggleCsvCloud(it) }
        )
    }

    // Log settings bottom sheet
    if (showLogSettings) {
        LogSettingBottomSheet(
            logElements = maintenanceViewModel.logElements,
            onDismiss = { showLogSettings = false },
            onToggle = { element, enabled -> maintenanceViewModel.toggleLogElement(element, enabled) },
            onResetToDefaults = { maintenanceViewModel.resetLogDefaults() }
        )
    }

    // Cloud directory dialog
    CloudDirectorySheet(
        state = cloudDirectoryState,
        onConnectGoogleDrive = { maintenanceViewModel.connectGoogleDrive() },
        onConfirmClear = { maintenanceViewModel.confirmClearCloud() },
        onCancelClear = { maintenanceViewModel.cancelClearCloud() },
        onReauthorize = { maintenanceViewModel.reauthorize() },
        onDismiss = { maintenanceViewModel.dismissCloudDirectory() }
    )

    // Confirmation dialogs
    if (showConfirmResetAps) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.reset_aps_results_confirm),
            onConfirm = {
                showConfirmResetAps = false
                maintenanceViewModel.resetApsResults()
            },
            onDismiss = { showConfirmResetAps = false }
        )
    }

    if (showConfirmResetDb) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.reset_db_confirm),
            onConfirm = {
                showConfirmResetDb = false
                maintenanceViewModel.resetDatabases()
            },
            onDismiss = { showConfirmResetDb = false }
        )
    }

    if (showConfirmCleanupDb) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.cleanup_db_confirm),
            onConfirm = {
                showConfirmCleanupDb = false
                maintenanceViewModel.cleanupDatabases()
            },
            onDismiss = { showConfirmCleanupDb = false }
        )
    }

    if (showConfirmSendLogs) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.send_logs) + "?",
            onConfirm = {
                showConfirmSendLogs = false
                maintenanceViewModel.sendLogs()
            },
            onDismiss = { showConfirmSendLogs = false }
        )
    }

    if (showConfirmExportCsv) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.confirmation),
            message = stringResource(CoreUiStrings.ue_export_to_csv) + "?",
            onConfirm = {
                showConfirmExportCsv = false
                maintenanceViewModel.exportCsv()
            },
            onDismiss = { showConfirmExportCsv = false }
        )
    }

    // Export settings dialog chain
    when (exportState) {
        is ExportState.MasterPasswordMissing -> {
            OkDialog(
                title = stringResource(CoreUiStrings.nav_export),
                message = stringResource(CoreUiStrings.master_password_missing),
                onDismiss = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.ConfirmExport         -> {
            val confirmState = exportState as ExportState.ConfirmExport
            OkCancelDialog(
                title = stringResource(CoreUiStrings.export_to),
                message = confirmState.fileName + "?\n\n" +
                    stringResource(CoreUiStrings.password_preferences_encrypt_prompt),
                onConfirm = { maintenanceViewModel.onExportConfirmed() },
                onDismiss = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.AskPassword           -> {
            val askState = exportState as ExportState.AskPassword
            QueryAnyPasswordDialog(
                title = stringResource(StringKey.ProtectionMasterPassword.title),
                passwordExplanation = stringResource(CoreUiStrings.password_preferences_encrypt_prompt),
                errorMessage = if (askState.wrongPassword) stringResource(CoreUiStrings.wrongpassword) else null,
                onConfirm = { password -> maintenanceViewModel.onExportPasswordEntered(password) },
                onCancel = { maintenanceViewModel.cancelExport() }
            )
        }

        is ExportState.Idle                  -> { /* no dialog */
        }
    }

    // Cleanup result dialog
    cleanupResultText?.let { result ->
        OkDialog(
            title = stringResource(CoreUiStrings.result),
            message = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(CoreUiStrings.cleared_entries)) }
                appendLine()
                append(result)
            },
            onDismiss = { cleanupResultText = null }
        )
    }
}
