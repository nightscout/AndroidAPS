package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel

/**
 * Compose content provider for BG Source plugins.
 * This class is shared by all BG source plugins (Dexcom, xDrip, etc.) since they all
 * use the same UI to display blood glucose readings.
 */
internal class BgSourceComposeContent(
    private val title: String
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: BgSourceViewModel = metroViewModel()

        BgSourceScreen(
            viewModel = viewModel,
            title = title,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings
        )
    }
}
