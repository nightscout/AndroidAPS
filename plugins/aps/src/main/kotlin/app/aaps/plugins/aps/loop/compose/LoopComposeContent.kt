package app.aaps.plugins.aps.loop.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig

class LoopComposeContent(
    private val loop: Loop,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()
        val viewModel = remember {
            LoopViewModel(
                loop = loop,
                rxBus = rxBus,
                rh = rh,
                dateUtil = dateUtil,
                decimalFormatter = decimalFormatter,
                aapsLogger = aapsLogger,
                preferences = preferences,
                scope = scope
            )
        }

        val state = viewModel.uiState.collectAsStateWithLifecycle()

        LoopScreen(
            state = state.value,
            onRefresh = viewModel::onRefresh
        )
    }
}
