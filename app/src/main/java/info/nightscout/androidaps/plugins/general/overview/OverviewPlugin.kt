package info.nightscout.androidaps.plugins.general.overview

import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.extensions.*
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.events.EventLoopInvoked
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventUpdateOverview
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.Scale
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.ScaledDataPoint
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventBucketedDataCreated
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewPlugin @Inject constructor(
        injector: HasAndroidInjector,
        private val notificationStore: NotificationStore,
        private val fabricPrivacy: FabricPrivacy,
        private val rxBus: RxBusWrapper,
        private val sp: SP,
        aapsLogger: AAPSLogger,
        private val aapsSchedulers: AapsSchedulers,
        resourceHelper: ResourceHelper,
        private val config: Config,
        private val dateUtil: DateUtil,
        private val translator: Translator,
//    private val profiler: Profiler,
        private val profileFunction: ProfileFunction,
        private val iobCobCalculator: IobCobCalculator,
        private val repository: AppRepository,
        private val overviewData: OverviewData,
        private val overviewMenus: OverviewMenus
) : PluginBase(PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewFragment::class.qualifiedName)
        .alwaysVisible(true)
        .alwaysEnabled(true)
        .pluginIcon(R.drawable.ic_home)
        .pluginName(R.string.overview)
        .shortName(R.string.overview_shortname)
        .preferencesId(R.xml.pref_overview)
        .description(R.string.description_overview),
        aapsLogger, resourceHelper, injector
), Overview {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override val overviewBus = RxBusWrapper(aapsSchedulers)

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
                        rxBus.send(EventRefreshOverview("EventNewNotification"))
                }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventDismissNotification::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ n ->
                    if (notificationStore.remove(n.id))
                        rxBus.send(EventRefreshOverview("EventDismissNotification"))
                }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventIobCalculationProgress::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe({ overviewData.calcProgress = it.progress; overviewBus.send(EventUpdateOverview("EventIobCalculationProgress", OverviewData.Property.CALC_PROGRESS)) }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ loadTemporaryBasal("EventTempBasalChange") }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventExtendedBolusChange::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ loadExtendedBolus("EventExtendedBolusChange") }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventNewBG::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ loadBg("EventNewBG") }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventTempTargetChange::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ loadTemporaryTarget("EventTempTargetChange") }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventTreatmentChange::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({
                    loadIobCobResults("EventTreatmentChange")
                    overviewData.prepareTreatmentsData("EventTreatmentChange")
                    overviewBus.send(EventUpdateOverview("EventTreatmentChange", OverviewData.Property.GRAPH))
                }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventTherapyEventChange::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({
                    overviewData.prepareTreatmentsData("EventTherapyEventChange")
                    overviewBus.send(EventUpdateOverview("EventTherapyEventChange", OverviewData.Property.GRAPH))
                }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventBucketedDataCreated::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({
                    overviewData.prepareBucketedData("EventBucketedDataCreated")
                    overviewData.prepareBgData("EventBucketedDataCreated")
                    overviewBus.send(EventUpdateOverview("EventBucketedDataCreated", OverviewData.Property.GRAPH))
                }, fabricPrivacy::logException)
        disposable += rxBus
                .toObservable(EventLoopInvoked::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ overviewData.preparePredictions("EventLoopInvoked") }, fabricPrivacy::logException)
        disposable.add(rxBus
                .toObservable(EventNewBasalProfile::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ loadProfile("EventNewBasalProfile") }, fabricPrivacy::logException))
        disposable.add(rxBus
                .toObservable(EventAutosensCalculationFinished::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({
                    if (it.cause !is EventCustomCalculationFinished) refreshLoop("EventAutosensCalculationFinished")
                }, fabricPrivacy::logException))

        Thread { loadAll("onResume") }.start()
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            (preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_cgm_button)) as SwitchPreference?)?.let {
                it.isVisible = false
                it.isEnabled = false
            }
            (preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_calibration_button)) as SwitchPreference?)?.let {
                it.isVisible = false
                it.isEnabled = false
            }
        }
    }

    override fun configuration(): JSONObject =
            JSONObject()
                    .putString(R.string.key_quickwizard, sp, resourceHelper)
                    .putInt(R.string.key_eatingsoon_duration, sp, resourceHelper)
                    .putDouble(R.string.key_eatingsoon_target, sp, resourceHelper)
                    .putInt(R.string.key_activity_duration, sp, resourceHelper)
                    .putDouble(R.string.key_activity_target, sp, resourceHelper)
                    .putInt(R.string.key_hypo_duration, sp, resourceHelper)
                    .putDouble(R.string.key_hypo_target, sp, resourceHelper)
                    .putDouble(R.string.key_low_mark, sp, resourceHelper)
                    .putDouble(R.string.key_high_mark, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_cage_warning, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_cage_critical, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_iage_warning, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_iage_critical, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_sage_warning, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_sage_critical, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_sbat_warning, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_sbat_critical, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_bage_warning, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_bage_critical, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_res_warning, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_res_critical, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_bat_warning, sp, resourceHelper)
                    .putDouble(R.string.key_statuslights_bat_critical, sp, resourceHelper)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration
                .storeString(R.string.key_quickwizard, sp, resourceHelper)
                .storeInt(R.string.key_eatingsoon_duration, sp, resourceHelper)
                .storeDouble(R.string.key_eatingsoon_target, sp, resourceHelper)
                .storeInt(R.string.key_activity_duration, sp, resourceHelper)
                .storeDouble(R.string.key_activity_target, sp, resourceHelper)
                .storeInt(R.string.key_hypo_duration, sp, resourceHelper)
                .storeDouble(R.string.key_hypo_target, sp, resourceHelper)
                .storeDouble(R.string.key_low_mark, sp, resourceHelper)
                .storeDouble(R.string.key_high_mark, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_cage_warning, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_cage_critical, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_iage_warning, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_iage_critical, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_sage_warning, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_sage_critical, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_sbat_warning, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_sbat_critical, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_bage_warning, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_bage_critical, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_res_warning, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_res_critical, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_bat_warning, sp, resourceHelper)
                .storeDouble(R.string.key_statuslights_bat_critical, sp, resourceHelper)
    }

    @Volatile
    var runningRefresh = false
    override fun refreshLoop(from: String) {
        if (runningRefresh) return
        runningRefresh = true
        loadIobCobResults(from)
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.BG))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TIME))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TEMPORARY_BASAL))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.EXTENDED_BOLUS))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.IOB_COB))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TEMPORARY_TARGET))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.SENSITIVITY))
        loadAsData(from)
        overviewData.preparePredictions(from)
        overviewData.prepareBasalData(from)
        overviewData.prepareTemporaryTargetData(from)
        overviewData.prepareTreatmentsData(from)
        overviewData.prepareIobAutosensData(from)
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.GRAPH))
        aapsLogger.debug(LTag.UI, "refreshLoop finished")
        runningRefresh = false
    }

    @Suppress("SameParameterValue")
    private fun loadAll(from: String) {
        loadBg(from)
        loadProfile(from)
        loadTemporaryBasal(from)
        loadExtendedBolus(from)
        loadTemporaryTarget(from)
        loadIobCobResults(from)
        loadAsData(from)
        overviewData.prepareBasalData(from)
        overviewData.prepareTemporaryTargetData(from)
        overviewData.prepareTreatmentsData(from)
//        prepareIobAutosensData(from)
//        preparePredictions(from)
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.GRAPH))
        aapsLogger.debug(LTag.UI, "loadAll finished")
    }

    private fun loadProfile(from: String) {
        overviewData.profile = profileFunction.getProfile()
        overviewData.profileName = profileFunction.getProfileName()
        overviewData.profileNameWithRemainingTime = profileFunction.getProfileNameWithRemainingTime()
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.PROFILE))
    }

    private fun loadTemporaryBasal(from: String) {
        overviewData.temporaryBasal = iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now())
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TEMPORARY_BASAL))
    }

    private fun loadExtendedBolus(from: String) {
        overviewData.extendedBolus = iobCobCalculator.getExtendedBolus(dateUtil.now())
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.EXTENDED_BOLUS))
    }

    private fun loadTemporaryTarget(from: String) {
        val tempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        if (tempTarget is ValueWrapper.Existing) overviewData.temporaryTarget = tempTarget.value
        else overviewData.temporaryTarget = null
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TEMPORARY_TARGET))
    }

    private fun loadAsData(from: String) {
        overviewData.lastAutosensData = iobCobCalculator.ads.getLastAutosensData("Overview", aapsLogger, dateUtil)
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.SENSITIVITY))
    }

    private fun loadBg(from: String) {
        val gvWrapped = repository.getLastGlucoseValueWrapped().blockingGet()
        if (gvWrapped is ValueWrapper.Existing) overviewData.lastBg = gvWrapped.value
        else overviewData.lastBg = null
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.BG))
    }

    private fun loadIobCobResults(from: String) {
        overviewData.bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        overviewData.basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        overviewData.cobInfo = iobCobCalculator.getCobInfo(false, "Overview COB")
        val lastCarbs = repository.getLastCarbsRecordWrapped().blockingGet()
        overviewData.lastCarbsTime = if (lastCarbs is ValueWrapper.Existing) lastCarbs.value.timestamp else 0L

        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.IOB_COB))
    }

}
