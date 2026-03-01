package app.aaps.plugins.source.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

/**
 * Compose content provider for BG Source plugins.
 * This class is shared by all BG source plugins (Dexcom, xDrip, etc.) since they all
 * use the same UI to display blood glucose readings.
 */
internal class BgSourceComposeContent(
    private val viewModelFactory: ViewModelProvider.Factory,
    private val title: String
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
        val viewModel: BgSourceViewModel = remember(viewModelStoreOwner) {
            ViewModelProvider(viewModelStoreOwner, viewModelFactory)[BgSourceViewModel::class.java]
        }

        BgSourceScreen(
            viewModel = viewModel,
            title = title,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings
        )
    }
}
