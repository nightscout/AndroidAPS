package app.aaps.plugins.sync.openhumans.compose

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.MetroViewModelFactoryOwner
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import app.aaps.plugins.sync.openhumans.ui.OHLoginActivity

internal class OHComposeContent(
    private val plugin: OpenHumansUploaderPlugin,
    private val context: Context
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        // hiltViewModel() only worked because the host activity is @AndroidEntryPoint. The Metro
        // factory is reached the same way member injection is - through the Application.
        val viewModel: OHViewModel = viewModel(
            factory = (context.applicationContext as MetroViewModelFactoryOwner).metroViewModelFactory
        )

        OHScreen(
            viewModel = viewModel,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSetup = {
                context.startActivity(
                    Intent(context, OHLoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onLogout = { plugin.logout() },
            onUploadNow = { plugin.uploadNow() }
        )
    }
}
