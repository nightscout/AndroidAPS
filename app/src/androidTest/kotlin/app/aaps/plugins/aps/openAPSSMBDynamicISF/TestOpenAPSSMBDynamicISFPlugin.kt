package app.aaps.plugins.aps.openAPSSMBDynamicISF

import android.content.Context
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.aps.openAPSSMB.TestOpenAPSSMBPlugin
import app.aaps.plugins.aps.utils.ScriptReader
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestOpenAPSSMBDynamicISFPlugin @Inject constructor(
    private val injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    constraintChecker: ConstraintsChecker,
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    context: Context,
    activePlugin: ActivePlugin,
    iobCobCalculator: IobCobCalculator,
    processedTbrEbData: ProcessedTbrEbData,
    hardLimits: HardLimits,
    profiler: Profiler,
    preferences: Preferences,
    dateUtil: DateUtil,
    persistenceLayer: PersistenceLayer,
    glucoseStatusProvider: GlucoseStatusProvider,
    bgQualityCheck: BgQualityCheck,
    tddCalculator: TddCalculator,
    private val uiInteraction: UiInteraction,
    glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB
) : TestOpenAPSSMBPlugin(
    injector,
    aapsLogger,
    rxBus,
    constraintChecker,
    rh,
    profileFunction,
    context,
    activePlugin,
    iobCobCalculator,
    processedTbrEbData,
    hardLimits,
    profiler,
    preferences,
    dateUtil,
    persistenceLayer,
    glucoseStatusProvider,
    bgQualityCheck,
    tddCalculator,
    glucoseStatusCalculatorSMB
) {

    init {
        pluginDescription
            .pluginName(R.string.openaps_smb_dynamic_isf)
            .description(R.string.description_smb_dynamic_isf)
            .shortName(R.string.dynisf_shortname)
            .preferencesVisibleInSimpleMode(true)
            .setDefault(false)
    }

    // override fun specialEnableCondition(): Boolean =
    //     objectives.isStarted(Objectives.DYN_ISF_OBJECTIVE)

    // If there is no TDD data fallback to SMB as ISF calculation may be really off
    override fun provideDetermineBasalAdapter(): DetermineBasalAdapter =
        if (tdd1D == null || tdd7D == null || tddLast4H == null || tddLast8to4H == null || tddLast24H == null || !dynIsfEnabled.value()) {
            uiInteraction.addNotificationValidTo(
                Notification.SMB_FALLBACK, dateUtil.now(),
                rh.gs(R.string.fallback_smb_no_tdd), Notification.INFO, dateUtil.now() + T.mins(1).msecs()
            )
            DetermineBasalAdapterSMBJS(ScriptReader(), injector)
        } else {
            uiInteraction.dismissNotification(Notification.SMB_FALLBACK)
            DetermineBasalAdapterSMBDynamicISFJS(ScriptReader(), injector)
        }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        //value.set(false, rh.gs(R.string.autosens_disabled_in_dyn_isf), this)
        return value
    }
}