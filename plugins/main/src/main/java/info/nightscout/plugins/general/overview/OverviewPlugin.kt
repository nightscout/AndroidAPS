package info.nightscout.plugins.general.overview

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.core.events.EventIobCalculationProgress
import info.nightscout.core.events.EventNewNotification
import info.nightscout.core.extensions.putDouble
import info.nightscout.core.extensions.putInt
import info.nightscout.core.extensions.putString
import info.nightscout.core.extensions.storeDouble
import info.nightscout.core.extensions.storeInt
import info.nightscout.core.extensions.storeString
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Overview
import info.nightscout.interfaces.overview.OverviewMenus
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.plugins.R
import info.nightscout.plugins.general.overview.notifications.NotificationStore
import info.nightscout.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.plugins.general.overview.notifications.events.EventUpdateOverviewNotification
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.events.EventUpdateOverviewCalcProgress
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
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
    private val context: Context
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewFragment::class.qualifiedName)
        .alwaysVisible(true)
        .alwaysEnabled(true)
        .pluginIcon(R.drawable.ic_home)
        .pluginName(R.string.overview)
        .shortName(R.string.overview_shortname)
        .preferencesId(R.xml.pref_overview)
        .description(R.string.description_overview),
    aapsLogger, rh, injector
), Overview {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override val overviewBus = RxBus(aapsSchedulers, aapsLogger)

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
                           overviewData.calcProgressPct = it.pass.finalPercent(it.progressPct)
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
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .putString(R.string.key_units, sp, rh)
            .putString(R.string.key_quickwizard, sp, rh)
            .putInt(R.string.key_eatingsoon_duration, sp, rh)
            .putDouble(R.string.key_eatingsoon_target, sp, rh)
            .putInt(R.string.key_activity_duration, sp, rh)
            .putDouble(R.string.key_activity_target, sp, rh)
            .putInt(R.string.key_hypo_duration, sp, rh)
            .putDouble(R.string.key_hypo_target, sp, rh)
            .putDouble(R.string.key_low_mark, sp, rh)
            .putDouble(R.string.key_high_mark, sp, rh)
            .putDouble(R.string.key_statuslights_cage_warning, sp, rh)
            .putDouble(R.string.key_statuslights_cage_critical, sp, rh)
            .putDouble(R.string.key_statuslights_iage_warning, sp, rh)
            .putDouble(R.string.key_statuslights_iage_critical, sp, rh)
            .putDouble(R.string.key_statuslights_sage_warning, sp, rh)
            .putDouble(R.string.key_statuslights_sage_critical, sp, rh)
            .putDouble(R.string.key_statuslights_sbat_warning, sp, rh)
            .putDouble(R.string.key_statuslights_sbat_critical, sp, rh)
            .putDouble(R.string.key_statuslights_bage_warning, sp, rh)
            .putDouble(R.string.key_statuslights_bage_critical, sp, rh)
            .putDouble(R.string.key_statuslights_res_warning, sp, rh)
            .putDouble(R.string.key_statuslights_res_critical, sp, rh)
            .putDouble(R.string.key_statuslights_bat_warning, sp, rh)
            .putDouble(R.string.key_statuslights_bat_critical, sp, rh)
            .putInt(R.string.key_boluswizard_percentage, sp, rh)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration
            .storeString(R.string.key_units, sp, rh)
            .storeString(R.string.key_quickwizard, sp, rh)
            .storeInt(R.string.key_eatingsoon_duration, sp, rh)
            .storeDouble(R.string.key_eatingsoon_target, sp, rh)
            .storeInt(R.string.key_activity_duration, sp, rh)
            .storeDouble(R.string.key_activity_target, sp, rh)
            .storeInt(R.string.key_hypo_duration, sp, rh)
            .storeDouble(R.string.key_hypo_target, sp, rh)
            .storeDouble(R.string.key_low_mark, sp, rh)
            .storeDouble(R.string.key_high_mark, sp, rh)
            .storeDouble(R.string.key_statuslights_cage_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_cage_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_iage_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_iage_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_sage_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_sage_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_sbat_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_sbat_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_bage_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_bage_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_res_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_res_critical, sp, rh)
            .storeDouble(R.string.key_statuslights_bat_warning, sp, rh)
            .storeDouble(R.string.key_statuslights_bat_critical, sp, rh)
            .storeInt(R.string.key_boluswizard_percentage, sp, rh)
    }
}
