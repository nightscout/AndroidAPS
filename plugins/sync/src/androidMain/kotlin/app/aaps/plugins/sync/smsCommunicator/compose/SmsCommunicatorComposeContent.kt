package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

class SmsCommunicatorComposeContent : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: SmsCommunicatorViewModel = metroViewModel()

        SmsCommunicatorScreen(viewModel = viewModel)
    }
}
