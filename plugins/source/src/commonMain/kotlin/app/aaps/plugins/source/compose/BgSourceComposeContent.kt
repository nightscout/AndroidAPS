package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel

/**
 * Compose content provider for BG Source plugins.
 * This class is shared by all BG source plugins (Dexcom, xDrip, etc.) since they all
 * use the same UI to display blood glucose readings.
 *
 * Public so a source in another module (Dexcom G7 Direct in `:cgm:dexcomg7`) shows the same list.
 * [extraActions] adds toolbar buttons in front of the list's own, for a source with screens of its own.
 */
class BgSourceComposeContent(
    private val title: String,
    private val extraActions: (@Composable RowScope.() -> Unit)? = null
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: BgSourceViewModel = metroViewModel()
        val extra = extraActions

        BgSourceScreen(
            viewModel = viewModel,
            title = title,
            setToolbarConfig = if (extra == null) setToolbarConfig else { config ->
                setToolbarConfig(
                    config.copy(actions = {
                        extra()
                        config.actions(this)
                    })
                )
            },
            onNavigateBack = onNavigateBack,
            onSettings = onSettings
        )
    }
}
