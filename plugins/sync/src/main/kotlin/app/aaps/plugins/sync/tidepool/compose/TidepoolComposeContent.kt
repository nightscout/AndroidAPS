package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

internal class TidepoolComposeContent(
    private val viewModelFactory: ViewModelProvider.Factory,
    private val dateUtil: DateUtil,
    private val onLogin: () -> Unit,
    private val onLogout: () -> Unit,
    private val onUploadNow: () -> Unit,
    private val onFullSync: () -> Unit,
    private val onClearLog: () -> Unit
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
        val viewModel: TidepoolViewModel = remember(viewModelStoreOwner) {
            ViewModelProvider(viewModelStoreOwner, viewModelFactory)[TidepoolViewModel::class.java]
        }

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
            onClearLog = onClearLog
        )
    }
}
