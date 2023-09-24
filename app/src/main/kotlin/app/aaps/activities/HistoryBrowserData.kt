package app.aaps.activities

import app.aaps.interfaces.logging.AAPSLogger
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.profile.DefaultValueHelper
import app.aaps.interfaces.profile.ProfileFunction
import app.aaps.interfaces.resources.ResourceHelper
import app.aaps.interfaces.rx.AapsSchedulers
import app.aaps.interfaces.rx.bus.RxBus
import app.aaps.interfaces.sharedPreferences.SP
import app.aaps.interfaces.utils.DateUtil
import app.aaps.interfaces.utils.DecimalFormatter
import dagger.android.HasAndroidInjector
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.overview.OverviewDataImpl
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryBrowserData @Inject constructor(
    injector: HasAndroidInjector,
    aapsSchedulers: AapsSchedulers,
    rxBus: RxBus,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    dateUtil: DateUtil,
    sp: SP,
    activePlugin: ActivePlugin,
    defaultValueHelper: DefaultValueHelper,
    profileFunction: ProfileFunction,
    repository: AppRepository,
    fabricPrivacy: FabricPrivacy,
    calculationWorkflow: CalculationWorkflow,
    decimalFormatter: DecimalFormatter
) {

    var iobCobCalculator: IobCobCalculatorPlugin
    var overviewData: OverviewData

    init {
        // We don't want to use injected singletons but own instance working on top of different data
        overviewData =
            OverviewDataImpl(
                aapsLogger,
                rh,
                dateUtil,
                sp,
                activePlugin,
                defaultValueHelper,
                profileFunction,
                repository,
                decimalFormatter
            )
        iobCobCalculator =
            IobCobCalculatorPlugin(
                injector,
                aapsLogger,
                aapsSchedulers,
                rxBus,
                sp,
                rh,
                profileFunction,
                activePlugin,
                fabricPrivacy,
                dateUtil,
                repository,
                overviewData,
                calculationWorkflow,
                decimalFormatter
            )
    }
}