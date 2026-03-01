package app.aaps.plugins.sync.wear.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

internal class WearComposeContent(
    private val viewModelFactory: ViewModelProvider.Factory
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
        val viewModel: WearViewModel = remember(viewModelStoreOwner) {
            ViewModelProvider(viewModelStoreOwner, viewModelFactory)[WearViewModel::class.java]
        }

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
