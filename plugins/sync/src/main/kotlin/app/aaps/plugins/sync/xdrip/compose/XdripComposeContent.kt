package app.aaps.plugins.sync.xdrip.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.plugins.sync.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class XdripComposeContent(
    private val xdripMvvmRepository: XdripMvvmRepository,
    private val dataSyncSelector: DataSyncSelectorXdrip,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()
        val viewModel = remember {
            XdripViewModel(
                rh = rh,
                xdripMvvmRepository = xdripMvvmRepository,
                dataSyncSelector = dataSyncSelector
            ).also { it.loadInitialData() }
        }

        var showFullSyncDialog by remember { mutableStateOf(false) }

        if (showFullSyncDialog) {
            OkCancelDialog(
                title = rh.gs(R.string.xdrip),
                message = rh.gs(R.string.full_xdrip_sync_comment),
                onConfirm = {
                    showFullSyncDialog = false
                    scope.launch(Dispatchers.IO) {
                        dataSyncSelector.resetToNextFullSync()
                    }
                },
                onDismiss = { showFullSyncDialog = false }
            )
        }

        XdripScreen(
            viewModel = viewModel,
            dateUtil = dateUtil,
            rh = rh,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings,
            onClearLog = { xdripMvvmRepository.clearLog() },
            onFullSync = { showFullSyncDialog = true }
        )
    }
}
