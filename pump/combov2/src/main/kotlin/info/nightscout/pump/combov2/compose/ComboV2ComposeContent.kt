package info.nightscout.pump.combov2.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

class ComboV2ComposeContent(
    private val pluginName: String
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel: ComboV2OverviewViewModel = hiltViewModel()

        val navIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                )
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.settings)
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            setToolbarConfig(
                ToolbarConfig(
                    title = pluginName,
                    navigationIcon = navIcon,
                    actions = settingsAction
                )
            )
        }

        ComboV2OverviewScreen(viewModel = viewModel)
    }
}
