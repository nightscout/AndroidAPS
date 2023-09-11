package info.nightscout.androidaps.activities

import dagger.android.HasAndroidInjector
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.core.workflow.CalculationWorkflow
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.overview.OverviewDataImpl
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
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