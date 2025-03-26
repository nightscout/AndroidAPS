package app.aaps.activities

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.general.overview.OverviewDataImpl
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import javax.inject.Inject
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
    processedTbrEbData: ProcessedTbrEbData
) {

    // We don't want to use injected singletons but own instance working on top of different data
    val overviewData =
        OverviewDataImpl(rh, dateUtil, preferences, activePlugin, profileFunction, persistenceLayer, processedTbrEbData)
    val iobCobCalculator =
        IobCobCalculatorPlugin(
            aapsLogger, aapsSchedulers, rxBus, preferences, rh, profileFunction, activePlugin,
            fabricPrivacy, dateUtil, persistenceLayer, overviewData, calculationWorkflow, decimalFormatter, processedTbrEbData
        )
}