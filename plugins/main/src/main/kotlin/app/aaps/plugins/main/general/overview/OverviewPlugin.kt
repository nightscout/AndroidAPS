package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.validators.preferences.AdaptiveClickPreference
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.keys.OverviewStringKey
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver
import app.aaps.shared.impl.rx.bus.RxBusImpl
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val notificationStore: NotificationStore,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val overviewData: OverviewData,
    private val overviewMenus: OverviewMenus,
    private val context: Context,
    private val constraintsChecker: ConstraintsChecker,
    private val uiInteraction: UiInteraction,
    private val nsSettingStatus: NSSettingsStatus,
    private val config: Config
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewFragment::class.qualifiedName)
        .alwaysVisible(true)
        .alwaysEnabled(true)
        .simpleModePosition(PluginDescription.Position.TAB)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_home)
        .pluginName(R.string.overview)
        .shortName(R.string.overview_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_overview),
    ownPreferences = listOf(OverviewStringKey::class.java),
    aapsLogger, rh, preferences
), Overview {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override val overviewBus = RxBusImpl(aapsSchedulers, aapsLogger)

    override fun onStart() {
        super.onStart()
        registerLocalBroadcastReceiver()
        overviewMenus.loadGraphConfig()
        overviewData.initRange()

        notificationStore.createNotificationChannel()
        disposable += rxBus
            .toObservable(EventNewNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ n ->
                           if (notificationStore.add(n.notification))
                               overviewBus.send(EventUpdateOverviewNotification("EventNewNotification"))
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDismissNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ n ->
                           if (notificationStore.remove(n.id))
                               overviewBus.send(EventUpdateOverviewNotification("EventDismissNotification"))
                       }, fabricPrivacy::logException)
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
        unregisterLocalBroadcastReceiver()
        super.onStop()
    }

    override fun configuration(): JSONObject =
        JSONObject()
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
            .put(BooleanNonKey.AutosensUsedOnMainPhone.key, constraintsChecker.isAutosensModeEnabled().value())

    override fun applyConfiguration(configuration: JSONObject) {
        val previousUnits = preferences.getIfExists(StringKey.GeneralUnits) ?: "old"
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

        val newUnits = preferences.getIfExists(StringKey.GeneralUnits) ?: "new"
        if (previousUnits != newUnits) {
            overviewData.reset()
            rxBus.send(EventNewHistoryData(0L, reloadBgData = true))
        }
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

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "overview_buttons_settings" && requiredKey != "default_temp_targets_settings" && requiredKey != "prime_fill_settings" && requiredKey != "range_settings" && requiredKey != "statuslights_overview_advanced" && requiredKey != "overview_advanced_settings") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "overview_settings"
            title = rh.gs(R.string.overview)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewKeepScreenOn, summary = R.string.keep_screen_on_summary, title = R.string.keep_screen_on_title))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "overview_buttons_settings"
                title = rh.gs(R.string.overview_buttons_selection)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowTreatmentButton, title = R.string.treatments))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowWizardButton, title = R.string.calculator_label))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowInsulinButton, title = app.aaps.core.ui.R.string.configbuilder_insulin))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.OverviewInsulinButtonIncrement1, dialogMessage = R.string.insulin_increment_button_message, title = R.string.firstinsulinincrement))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.OverviewInsulinButtonIncrement2, dialogMessage = R.string.insulin_increment_button_message, title = R.string.secondinsulinincrement))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.OverviewInsulinButtonIncrement3, dialogMessage = R.string.insulin_increment_button_message, title = R.string.thirdinsulinincrement))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowCarbsButton, title = app.aaps.core.ui.R.string.carbs))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewCarbsButtonIncrement1, dialogMessage = R.string.carb_increment_button_message, title = R.string.firstcarbsincrement))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewCarbsButtonIncrement2, dialogMessage = R.string.carb_increment_button_message, title = R.string.secondcarbsincrement))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewCarbsButtonIncrement3, dialogMessage = R.string.carb_increment_button_message, title = R.string.thirdcarbsincrement))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowCgmButton, summary = R.string.show_cgm_button_summary, title = R.string.cgm))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowCalibrationButton, summary = R.string.show_calibration_button_summary, title = app.aaps.core.ui.R.string.calibration))
            })
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context,
                    intentKey = IntentKey.OverviewQuickWizardSettings,
                    title = R.string.quickwizard_settings,
                    intent = Intent(context, uiInteraction.quickWizardListActivity)
                )
            )
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "default_temp_targets_settings"
                title = rh.gs(R.string.default_temptargets)
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewEatingSoonDuration, title = R.string.eatingsoon_duration))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewEatingSoonTarget, title = R.string.eatingsoon_target))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewActivityDuration, title = R.string.activity_duration))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewActivityTarget, title = R.string.activity_target))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewHypoDuration, title = R.string.hypo_duration))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewHypoTarget, title = R.string.hypo_target))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "prime_fill_settings"
                title = rh.gs(R.string.fill_bolus_title)
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ActionsFillButton1, title = R.string.button1))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ActionsFillButton2, title = R.string.button2))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ActionsFillButton3, title = R.string.button3))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "range_settings"
                summary = rh.gs(R.string.prefs_range_summary)
                title = rh.gs(R.string.prefs_range_title)
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewLowMark, title = R.string.low_mark))
                addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.OverviewHighMark, title = R.string.high_mark))
            })
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShortTabTitles, title = R.string.short_tabtitles))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowNotesInDialogs, title = R.string.overview_show_notes_field_in_dialogs_title))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "statuslights_overview_advanced"
                title = rh.gs(app.aaps.core.ui.R.string.statuslights)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewShowStatusLights, title = R.string.show_statuslights))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewCageWarning, title = R.string.statuslights_cage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewCageCritical, title = R.string.statuslights_cage_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewIageWarning, title = R.string.statuslights_iage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewIageCritical, title = R.string.statuslights_iage_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSageWarning, title = R.string.statuslights_sage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSageCritical, title = R.string.statuslights_sage_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSbatWarning, title = R.string.statuslights_sbat_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewSbatCritical, title = R.string.statuslights_sbat_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewResWarning, title = R.string.statuslights_res_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewResCritical, title = R.string.statuslights_res_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBattWarning, title = R.string.statuslights_bat_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBattCritical, title = R.string.statuslights_bat_critical))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBageWarning, title = R.string.statuslights_bage_warning))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBageCritical, title = R.string.statuslights_bage_critical))
                addPreference(
                    AdaptiveClickPreference(ctx = context, stringKey = StringKey.OverviewCopySettingsFromNs, title = R.string.statuslights_copy_ns,
                                            onPreferenceClickListener = {
                                                nsSettingStatus.copyStatusLightsNsSettings(context)
                                                true
                                            })
                )
            })
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewBolusPercentage, dialogMessage = R.string.deliverpartofboluswizard, title = app.aaps.core.ui.R.string.partialboluswizard))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.OverviewResetBolusPercentageTime, dialogMessage = R.string.deliver_part_of_boluswizard_reset_time, title = app.aaps.core.ui.R.string.partialboluswizard_reset_time))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewUseBolusAdvisor, summary = R.string.enable_bolus_advisor_summary, title = R.string.enable_bolus_advisor))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewUseBolusReminder, summary = R.string.enablebolusreminder_summary, title = R.string.enablebolusreminder))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "overview_advanced_settings"
                title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.OverviewUseSuperBolus, summary = R.string.enablesuperbolus_summary, title = R.string.enablesuperbolus))
            })
        }
    }

    private val dismissReceiver = DismissNotificationReceiver()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerLocalBroadcastReceiver() {
        val filter = IntentFilter().apply { addAction(DismissNotificationReceiver.ACTION) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            context.registerReceiver(dismissReceiver, filter)
    }

    private fun unregisterLocalBroadcastReceiver() {
        context.unregisterReceiver(dismissReceiver)
    }
}
