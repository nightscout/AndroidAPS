package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.log.LabelValueRow
import app.aaps.plugins.sync.log.SyncLogLayout
import app.aaps.plugins.sync.log.clearLogAction
import app.aaps.plugins.sync.log.fullSyncAction
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut.ConnectionStatus

@Composable
internal fun TidepoolScreen(
    viewModel: TidepoolViewModel,
    dateUtil: DateUtil,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onUploadNow: () -> Unit,
    onFullSync: () -> Unit,
    onPurge: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showFullSyncConfirm by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    // Only title needs pre-resolving (plain String used in LaunchedEffect suspend block)
    val title = stringResource(SyncStrings.tidepool)

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiStrings.back)
                        )
                    }
                },
                actions = {
                    // Login and Logout change the whole session, so they stay on top. The other actions are at the bottom.
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    if (state.loggedIn)
                        IconButton(onClick = { showLogoutConfirm = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(CoreUiStrings.logout)
                            )
                        }
                    else
                        IconButton(onClick = onLogin) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = stringResource(CoreUiStrings.login)
                            )
                        }
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(CoreUiStrings.nav_plugin_preferences)
                            )
                        }
                    }
                }
            )
        )
    }

    TidepoolScreenContent(
        uiState = uiState,
        dateUtil = dateUtil,
        onUploadNow = onUploadNow,
        onFullSync = { showFullSyncConfirm = true },
        onClearLog = onClearLog,
        onPurge = { showPurgeConfirm = true },
        modifier = modifier
    )

    if (showLogoutConfirm) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.logout),
            message = stringResource(SyncStrings.tidepool_logout_confirm),
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }
    if (showFullSyncConfirm) {
        OkCancelDialog(
            title = stringResource(SyncStrings.tidepool),
            message = stringResource(SyncStrings.tidepool_full_sync_comment),
            onConfirm = {
                showFullSyncConfirm = false
                onFullSync()
            },
            onDismiss = { showFullSyncConfirm = false }
        )
    }
    if (showPurgeConfirm) {
        OkCancelDialog(
            title = stringResource(SyncStrings.tidepool_purge_data),
            message = stringResource(SyncStrings.tidepool_purge_data_confirm),
            onConfirm = {
                showPurgeConfirm = false
                onPurge()
            },
            onDismiss = { showPurgeConfirm = false }
        )
    }
}

/**
 * @see TidepoolScreenPreview
 */
@Composable
internal fun TidepoolScreenContent(
    uiState: TidepoolUiState,
    dateUtil: DateUtil? = null,
    onUploadNow: () -> Unit = {},
    onFullSync: () -> Unit = {},
    onClearLog: () -> Unit = {},
    onPurge: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SyncLogLayout(
        logList = uiState.logList,
        dateUtil = dateUtil,
        actions = listOf(
            ScreenAction(label = stringResource(SyncStrings.upload_now), icon = Icons.Filled.CloudUpload, onClick = onUploadNow),
            fullSyncAction(onFullSync),
            clearLogAction(onClearLog),
            ScreenAction(label = stringResource(SyncStrings.tidepool_purge_data), icon = Icons.Filled.DeleteForever, onClick = onPurge)
        ),
        modifier = modifier
    ) {
        LabelValueRow(
            label = stringResource(SyncStrings.status_label),
            value = connectionStatusText(uiState.connectionStatus),
            boldValue = true
        )
    }
}

@Composable
private fun connectionStatusText(status: ConnectionStatus): String = when (status) {
    // AuthFlowOut never reports NONE, so it means "not known yet"
    ConnectionStatus.NONE                -> ""
    ConnectionStatus.NOT_LOGGED_IN       -> stringResource(SyncStrings.tidepool_status_not_logged_in)
    ConnectionStatus.NO_SESSION          -> stringResource(SyncStrings.tidepool_status_no_session)
    ConnectionStatus.BLOCKED             -> stringResource(SyncStrings.tidepool_status_blocked)
    ConnectionStatus.FETCHING_TOKEN      -> stringResource(CoreUiStrings.connecting)
    ConnectionStatus.SESSION_ESTABLISHED -> stringResource(InterfacesStrings.connected)
    ConnectionStatus.FAILED              -> stringResource(SyncStrings.tidepool_status_failed)
}
