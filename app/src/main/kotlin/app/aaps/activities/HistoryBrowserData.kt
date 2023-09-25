package app.aaps.activities

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.main.workflow.CalculationWorkflow
import app.aaps.implementation.overview.OverviewDataImpl
import dagger.android.HasAndroidInjector
import info.nightscout.database.impl.AppRepository
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