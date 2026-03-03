package app.aaps.plugins.sync.nsShared.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
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
import app.aaps.plugins.sync.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose content provider for NSClient plugins.
 * This class provides the UI for NSClientV1 and NSClientV3 plugins.
 */
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
        val viewModel: NSClientViewModel = hiltViewModel()

        // Dialog states
        var showFullSyncDialog by remember { mutableStateOf(false) }
        var showCleanupDialog by remember { mutableStateOf(false) }
        var showResultDialog by remember { mutableStateOf(false) }
        var resultMessage by remember { mutableStateOf("") }

        // Load initial data
        LaunchedEffect(Unit) { viewModel.loadInitialData() }

        // Pre-resolve strings for use in coroutine callbacks
        val clearedEntriesText = stringResource(app.aaps.core.ui.R.string.cleared_entries)

        // Full sync confirmation dialog
        if (showFullSyncDialog) {
            OkCancelDialog(
                title = stringResource(R.string.ns_client),
                message = stringResource(R.string.full_sync_comment),
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
                title = stringResource(R.string.ns_client),
                message = stringResource(app.aaps.core.ui.R.string.cleanup_db_confirm_sync),
                onConfirm = {
                    showCleanupDialog = false
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                persistenceLayer.cleanupDatabase(CLEANUP_RETENTION_DAYS, deleteTrackedChanges = true)
                            }
                            if (result.isNotEmpty()) {
                                resultMessage = "<b>$clearedEntriesText</b><br>$result"
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
                title = stringResource(app.aaps.core.ui.R.string.result),
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
