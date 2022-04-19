package info.nightscout.androidaps.plugins.general.overview

import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.extensions.*
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Overview
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventUpdateOverviewCalcProgress
import info.nightscout.androidaps.plugins.general.overview.events.EventUpdateOverviewNotification
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.Scale
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.ScaledDataPoint
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
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
    private val overviewMenus: OverviewMenus
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

    class DeviationDataPoint(x: Double, y: Double, var color: Int, scale: Scale) : ScaledDataPoint(x, y, scale)

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
                           overviewData.pumpStatus = it.getStatus(rh)
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
/*
    @Volatile
    var runningRefresh = false
    override fun refreshLoop(from: String) {
        if (runningRefresh) return
        runningRefresh = true
        overviewBus.send(EventUpdateOverviewNotification(from))
        loadIobCobResults(from)
        overviewBus.send(EventUpdateOverviewProfile(from))
        overviewBus.send(EventUpdateOverviewBg(from))
        overviewBus.send(EventUpdateOverviewTime(from))
        overviewBus.send(EventUpdateOverviewTemporaryBasal(from))
        overviewBus.send(EventUpdateOverviewExtendedBolus(from))
        overviewBus.send(EventUpdateOverviewTemporaryTarget(from))
        loadAsData(from)
        overviewData.preparePredictions(from)
        overviewData.prepareBasalData(from)
        overviewData.prepareTemporaryTargetData(from)
        overviewData.prepareTreatmentsData(from)
        overviewData.prepareIobAutosensData(from)
        overviewBus.send(EventUpdateOverviewGraph(from))
        overviewBus.send(EventUpdateOverviewIobCob(from))
        aapsLogger.debug(LTag.UI, "refreshLoop finished")
        runningRefresh = false
    }

    @Suppress("SameParameterValue")
    private fun loadAll(from: String) {
        loadBg(from)
        loadProfile(from)
        loadTemporaryTarget(from)
        loadIobCobResults(from)
        loadAsData(from)
        overviewData.prepareBasalData(from)
        overviewData.prepareTemporaryTargetData(from)
        overviewData.prepareTreatmentsData(from)
//        prepareIobAutosensData(from)
//        preparePredictions(from)
        overviewBus.send(EventUpdateOverviewGraph(from))
        aapsLogger.debug(LTag.UI, "loadAll finished")
    }
*/
}
