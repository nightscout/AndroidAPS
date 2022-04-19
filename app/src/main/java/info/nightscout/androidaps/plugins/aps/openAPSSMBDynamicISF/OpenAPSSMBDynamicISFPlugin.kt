package info.nightscout.androidaps.plugins.aps.openAPSSMBDynamicISF

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader
import info.nightscout.androidaps.interfaces.DetermineBasalAdapterInterface
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.Profiler
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class OpenAPSSMBDynamicISFPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    constraintChecker: ConstraintChecker,
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    context: Context,
    activePlugin: ActivePlugin,
    iobCobCalculator: IobCobCalculator,
    hardLimits: HardLimits,
    profiler: Profiler,
    sp: SP,
    dateUtil: DateUtil,
    repository: AppRepository,
    glucoseStatusProvider: GlucoseStatusProvider,
    private val buildHelper: BuildHelper
) : OpenAPSSMBPlugin(
    injector,
    aapsLogger,
    rxBus,
    constraintChecker,
    rh,
    profileFunction,
    context,
    activePlugin,
    iobCobCalculator,
    hardLimits,
    profiler,
    sp,
    dateUtil,
    repository,
    glucoseStatusProvider
) {

    init {
        pluginDescription
            .pluginName(R.string.openaps_smb_dynamic_isf)
            .description(R.string.description_smb_dynamic_isf)
            .shortName(R.string.dynisf_shortname)
            .preferencesId(R.xml.pref_openapssmbdynamicisf)
            .setDefault(false)
    }

    override fun specialEnableCondition(): Boolean = buildHelper.isEngineeringMode() && buildHelper.isDev()

    override fun provideDetermineBasalAdapter(): DetermineBasalAdapterInterface = DetermineBasalAdapterSMBDynamicISFJS(ScriptReader(context), injector)
}