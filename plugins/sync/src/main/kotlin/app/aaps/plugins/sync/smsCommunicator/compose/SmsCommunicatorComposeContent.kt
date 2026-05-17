package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

class SmsCommunicatorComposeContent : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: SmsCommunicatorViewModel = hiltViewModel()

        SmsCommunicatorScreen(viewModel = viewModel)
    }
}
