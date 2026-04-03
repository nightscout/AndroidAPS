package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

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
        val viewModel: BgSourceViewModel = hiltViewModel()

        BgSourceScreen(
            viewModel = viewModel,
            title = title,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings
        )
    }
}
