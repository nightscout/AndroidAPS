package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

class SmsCommunicatorComposeContent(
    private val viewModelFactory: ViewModelProvider.Factory
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
        val viewModel: SmsCommunicatorViewModel = remember(viewModelStoreOwner) {
            ViewModelProvider(viewModelStoreOwner, viewModelFactory)[SmsCommunicatorViewModel::class.java]
        }

        SmsCommunicatorScreen(viewModel = viewModel)
    }
}
