package app.aaps.plugins.sync.nsclientV3.compose

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CLEANUP_RETENTION_DAYS = 93L

class NSClientComposeContent(
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val uel: UserEntryLogger,
    private val nsClientRepository: NSClientRepository,
    private val nsClient: NsClient,
    private val title: String
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()
        val viewModel: NSClientViewModel = metroViewModel()

        // Dialog states
        var showFullSyncDialog by remember { mutableStateOf(false) }
        var showCleanupDialog by remember { mutableStateOf(false) }
        var showResultDialog by remember { mutableStateOf(false) }
        var resultMessage by remember { mutableStateOf(AnnotatedString("")) }

        // Load initial data
        LaunchedEffect(Unit) { viewModel.loadInitialData() }

        // Pre-resolve strings for use in coroutine callbacks
        val clearedEntriesText = stringResource(CoreUiStrings.cleared_entries)

        // Full sync confirmation dialog
        if (showFullSyncDialog) {
            OkCancelDialog(
                title = stringResource(SyncStrings.ns_client),
                message = stringResource(SyncStrings.full_sync_comment),
                onConfirm = {
                    showFullSyncDialog = false
                    showCleanupDialog = true
                },
                onDismiss = { showFullSyncDialog = false }
            )
        }

        // Cleanup confirmation dialog
        if (showCleanupDialog) {
            OkCancelDialog(
                title = stringResource(SyncStrings.ns_client),
                message = stringResource(CoreUiStrings.cleanup_db_confirm_sync),
                onConfirm = {
                    showCleanupDialog = false
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                persistenceLayer.cleanupDatabase(CLEANUP_RETENTION_DAYS, deleteTrackedChanges = true)
                            }
                            if (result.isNotEmpty()) {
                                resultMessage = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(clearedEntriesText) }
                                    appendLine()
                                    append(result)
                                }
                                showResultDialog = true
                            }
                            aapsLogger.info(LTag.CORE, "Cleaned up databases with result: $result")
                            withContext(Dispatchers.IO) {
                                nsClient.resetToFullSync()
                                nsClient.resend("FULL_SYNC")
                            }
                        } catch (e: Exception) {
                            aapsLogger.error("Error cleaning up databases", e)
                        }
                    }
                    uel.log(action = Action.CLEANUP_DATABASES, source = Sources.NSClient)
                },
                onDismiss = {
                    showCleanupDialog = false
                    // Cancel means "No" to cleanup but continue with full sync
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            nsClient.resetToFullSync()
                            nsClient.resend("FULL_SYNC")
                        }
                    }
                }
            )
        }

        // Result dialog
        if (showResultDialog) {
            OkDialog(
                title = stringResource(CoreUiStrings.result),
                message = resultMessage,
                onDismiss = { showResultDialog = false }
            )
        }

        NSClientScreen(
            viewModel = viewModel,
            dateUtil = dateUtil,
            title = title,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onPauseChanged = { isChecked ->
                uel.log(
                    action = if (isChecked) Action.NS_PAUSED else Action.NS_RESUME,
                    source = Sources.NSClient
                )
                nsClient.pause(isChecked)
                viewModel.updatePaused(isChecked)
            },
            onClearLog = {
                nsClientRepository.clearLog()
            },
            onSendNow = {
                scope.launch(Dispatchers.IO) {
                    nsClient.resend("GUI")
                }
            },
            onFullSync = {
                showFullSyncDialog = true
            },
            onSettings = onSettings
        )
    }
}
