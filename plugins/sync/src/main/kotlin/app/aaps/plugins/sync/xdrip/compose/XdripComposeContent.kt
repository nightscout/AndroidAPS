package app.aaps.plugins.sync.xdrip.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.plugins.sync.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class XdripComposeContent(
    private val dateUtil: DateUtil,
    private val dataSyncSelector: DataSyncSelectorXdrip,
    private val onClearLog: () -> Unit,
    private val onFullSync: suspend () -> Unit
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()
        val viewModel: XdripViewModel = hiltViewModel()

        LaunchedEffect(Unit) { viewModel.loadInitialData() }

        var showFullSyncDialog by remember { mutableStateOf(false) }

        if (showFullSyncDialog) {
            OkCancelDialog(
                title = stringResource(R.string.xdrip),
                message = stringResource(R.string.full_xdrip_sync_comment),
                onConfirm = {
                    showFullSyncDialog = false
                    scope.launch(Dispatchers.IO) {
                        onFullSync()
                    }
                },
                onDismiss = { showFullSyncDialog = false }
            )
        }

        XdripScreen(
            viewModel = viewModel,
            dateUtil = dateUtil,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings,
            onClearLog = onClearLog,
            onFullSync = { showFullSyncDialog = true }
        )
    }
}
