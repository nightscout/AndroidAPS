package app.aaps.pump.virtual

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.PumpOverviewScreen

class VirtualPumpComposeContent(
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val rh: ResourceHelper,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val ch: ConcentrationHelper,
    private val rxBus: RxBus,
    private val commandQueue: CommandQueue
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val viewModel = remember {
            VirtualPumpViewModel(
                virtualPumpPlugin = virtualPumpPlugin,
                rh = rh,
                pumpSync = pumpSync,
                dateUtil = dateUtil,
                persistenceLayer = persistenceLayer,
                preferences = preferences,
                ch = ch,
                rxBus = rxBus,
                commandQueue = commandQueue,
                context = context,
                scope = scope
            )
        }

        val state = viewModel.uiState.collectAsStateWithLifecycle()

        PumpOverviewScreen(state = state.value)
    }
}
