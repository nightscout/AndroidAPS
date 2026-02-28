package app.aaps.plugins.sync.wear.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.wear.WearPlugin

class WearComposeContent(
    private val wearPlugin: WearPlugin,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val importExportPrefs: ImportExportPrefs,
    private val preferences: Preferences,
    private val versionCheckerUtils: VersionCheckerUtils,
    private val aapsLogger: AAPSLogger
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val viewModel = remember {
            WearViewModel(
                wearPlugin = wearPlugin,
                rxBus = rxBus,
                rh = rh,
                dateUtil = dateUtil,
                preferences = preferences,
                versionCheckerUtils = versionCheckerUtils,
                aapsLogger = aapsLogger
            ).also { it.requestCustomWatchface() }
        }

        WearScreen(
            viewModel = viewModel,
            rh = rh,
            importExportPrefs = importExportPrefs,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings
        )
    }
}
