package info.nightscout.plugins.aps.openAPSSMBDynamicISF

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.annotations.OpenForTesting
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.aps.DetermineBasalAdapter
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import info.nightscout.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.plugins.aps.utils.ScriptReader
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class OpenAPSSMBDynamicISFPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    constraintChecker: Constraints,
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
    bgQualityCheck: BgQualityCheck,
    tddCalculator: TddCalculator
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
    glucoseStatusProvider,
    bgQualityCheck,
    tddCalculator
) {

    init {
        pluginDescription
            .pluginName(R.string.openaps_smb_dynamic_isf)
            .description(R.string.description_smb_dynamic_isf)
            .shortName(R.string.dynisf_shortname)
            .preferencesId(R.xml.pref_openapssmbdynamicisf)
            .setDefault(false)
    }

    // If there is no TDD data fallback to SMB as ISF calculation may be really off
    override fun provideDetermineBasalAdapter(): DetermineBasalAdapter =
        if (tdd1D == null || tdd7D == null || tddLast4H == null || tddLast8to4H == null || tddLast24H == null || !dynIsfEnabled.value())
            DetermineBasalAdapterSMBJS(ScriptReader(context), injector)
        else DetermineBasalAdapterSMBDynamicISFJS(ScriptReader(context), injector)
}