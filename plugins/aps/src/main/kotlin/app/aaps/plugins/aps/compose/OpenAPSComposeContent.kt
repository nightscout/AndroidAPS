package app.aaps.plugins.aps.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

class OpenAPSComposeContent(
    private val apsPlugin: APS,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()
        val viewModel = remember {
            OpenAPSViewModel(
                apsPlugin = apsPlugin,
                rxBus = rxBus,
                rh = rh,
                dateUtil = dateUtil,
                scope = scope
            )
        }

        val state = viewModel.uiState.collectAsStateWithLifecycle()

        OpenAPSScreen(
            state = state.value,
            onRefresh = viewModel::onRefresh
        )
    }
}
