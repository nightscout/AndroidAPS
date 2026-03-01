package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.validators.preferences.AdaptiveClickPreference
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.keys.OverviewIntentKey
import app.aaps.plugins.main.general.overview.keys.OverviewStringKey
import app.aaps.shared.impl.rx.bus.RxBusImpl
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val overviewData: OverviewData,
    private val overviewMenus: OverviewMenus,
    private val context: Context,
    private val constraintsChecker: ConstraintsChecker,
    private val uiInteraction: UiInteraction,
    private val nsSettingStatus: NSSettingsStatus,
    private val config: Config,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val notificationManager: NotificationManager,
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewFragment::class.qualifiedName)
        .alwaysVisible(true)
        .alwaysEnabled(true)
        .simpleModePosition(PluginDescription.Position.TAB)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_home)
        .pluginName(app.aaps.core.ui.R.string.overview)
        .shortName(R.string.overview_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_overview),
    ownPreferences = listOf(OverviewStringKey::class.java, OverviewIntentKey::class.java),
    aapsLogger, rh, preferences
), Overview {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override val overviewBus = RxBusImpl(aapsSchedulers, aapsLogger)

    override fun onStart() {
        super.onStart()
        overviewMenus.loadGraphConfig()
        overviewData.initRange()

        notificationManager.createNotificationChannel()

        disposable += rxBus
            .toObservable(EventIobCalculationProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           overviewData.calcProgressPct = it.finalPercent
                           overviewBus.send(EventUpdateOverviewCalcProgress("EventIobCalculationProgress"))
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           overviewData.pumpStatus = it.getStatus(context)
                       }, fabricPrivacy::logException)

    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun configuration(): JsonObject =
        JsonObject(emptyMap())
            .put(StringKey.GeneralUnits, preferences)
            .put(StringNonKey.QuickWizard, preferences)
            .put(IntKey.OverviewEatingSoonDuration, preferences)
            .put(UnitDoubleKey.OverviewEatingSoonTarget, preferences)
            .put(IntKey.OverviewActivityDuration, preferences)
            .put(UnitDoubleKey.OverviewActivityTarget, preferences)
            .put(IntKey.OverviewHypoDuration, preferences)
            .put(UnitDoubleKey.OverviewHypoTarget, preferences)
            .put(UnitDoubleKey.OverviewLowMark, preferences)
            .put(UnitDoubleKey.OverviewHighMark, preferences)
            .put(IntKey.OverviewCageWarning, preferences)
            .put(IntKey.OverviewCageCritical, preferences)
            .put(IntKey.OverviewIageWarning, preferences)
            .put(IntKey.OverviewIageCritical, preferences)
            .put(IntKey.OverviewSageWarning, preferences)
            .put(IntKey.OverviewSageCritical, preferences)
            .put(IntKey.OverviewSbatWarning, preferences)
            .put(IntKey.OverviewSbatCritical, preferences)
            .put(IntKey.OverviewBageWarning, preferences)
            .put(IntKey.OverviewBageCritical, preferences)
            .put(IntKey.OverviewResWarning, preferences)
            .put(IntKey.OverviewResCritical, preferences)
            .put(IntKey.OverviewBattWarning, preferences)
            .put(IntKey.OverviewBattCritical, preferences)
            .put(IntKey.OverviewBolusPercentage, preferences)
            .put(BooleanNonKey.AutosensUsedOnMainPhone, constraintsChecker.isAutosensModeEnabled().value())

    override fun applyConfiguration(configuration: JsonObject) {
        configuration
            .store(StringKey.GeneralUnits, preferences)
            .store(StringNonKey.QuickWizard, preferences)
            .store(IntKey.OverviewEatingSoonDuration, preferences)
            .store(UnitDoubleKey.OverviewEatingSoonTarget, preferences)
            .store(IntKey.OverviewActivityDuration, preferences)
            .store(UnitDoubleKey.OverviewActivityTarget, preferences)
            .store(IntKey.OverviewHypoDuration, preferences)
            .store(UnitDoubleKey.OverviewHypoTarget, preferences)
            .store(UnitDoubleKey.OverviewLowMark, preferences)
            .store(UnitDoubleKey.OverviewHighMark, preferences)
            .store(IntKey.OverviewCageWarning, preferences)
            .store(IntKey.OverviewCageCritical, preferences)
            .store(IntKey.OverviewIageWarning, preferences)
            .store(IntKey.OverviewIageCritical, preferences)
            .store(IntKey.OverviewSageWarning, preferences)
            .store(IntKey.OverviewSageCritical, preferences)
            .store(IntKey.OverviewSbatWarning, preferences)
            .store(IntKey.OverviewSbatCritical, preferences)
            .store(IntKey.OverviewBageWarning, preferences)
            .store(IntKey.OverviewBageCritical, preferences)
            .store(IntKey.OverviewResWarning, preferences)
            .store(IntKey.OverviewResCritical, preferences)
            .store(IntKey.OverviewBattWarning, preferences)
            .store(IntKey.OverviewBattCritical, preferences)
            .store(IntKey.OverviewBolusPercentage, preferences)
            .store(BooleanNonKey.AutosensUsedOnMainPhone, preferences)
    }

    @SuppressLint("SetTextI18n")
    override fun setVersionView(view: TextView) {
        if (config.APS || config.PUMPCONTROL) {
            view.text = "${config.VERSION_NAME} (${config.HEAD.substring(0, 4)})"
            if (config.COMMITTED) {
                view.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.omniGrayColor))
                view.alpha = 1.0f
            } else if (preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME) != 0L) {
                view.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.metadataTextWarningColor))
            } else {
                view.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.urgentColor))
            }
        } else view.text = ""
    }

    // TODO: Remove after full migration to Compose preferences (getPreferenceScreenContent)
    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "overview_buttons_settings" && requiredKey != "default_temp_targets_settings" && requiredKey != "prime_fill_settings" && requiredKey != "range_settings" && requiredKey != "statuslights_overview_advanced" && requiredKey != "overview_advanced_settings") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "overview_settings"
            title = rh.gs(app.aaps.core.ui.R.string.overview)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewKeepScreenOn, summary = app.aaps.core.keys.R.string.pref_summary_keep_screen_on, title = app.aaps.core.keys.R.string.pref_title_keep_screen_on))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "overview_buttons_settings"
                title = rh.gs(R.string.overview_buttons_selection)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowTreatmentButton, title = app.aaps.core.ui.R.string.treatments))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowWizardButton, title = R.string.calculator_label))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowInsulinButton, title = app.aaps.core.ui.R.string.configbuilder_insulin))
                addPreference(
                    AdaptiveDoublePreference(
                        ctx = context,
                        doubleKey = DoubleKey.OverviewInsulinButtonIncrement1,
                        dialogMessage = app.aaps.core.keys.R.string.insulin_increment_button_message,
                        title = app.aaps.core.keys.R.string.pref_title_insulin_button_increment_1
                    )
                )
                addPreference(
                    AdaptiveDoublePreference(
                        ctx = context,
                        doubleKey = DoubleKey.OverviewInsulinButtonIncrement2,
                        dialogMessage = app.aaps.core.keys.R.string.insulin_increment_button_message,
                        title = app.aaps.core.keys.R.string.pref_title_insulin_button_increment_2
                    )
                )
                addPreference(
                    AdaptiveDoublePreference(
                        ctx = context,
                        doubleKey = DoubleKey.OverviewInsulinButtonIncrement3,
                        dialogMessage = app.aaps.core.keys.R.string.insulin_increment_button_message,
                        title = app.aaps.core.keys.R.string.pref_title_insulin_button_increment_3
                    )
                )
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowCarbsButton, title = app.aaps.core.ui.R.string.carbs))
                addPreference(
                    AdaptiveIntPreference(
                        ctx = context,
                        intKey = IntKey.OverviewCarbsButtonIncrement1,
                        dialogMessage = app.aaps.core.keys.R.string.carb_increment_button_message,
                        title = app.aaps.core.keys.R.string.pref_title_carbs_button_increment_1
                    )
                )
                addPreference(
                    AdaptiveIntPreference(
                        ctx = context,
                        intKey = IntKey.OverviewCarbsButtonIncrement2,
                        dialogMessage = app.aaps.core.keys.R.string.carb_increment_button_message,
                        title = app.aaps.core.keys.R.string.pref_title_carbs_button_increment_2
                    )
                )
                addPreference(
                    AdaptiveIntPreference(
                        ctx = context,
                        intKey = IntKey.OverviewCarbsButtonIncrement3,
                        dialogMessage = app.aaps.core.keys.R.string.carb_increment_button_message,
                        title = app.aaps.core.keys.R.string.pref_title_carbs_button_increment_3
                    )
                )
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowCgmButton, summary = app.aaps.core.keys.R.string.pref_summary_show_cgm_button, title = app.aaps.core.ui.R.string.cgm))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowCalibrationButton, summary = app.aaps.core.keys.R.string.pref_summary_show_calibration_button, title = app.aaps.core.ui.R.string.calibration))
            })
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context,
                    intentKey = OverviewIntentKey.QuickWizardSettings,
                    title = R.string.quickwizard_settings,
                    intent = Intent(context, uiInteraction.quickWizardListActivity)
                )
            )
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "default_temp_targets_settings"
                title = rh.gs(R.string.default_temptargets)
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewEatingSoonDuration, title = app.aaps.core.keys.R.string.pref_title_eating_soon_duration))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewEatingSoonTarget, title = app.aaps.core.keys.R.string.pref_title_eating_soon_target))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewActivityDuration, title = app.aaps.core.keys.R.string.pref_title_activity_duration))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewActivityTarget, title = app.aaps.core.keys.R.string.pref_title_activity_target))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewHypoDuration, title = app.aaps.core.keys.R.string.pref_title_hypo_duration))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewHypoTarget, title = app.aaps.core.keys.R.string.pref_title_hypo_target))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "prime_fill_settings"
                title = rh.gs(R.string.fill_bolus_title)
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ActionsFillButton1, title = app.aaps.core.keys.R.string.pref_title_fill_button_1))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ActionsFillButton2, title = app.aaps.core.keys.R.string.pref_title_fill_button_2))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ActionsFillButton3, title = app.aaps.core.keys.R.string.pref_title_fill_button_3))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "range_settings"
                title = rh.gs(app.aaps.core.keys.R.string.prefs_range_title)
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewLowMark, title = app.aaps.core.keys.R.string.pref_title_low_mark))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewHighMark, title = app.aaps.core.keys.R.string.pref_title_high_mark))
            })
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowNotesInDialogs, title = app.aaps.core.keys.R.string.overview_show_notes_field_in_dialogs_title))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                activePlugin.activePump
                key = "statuslights_overview_advanced"
                title = rh.gs(app.aaps.core.ui.R.string.statuslights)
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewCageWarning, title = app.aaps.core.keys.R.string.pref_title_cage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewCageCritical, title = app.aaps.core.keys.R.string.pref_title_cage_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewIageWarning, title = app.aaps.core.keys.R.string.pref_title_iage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewIageCritical, title = app.aaps.core.keys.R.string.pref_title_iage_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSageWarning, title = app.aaps.core.keys.R.string.pref_title_sage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSageCritical, title = app.aaps.core.keys.R.string.pref_title_sage_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSbatWarning, title = app.aaps.core.keys.R.string.pref_title_sbat_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSbatCritical, title = app.aaps.core.keys.R.string.pref_title_sbat_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewResWarning, title = app.aaps.core.keys.R.string.pref_title_res_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewResCritical, title = app.aaps.core.keys.R.string.pref_title_res_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBattWarning, title = app.aaps.core.keys.R.string.pref_title_batt_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBattCritical, title = app.aaps.core.keys.R.string.pref_title_batt_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBageWarning, title = app.aaps.core.keys.R.string.pref_title_bage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBageCritical, title = app.aaps.core.keys.R.string.pref_title_bage_critical))
                addPreference(
                    AdaptiveClickPreference(
                        ctx = context, stringKey = StringKey.OverviewCopySettingsFromNs, title = R.string.statuslights_copy_ns,
                        onPreferenceClickListener = {
                            applyStatusLightsFromNs(context)
                            true
                        })
                )
            })
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBolusPercentage, dialogMessage = app.aaps.core.keys.R.string.deliverpartofboluswizard, title = app.aaps.core.keys.R.string.deliverpartofboluswizard))
            addPreference(
                AdaptiveIntPreference(
                    ctx = context,
                    intKey = IntKey.OverviewResetBolusPercentageTime,
                    dialogMessage = app.aaps.core.keys.R.string.deliver_part_of_boluswizard_reset_time,
                    title = app.aaps.core.keys.R.string.pref_title_reset_bolus_percentage_time
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = BooleanKey.OverviewUseBolusAdvisor,
                    summary = app.aaps.core.keys.R.string.pref_summary_use_bolus_advisor,
                    title = app.aaps.core.keys.R.string.pref_title_use_bolus_advisor
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = BooleanKey.OverviewUseBolusReminder,
                    summary = app.aaps.core.keys.R.string.pref_summary_use_bolus_reminder,
                    title = app.aaps.core.keys.R.string.pref_title_use_bolus_reminder
                )
            )
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "overview_advanced_settings"
                title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewUseSuperBolus, summary = app.aaps.core.keys.R.string.pref_summary_use_super_bolus, title = app.aaps.core.keys.R.string.pref_title_use_super_bolus))
            })
        }
    }

    override fun applyStatusLightsFromNs(context: Context?) {
        if (context != null) uiInteraction.showOkCancelDialog(context = context, title = app.aaps.core.ui.R.string.statuslights, message = app.aaps.core.ui.R.string.copy_existing_values, ok = { applyStatusLightsFromNsExec() })
        else applyStatusLightsFromNsExec()
    }

    fun applyStatusLightsFromNsExec() {
        val cageWarn = nsSettingStatus.getExtendedWarnValue("cage", "warn")?.toInt()
        val cageCritical = nsSettingStatus.getExtendedWarnValue("cage", "urgent")?.toInt()
        val iageWarn = nsSettingStatus.getExtendedWarnValue("iage", "warn")?.toInt()
        val iageCritical = nsSettingStatus.getExtendedWarnValue("iage", "urgent")?.toInt()
        val sageWarn = nsSettingStatus.getExtendedWarnValue("sage", "warn")?.toInt()
        val sageCritical = nsSettingStatus.getExtendedWarnValue("sage", "urgent")?.toInt()
        val bageWarn = nsSettingStatus.getExtendedWarnValue("bage", "warn")?.toInt()
        val bageCritical = nsSettingStatus.getExtendedWarnValue("bage", "urgent")?.toInt()
        cageWarn?.let { preferences.put(IntKey.OverviewCageWarning, it) }
        cageCritical?.let { preferences.put(IntKey.OverviewCageCritical, it) }
        iageWarn?.let { preferences.put(IntKey.OverviewIageWarning, it) }
        iageCritical?.let { preferences.put(IntKey.OverviewIageCritical, it) }
        sageWarn?.let { preferences.put(IntKey.OverviewSageWarning, it) }
        sageCritical?.let { preferences.put(IntKey.OverviewSageCritical, it) }
        bageWarn?.let { preferences.put(IntKey.OverviewBageWarning, it) }
        bageCritical?.let { preferences.put(IntKey.OverviewBageCritical, it) }
        uel.log(Action.NS_SETTINGS_COPIED, Sources.NSClient)
    }

}
