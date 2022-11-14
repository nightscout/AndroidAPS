package info.nightscout.androidaps.activities

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.workflow.CalculationWorkflow
import info.nightscout.core.fabric.FabricPrivacy
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
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
    calculationWorkflow: CalculationWorkflow
) {

    var iobCobCalculator: IobCobCalculatorPlugin
    var overviewData: OverviewData

    init {
        // We don't want to use injected singletons but own instance working on top of different data
        overviewData =
            OverviewData(
                aapsLogger,
                rh,
                dateUtil,
                sp,
                activePlugin,
                defaultValueHelper,
                profileFunction,
                repository
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
                calculationWorkflow
            )
    }
}