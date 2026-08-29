package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

internal class TidepoolComposeContent(
    private val dateUtil: DateUtil,
    private val onLogin: () -> Unit,
    private val onLogout: () -> Unit,
    private val onUploadNow: () -> Unit,
    private val onFullSync: () -> Unit,
    private val onPurge: () -> Unit,
    private val onClearLog: () -> Unit
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: TidepoolViewModel = metroViewModel()

        LaunchedEffect(Unit) {
            viewModel.loadInitialData()
        }

        TidepoolScreen(
            viewModel = viewModel,
            dateUtil = dateUtil,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings,
            onLogin = onLogin,
            onLogout = onLogout,
            onUploadNow = onUploadNow,
            onFullSync = onFullSync,
            onPurge = onPurge,
            onClearLog = onClearLog
        )
    }
}
