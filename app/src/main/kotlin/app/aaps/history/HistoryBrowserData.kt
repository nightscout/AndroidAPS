package app.aaps.history

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.implementation.overview.OverviewDataImpl
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.ui.compose.history.HistoryScope
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class HistoryBrowserData @Inject constructor(
    aapsSchedulers: AapsSchedulers,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    dateUtil: DateUtil,
    preferences: Preferences,
    activePlugin: ActivePlugin,
    profileFunction: ProfileFunction,
    persistenceLayer: PersistenceLayer,
    fabricPrivacy: FabricPrivacy,
    calculationWorkflow: CalculationWorkflow,
    decimalFormatter: DecimalFormatter,
    processedTbrEbData: ProcessedTbrEbData,
    overviewDataCacheFactory: OverviewDataCacheFactory
) : HistoryScope {

    // We don't want to use injected singletons but own instance working on top of different data
    override val overviewData: OverviewData =
        OverviewDataImpl(rh, dateUtil, activePlugin, profileFunction, persistenceLayer, processedTbrEbData)
    override val signals: CalculationSignalsEmitter = CalculationSignalsImpl()

    // Lazy lookup breaks the cache ↔ iobCobCalculator construction cycle.
    override val cache: OverviewDataCache = overviewDataCacheFactory.create(
        iobCobCalculatorProvider = { iobCobCalculator },
        signals = signals,
        observeDatabase = false
    )
    override val iobCobCalculator: IobCobCalculator =
        IobCobCalculatorPlugin(
            aapsLogger, aapsSchedulers, rxBus, preferences, rh, profileFunction, activePlugin,
            fabricPrivacy, dateUtil, persistenceLayer, overviewData, calculationWorkflow, decimalFormatter, processedTbrEbData,
            signals, Provider { cache }
        )

    override fun onDestroy() {
    }
}