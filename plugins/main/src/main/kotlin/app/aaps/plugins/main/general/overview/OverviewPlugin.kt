package app.aaps.plugins.main.general.overview

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.main.events.EventIobCalculationProgress
import app.aaps.core.main.events.EventNewNotification
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.utils.extensions.putDouble
import app.aaps.core.main.utils.extensions.putInt
import app.aaps.core.main.utils.extensions.putString
import app.aaps.core.main.utils.extensions.storeBoolean
import app.aaps.core.main.utils.extensions.storeDouble
import app.aaps.core.main.utils.extensions.storeInt
import app.aaps.core.main.utils.extensions.storeString
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.validators.ValidatingEditTextPreference
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.overview.notifications.NotificationWithAction
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import app.aaps.shared.impl.rx.bus.RxBusImpl
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val notificationStore: NotificationStore,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val sp: SP,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    rh: ResourceHelper,
    private val config: Config,
    private val overviewData: OverviewData,
    private val overviewMenus: OverviewMenus,
    private val context: Context,
    private val constraintsChecker: ConstraintsChecker
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewFragment::class.qualifiedName)
        .alwaysVisible(true)
        .alwaysEnabled(true)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_home)
        .pluginName(R.string.overview)
        .shortName(R.string.overview_shortname)
        .preferencesId(R.xml.pref_overview)
        .description(R.string.description_overview),
    aapsLogger, rh, injector
), Overview {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override val overviewBus = RxBusImpl(aapsSchedulers, aapsLogger)

    override fun addNotificationWithDialogResponse(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, title: String, message: String) {
        rxBus.send(
            EventNewNotification(
                NotificationWithAction(injector, id, text, level)
                    .also { n ->
                        n.action(actionButtonId) {
                            n.contextForAction?.let { OKDialog.show(it, title, message, null) }
                        }
                    })
        )
    }

    override fun addNotification(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, action: Runnable) {
        rxBus.send(
            EventNewNotification(
                NotificationWithAction(injector, id, text, level).apply {
                    action(actionButtonId, action)
                })
        )
    }

    override fun dismissNotification(id: Int) {
        rxBus.send(EventDismissNotification(id))
    }

    override fun onStart() {
        super.onStart()
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
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            (preferenceFragment.findPreference(rh.gs(R.string.key_show_cgm_button)) as SwitchPreference?)?.let {
                it.isVisible = false
                it.isEnabled = false
            }
            (preferenceFragment.findPreference(rh.gs(R.string.key_show_calibration_button)) as SwitchPreference?)?.let {
                it.isVisible = false
                it.isEnabled = false
            }
        }
        if (!config.isEngineeringMode())
            (preferenceFragment.findPreference(rh.gs(app.aaps.core.utils.R.string.key_reset_boluswizard_percentage_time)) as ValidatingEditTextPreference?)?.let {
                it.isVisible = false
                it.isEnabled = false
            }
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .putString(app.aaps.core.utils.R.string.key_units, sp, rh)
            .putString(app.aaps.core.utils.R.string.key_quickwizard, sp, rh)
            .putInt(app.aaps.core.utils.R.string.key_eatingsoon_duration, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_eatingsoon_target, sp, rh)
            .putInt(app.aaps.core.utils.R.string.key_activity_duration, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_activity_target, sp, rh)
            .putInt(app.aaps.core.utils.R.string.key_hypo_duration, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_hypo_target, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_low_mark, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_high_mark, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_cage_warning, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_cage_critical, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_iage_warning, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_iage_critical, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_sage_warning, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_sage_critical, sp, rh)
            .putDouble(R.string.key_statuslights_sbat_warning, sp, rh)
            .putDouble(R.string.key_statuslights_sbat_critical, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_bage_warning, sp, rh)
            .putDouble(app.aaps.core.utils.R.string.key_statuslights_bage_critical, sp, rh)
            .putDouble(R.string.key_statuslights_res_warning, sp, rh)
            .putDouble(R.string.key_statuslights_res_critical, sp, rh)
            .putDouble(R.string.key_statuslights_bat_warning, sp, rh)
            .putDouble(R.string.key_statuslights_bat_critical, sp, rh)
            .putInt(app.aaps.core.utils.R.string.key_boluswizard_percentage, sp, rh)
            .put(rh.gs(app.aaps.core.utils.R.string.key_used_autosens_on_main_phone), constraintsChecker.isAutosensModeEnabled().value()) // can be disabled by activated DynISF

    override fun applyConfiguration(configuration: JSONObject) {
        val previousUnits = sp.getString(app.aaps.core.utils.R.string.key_units, "random")
        configuration
            .storeString(app.aaps.core.utils.R.string.key_units, sp, rh)
            .storeString(app.aaps.core.utils.R.string.key_quickwizard, sp, rh)
            .storeInt(app.aaps.core.utils.R.string.key_eatingsoon_duration, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_eatingsoon_target, sp, rh)
            .storeInt(app.aaps.core.utils.R.string.key_activity_duration, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_activity_target, sp, rh)
            .storeInt(app.aaps.core.utils.R.string.key_hypo_duration, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_hypo_target, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_low_mark, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_high_mark, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_cage_warning, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_cage_critical, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_iage_warning, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_iage_critical, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_sage_warning, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_sage_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_sbat_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_sbat_critical, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_bage_warning, sp, rh)
            .storeDouble(app.aaps.core.utils.R.string.key_statuslights_bage_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_res_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_res_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_bat_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_bat_critical, sp, rh)
            .storeInt(app.aaps.core.utils.R.string.key_boluswizard_percentage, sp, rh)
            .storeBoolean(app.aaps.core.utils.R.string.key_used_autosens_on_main_phone, sp, rh)

        val newUnits = sp.getString(app.aaps.core.utils.R.string.key_units, "new")
        if (previousUnits != newUnits) {
            overviewData.reset()
            rxBus.send(EventNewHistoryData(0L, reloadBgData = true))
        }
    }
}
