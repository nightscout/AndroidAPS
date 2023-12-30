package app.aaps.plugins.aps.openAPSSMBAutoISF

import android.content.Context
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.Preferences
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.aps.utils.ScriptReader
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAPSSMBAutoISFPlugin @Inject constructor(
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
    private val preferences: Preferences,
    dateUtil: DateUtil,
    persistenceLayer: PersistenceLayer,
    glucoseStatusProvider: GlucoseStatusProvider,
    bgQualityCheck: BgQualityCheck,
    tddCalculator: TddCalculator,
    importExportPrefs: ImportExportPrefs,
    config: Config,
    private val objectives: Objectives
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
    processedTbrEbData,
    hardLimits,
    profiler,
    preferences,
    dateUtil,
    persistenceLayer,
    glucoseStatusProvider,
    bgQualityCheck,
    tddCalculator,
    importExportPrefs,
    config
) {

    init {
        pluginDescription
            .pluginName(R.string.openaps_smb_auto_isf)
            .description(R.string.description_smb_auto_isf)
            .shortName(R.string.autoisf_shortname)
            .preferencesId(R.xml.pref_openapssmbautoisf)
            .showInList(config.isEngineeringMode() && config.isDev())
            .preferencesVisibleInSimpleMode(true)
            .setDefault(false)
    }

    override fun specialEnableCondition(): Boolean =
        objectives.isStarted(Objectives.DYN_ISF_OBJECTIVE)

    override fun provideDetermineBasalAdapter(): DetermineBasalAdapter = DetermineBasalAdapterSMBAutoISFJS(ScriptReader(context), injector)

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseAutosens)
        if (!enabled) value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        return value
    }
}