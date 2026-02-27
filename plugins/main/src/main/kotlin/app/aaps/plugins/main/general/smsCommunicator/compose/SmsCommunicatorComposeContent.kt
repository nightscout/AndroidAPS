package app.aaps.plugins.main.general.smsCommunicator.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

class SmsCommunicatorComposeContent(
    private val repository: SmsCommunicatorRepository,
    private val dateUtil: DateUtil
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel = remember {
            SmsCommunicatorViewModel(
                repository = repository,
                dateUtil = dateUtil
            )
        }

        SmsCommunicatorScreen(viewModel = viewModel)
    }
}
