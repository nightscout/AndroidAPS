package app.aaps.plugins.sync.wear.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

internal class WearComposeContent : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: WearViewModel = metroViewModel()

        LaunchedEffect(Unit) {
            viewModel.requestCustomWatchface()
        }

        WearScreen(
            viewModel = viewModel,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings
        )
    }
}
