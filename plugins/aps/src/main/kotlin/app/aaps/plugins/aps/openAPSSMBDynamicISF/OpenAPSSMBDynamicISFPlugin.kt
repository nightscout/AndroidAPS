package app.aaps.plugins.aps.openAPSSMBDynamicISF

import android.content.Context
import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.aps.utils.ScriptReader
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class OpenAPSSMBDynamicISFPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    constraintChecker: ConstraintsChecker,
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

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(false, rh.gs(R.string.autosens_disabled_in_dyn_isf), this)
        return value
    }
}