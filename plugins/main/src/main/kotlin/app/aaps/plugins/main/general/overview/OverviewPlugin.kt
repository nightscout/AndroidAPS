package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
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
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.plugins.main.R
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
        .description(R.string.description_overview),
    ownPreferences = listOf(OverviewStringKey::class.java),
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
            .put(StringNonKey.TempTargetPresets, preferences)
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
            .store(StringNonKey.TempTargetPresets, preferences)
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
