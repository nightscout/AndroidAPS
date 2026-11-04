package app.aaps.pump.aaruy.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.pump.aaruy.R

/** Empty for now. The overview, pairing, replace and history screens come with the Compose UI step. */
class AaruyComposeContent : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        Text(stringResource(R.string.aaruy_pump))
    }
}
