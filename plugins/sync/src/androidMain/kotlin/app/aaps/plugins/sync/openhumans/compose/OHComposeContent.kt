package app.aaps.plugins.sync.openhumans.compose

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
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
        val viewModel: OHViewModel = metroViewModel()

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
