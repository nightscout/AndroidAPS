package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.events.EventTidepoolDoUpload
import app.aaps.plugins.sync.tidepool.keys.TidepoolLongNonKey

class TidepoolComposeContent(
    private val tidepoolRepository: TidepoolRepository,
    private val authFlowOut: AuthFlowOut,
    private val tidepoolUploader: TidepoolUploader,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel = remember {
            TidepoolViewModel(
                tidepoolRepository = tidepoolRepository,
                authFlowOut = authFlowOut
            ).also { it.loadInitialData() }
        }

        TidepoolScreen(
            viewModel = viewModel,
            dateUtil = dateUtil,
            rh = rh,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings,
            onLogin = { authFlowOut.doTidePoolInitialLogin("menu") },
            onLogout = {
                authFlowOut.clearAllSavedData()
                tidepoolUploader.resetInstance()
            },
            onUploadNow = { rxBus.send(EventTidepoolDoUpload()) },
            onFullSync = { preferences.put(TidepoolLongNonKey.LastEnd, 0) },
            onClearLog = { tidepoolRepository.clearLog() }
        )
    }
}
