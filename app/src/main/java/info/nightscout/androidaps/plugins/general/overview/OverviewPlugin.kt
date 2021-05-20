package info.nightscout.androidaps.plugins.general.overview

import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.extensions.*
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.events.EventLoopInvoked
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.SMBDefaults
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventUpdateOverview
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.*
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventBucketedDataCreated
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
    private val defaultValueHelper: DefaultValueHelper,
    private val loopPlugin: LoopPlugin,
    private val activePlugin: ActivePlugin,
    private val nsDeviceStatus: NSDeviceStatus,
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
                prepareTreatmentsData("EventTreatmentChange")
                overviewBus.send(EventUpdateOverview("EventTreatmentChange", OverviewData.Property.GRAPH))
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                prepareTreatmentsData("EventTherapyEventChange")
                overviewBus.send(EventUpdateOverview("EventTherapyEventChange", OverviewData.Property.GRAPH))
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                prepareBucketedData("EventBucketedDataCreated")
                prepareBgData("EventBucketedDataCreated")
                overviewBus.send(EventUpdateOverview("EventBucketedDataCreated", OverviewData.Property.GRAPH))
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventLoopInvoked::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ preparePredictions("EventLoopInvoked") }, fabricPrivacy::logException)
        disposable.add(rxBus
            .toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ loadProfile("EventProfileSwitchChanged") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ refreshLoop("EventAutosensCalculationFinished") }, fabricPrivacy::logException))

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

    @Volatile var runningRefresh = false
    override fun refreshLoop(from: String) {
        if (runningRefresh) return
        runningRefresh = true
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.BG))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TIME))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TEMPORARY_BASAL))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.EXTENDED_BOLUS))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.IOB_COB))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.TEMPORARY_TARGET))
        overviewBus.send(EventUpdateOverview(from, OverviewData.Property.SENSITIVITY))
        loadAsData(from)
        preparePredictions(from)
        prepareBasalData(from)
        prepareTemporaryTargetData(from)
        prepareTreatmentsData(from)
        prepareIobAutosensData(from)
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
        prepareBasalData(from)
        prepareTemporaryTargetData(from)
        prepareTreatmentsData(from)
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
        if (tempTarget is ValueWrapper.Existing) overviewData.temporarytarget = tempTarget.value
        else overviewData.temporarytarget = null
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

    @Synchronized
    @Suppress("SameParameterValue", "UNUSED_PARAMETER")
    private fun prepareBgData(from: String) {
//        val start = dateUtil.now()
        var maxBgValue = Double.MIN_VALUE
        overviewData.bgReadingsArray = repository.compatGetBgReadingsDataFromTime(overviewData.fromTime, overviewData.toTime, false).blockingGet()
        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (bg in overviewData.bgReadingsArray) {
            if (bg.timestamp < overviewData.fromTime || bg.timestamp > overviewData.toTime) continue
            if (bg.value > maxBgValue) maxBgValue = bg.value
            bgListArray.add(GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, resourceHelper))
        }
        overviewData.bgReadingGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
        overviewData.maxBgValue = Profile.fromMgdlToUnits(maxBgValue, profileFunction.getUnits())
        if (defaultValueHelper.determineHighLine() > maxBgValue) overviewData.maxBgValue = defaultValueHelper.determineHighLine()
        overviewData.maxBgValue = addUpperChartMargin(overviewData.maxBgValue)
//        profiler.log(LTag.UI, "prepareBgData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    private fun preparePredictions(from: String) {
//        val start = dateUtil.now()
        val apsResult = if (config.APS) loopPlugin.lastRun?.constraintsProcessed else nsDeviceStatus.getAPSResult(injector)
        val predictionsAvailable = if (config.APS) loopPlugin.lastRun?.request?.hasPredictions == true else config.NSCLIENT
        val menuChartSettings = overviewMenus.setting
        // align to hours
        val calendar = Calendar.getInstance().also {
            it.timeInMillis = System.currentTimeMillis()
            it[Calendar.MILLISECOND] = 0
            it[Calendar.SECOND] = 0
            it[Calendar.MINUTE] = 0
            it.add(Calendar.HOUR, 1)
        }
        if (predictionsAvailable && apsResult != null && menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal]) {
            var predictionHours = (ceil(apsResult.latestPredictionsTime - System.currentTimeMillis().toDouble()) / (60 * 60 * 1000)).toInt()
            predictionHours = min(2, predictionHours)
            predictionHours = max(0, predictionHours)
            val hoursToFetch = overviewData.rangeToDisplay - predictionHours
            overviewData.toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
            overviewData.fromTime = overviewData.toTime - T.hours(hoursToFetch.toLong()).msecs()
            overviewData.endTime = overviewData.toTime + T.hours(predictionHours.toLong()).msecs()
        } else {
            overviewData.toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
            overviewData.fromTime = overviewData.toTime - T.hours(overviewData.rangeToDisplay.toLong()).msecs()
            overviewData.endTime = overviewData.toTime
        }

        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        val predictions: MutableList<GlucoseValueDataPoint>? = apsResult?.predictions
            ?.map { bg -> GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, resourceHelper) }
            ?.toMutableList()
        if (predictions != null) {
            predictions.sortWith { o1: GlucoseValueDataPoint, o2: GlucoseValueDataPoint -> o1.x.compareTo(o2.x) }
            for (prediction in predictions) if (prediction.data.value >= 40) bgListArray.add(prediction)
        }
        overviewData.predictionsGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })
//        profiler.log(LTag.UI, "preparePredictions() $from", start)
    }

    @Synchronized
    @Suppress("SameParameterValue", "UNUSED_PARAMETER")
    private fun prepareBucketedData(from: String) {
//        val start = dateUtil.now()
        val bucketedData = iobCobCalculator.ads.getBucketedDataTableCopy() ?: return
        if (bucketedData.isEmpty()) {
            aapsLogger.debug("No bucketed data.")
            return
        }
        val bucketedListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        for (inMemoryGlucoseValue in bucketedData) {
            if (inMemoryGlucoseValue.timestamp < overviewData.fromTime || inMemoryGlucoseValue.timestamp > overviewData.toTime) continue
            bucketedListArray.add(InMemoryGlucoseValueDataPoint(inMemoryGlucoseValue, profileFunction, resourceHelper))
        }
        overviewData.bucketedGraphSeries = PointsWithLabelGraphSeries(Array(bucketedListArray.size) { i -> bucketedListArray[i] })
//        profiler.log(LTag.UI, "prepareBucketedData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    private fun prepareBasalData(from: String) {
//        val start = dateUtil.now()
        overviewData.maxBasalValueFound = 0.0
        val baseBasalArray: MutableList<ScaledDataPoint> = ArrayList()
        val tempBasalArray: MutableList<ScaledDataPoint> = ArrayList()
        val basalLineArray: MutableList<ScaledDataPoint> = ArrayList()
        val absoluteBasalLineArray: MutableList<ScaledDataPoint> = ArrayList()
        var lastLineBasal = 0.0
        var lastAbsoluteLineBasal = -1.0
        var lastBaseBasal = 0.0
        var lastTempBasal = 0.0
        var time = overviewData.fromTime
        while (time < overviewData.toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 60 * 1000L
                continue
            }
            val basalData = iobCobCalculator.getBasalData(profile, time)
            val baseBasalValue = basalData.basal
            var absoluteLineValue = baseBasalValue
            var tempBasalValue = 0.0
            var basal = 0.0
            if (basalData.isTempBasalRunning) {
                tempBasalValue = basalData.tempBasalAbsolute
                absoluteLineValue = tempBasalValue
                if (tempBasalValue != lastTempBasal) {
                    tempBasalArray.add(ScaledDataPoint(time, lastTempBasal, overviewData.basalScale))
                    tempBasalArray.add(ScaledDataPoint(time, tempBasalValue.also { basal = it }, overviewData.basalScale))
                }
                if (lastBaseBasal != 0.0) {
                    baseBasalArray.add(ScaledDataPoint(time, lastBaseBasal, overviewData.basalScale))
                    baseBasalArray.add(ScaledDataPoint(time, 0.0, overviewData.basalScale))
                    lastBaseBasal = 0.0
                }
            } else {
                if (baseBasalValue != lastBaseBasal) {
                    baseBasalArray.add(ScaledDataPoint(time, lastBaseBasal, overviewData.basalScale))
                    baseBasalArray.add(ScaledDataPoint(time, baseBasalValue.also { basal = it }, overviewData.basalScale))
                    lastBaseBasal = baseBasalValue
                }
                if (lastTempBasal != 0.0) {
                    tempBasalArray.add(ScaledDataPoint(time, lastTempBasal, overviewData.basalScale))
                    tempBasalArray.add(ScaledDataPoint(time, 0.0, overviewData.basalScale))
                }
            }
            if (baseBasalValue != lastLineBasal) {
                basalLineArray.add(ScaledDataPoint(time, lastLineBasal, overviewData.basalScale))
                basalLineArray.add(ScaledDataPoint(time, baseBasalValue, overviewData.basalScale))
            }
            if (absoluteLineValue != lastAbsoluteLineBasal) {
                absoluteBasalLineArray.add(ScaledDataPoint(time, lastAbsoluteLineBasal, overviewData.basalScale))
                absoluteBasalLineArray.add(ScaledDataPoint(time, basal, overviewData.basalScale))
            }
            lastAbsoluteLineBasal = absoluteLineValue
            lastLineBasal = baseBasalValue
            lastTempBasal = tempBasalValue
            overviewData.maxBasalValueFound = max(overviewData.maxBasalValueFound, max(tempBasalValue, baseBasalValue))
            time += 60 * 1000L
        }

        // final points
        basalLineArray.add(ScaledDataPoint(overviewData.toTime, lastLineBasal, overviewData.basalScale))
        baseBasalArray.add(ScaledDataPoint(overviewData.toTime, lastBaseBasal, overviewData.basalScale))
        tempBasalArray.add(ScaledDataPoint(overviewData.toTime, lastTempBasal, overviewData.basalScale))
        absoluteBasalLineArray.add(ScaledDataPoint(overviewData.toTime, lastAbsoluteLineBasal, overviewData.basalScale))

        // create series
        overviewData.baseBasalGraphSeries = LineGraphSeries(Array(baseBasalArray.size) { i -> baseBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = resourceHelper.getAttributeColor(null, R.attr.basebasal)
            it.thickness = 0
        }
        overviewData.tempBasalGraphSeries = LineGraphSeries(Array(tempBasalArray.size) { i -> tempBasalArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = resourceHelper.getAttributeColor( null, R.attr.basal)
            it.thickness = 0
        }
        overviewData.basalLineGraphSeries = LineGraphSeries(Array(basalLineArray.size) { i -> basalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = resourceHelper.getDisplayMetrics().scaledDensity * 2
                paint.pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
                paint.color = resourceHelper.getAttributeColor(null, R.attr.basal)
            })
        }
        overviewData.absoluteBasalGraphSeries = LineGraphSeries(Array(absoluteBasalLineArray.size) { i -> absoluteBasalLineArray[i] }).also {
            it.setCustomPaint(Paint().also { absolutePaint ->
                absolutePaint.style = Paint.Style.STROKE
                absolutePaint.strokeWidth = resourceHelper.getDisplayMetrics().scaledDensity * 2
                absolutePaint.color = resourceHelper.getAttributeColor(null, R.attr.basal)
            })
        }
//        profiler.log(LTag.UI, "prepareBasalData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    private fun prepareTemporaryTargetData(from: String) {
//        val start = dateUtil.now()
        val profile = overviewData.profile ?: return
        val units = profileFunction.getUnits()
        var toTime = overviewData.toTime
        val targetsSeriesArray: MutableList<DataPoint> = ArrayList()
        var lastTarget = -1.0
        loopPlugin.lastRun?.constraintsProcessed?.let { toTime = max(it.latestPredictionsTime, toTime) }
        var time = overviewData.fromTime
        while (time < toTime) {
            val tt = repository.getTemporaryTargetActiveAt(time).blockingGet()
            val value: Double = if (tt is ValueWrapper.Existing) {
                Profile.fromMgdlToUnits(tt.value.target(), units)
            } else {
                Profile.fromMgdlToUnits((profile.getTargetLowMgdl(time) + profile.getTargetHighMgdl(time)) / 2, units)
            }
            if (lastTarget != value) {
                if (lastTarget != -1.0) targetsSeriesArray.add(DataPoint(time.toDouble(), lastTarget))
                targetsSeriesArray.add(DataPoint(time.toDouble(), value))
            }
            lastTarget = value
            time += 5 * 60 * 1000L
        }
        // final point
        targetsSeriesArray.add(DataPoint(toTime.toDouble(), lastTarget))
        // create series
        overviewData.temporaryTargetSeries = LineGraphSeries(Array(targetsSeriesArray.size) { i -> targetsSeriesArray[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.getAttributeColor(null, R.attr.tempTargetBackground)
            it.thickness = 2
        }
//        profiler.log(LTag.UI, "prepareTemporaryTargetData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    private fun prepareTreatmentsData(from: String) {
        overviewData.maxTreatmentsValue = 0.0
        val filteredTreatments: MutableList<DataPointWithLabelInterface> = ArrayList()
        repository.getBolusesIncludingInvalidFromTimeToTime(overviewData.fromTime, overviewData.endTime, true).blockingGet()
            .map { BolusDataPoint(it, resourceHelper, activePlugin, defaultValueHelper) }
            .filter { it.data.type != Bolus.Type.SMB || it.data.isValid }
            .forEach {
                it.y = getNearestBg(it.x.toLong())
                filteredTreatments.add(it)
            }
        repository.getCarbsIncludingInvalidFromTimeToTimeExpanded(overviewData.fromTime, overviewData.endTime, true).blockingGet()
            .map { CarbsDataPoint(it, resourceHelper) }
            .forEach {
                it.y = getNearestBg(it.x.toLong())
                filteredTreatments.add(it)
            }

        // ProfileSwitch
        repository.getEffectiveProfileSwitchDataFromTimeToTime(overviewData.fromTime, overviewData.endTime, true).blockingGet()
            .map { EffectiveProfileSwitchDataPoint(it, resourceHelper) }
            .forEach(filteredTreatments::add)

        // Extended bolus
        if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            repository.getExtendedBolusDataFromTimeToTime(overviewData.fromTime, overviewData.endTime, true).blockingGet()
                .map { ExtendedBolusDataPoint(it, resourceHelper) }
                .filter { it.duration != 0L }
                .forEach {
                    it.y = getNearestBg(it.x.toLong())
                    filteredTreatments.add(it)
                }
        }

        // Careportal
        repository.compatGetTherapyEventDataFromToTime(overviewData.fromTime - T.hours(6).msecs(), overviewData.endTime).blockingGet()
            .map { TherapyEventDataPoint(it, resourceHelper, profileFunction, translator) }
            .filterTimeframe(overviewData.fromTime, overviewData.endTime)
            .forEach {
                if (it.y == 0.0) it.y = getNearestBg(it.x.toLong())
                filteredTreatments.add(it)
            }

        // increase maxY if a treatment forces it's own height that's higher than a BG value
        filteredTreatments.map { it.y }
            .maxOrNull()
            ?.let(::addUpperChartMargin)
            ?.let { overviewData.maxTreatmentsValue = maxOf(overviewData.maxTreatmentsValue, it) }

        overviewData.treatmentsSeries = PointsWithLabelGraphSeries(filteredTreatments.toTypedArray())
//        profiler.log(LTag.UI, "prepareTreatmentsData() $from", start)
    }

    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    private fun prepareIobAutosensData(from: String) {
//        val start = dateUtil.now()
        val iobArray: MutableList<ScaledDataPoint> = ArrayList()
        val absIobArray: MutableList<ScaledDataPoint> = ArrayList()
        overviewData.maxIobValueFound = Double.MIN_VALUE
        var lastIob = 0.0
        var absLastIob = 0.0
        var time = overviewData.fromTime

        val minFailOverActiveList: MutableList<DataPointWithLabelInterface> = ArrayList()
        val cobArray: MutableList<ScaledDataPoint> = ArrayList()
        overviewData.maxCobValueFound = Double.MIN_VALUE
        var lastCob = 0

        val actArrayHist: MutableList<ScaledDataPoint> = ArrayList()
        val actArrayPrediction: MutableList<ScaledDataPoint> = ArrayList()
        val now = dateUtil.now().toDouble()
        overviewData.maxIAValue = 0.0

        val bgiArrayHist: MutableList<ScaledDataPoint> = ArrayList()
        val bgiArrayPrediction: MutableList<ScaledDataPoint> = ArrayList()
        overviewData.maxBGIValue = Double.MIN_VALUE

        val devArray: MutableList<DeviationDataPoint> = ArrayList()
        overviewData.maxDevValueFound = Double.MIN_VALUE

        val ratioArray: MutableList<ScaledDataPoint> = ArrayList()
        overviewData.maxRatioValueFound = 5.0                    //even if sens data equals 0 for all the period, minimum scale is between 95% and 105%
        overviewData.minRatioValueFound = -5.0

        val dsMaxArray: MutableList<ScaledDataPoint> = ArrayList()
        val dsMinArray: MutableList<ScaledDataPoint> = ArrayList()
        overviewData.maxFromMaxValueFound = Double.MIN_VALUE
        overviewData.maxFromMinValueFound = Double.MIN_VALUE

        val adsData = iobCobCalculator.ads.clone()

        while (time <= overviewData.toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 5 * 60 * 1000L
                continue
            }
            // IOB
            val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(time, profile)
            val baseBasalIob = iobCobCalculator.calculateAbsoluteIobFromBaseBasals(time)
            val absIob = IobTotal.combine(iob, baseBasalIob)
            val autosensData = adsData.getAutosensDataAtTime(time)
            if (abs(lastIob - iob.iob) > 0.02) {
                if (abs(lastIob - iob.iob) > 0.2) iobArray.add(ScaledDataPoint(time, lastIob, overviewData.iobScale))
                iobArray.add(ScaledDataPoint(time, iob.iob, overviewData.iobScale))
                overviewData.maxIobValueFound = maxOf(overviewData.maxIobValueFound, abs(iob.iob))
                lastIob = iob.iob
            }
            if (abs(absLastIob - absIob.iob) > 0.02) {
                if (abs(absLastIob - absIob.iob) > 0.2) absIobArray.add(ScaledDataPoint(time, absLastIob, overviewData.iobScale))
                absIobArray.add(ScaledDataPoint(time, absIob.iob, overviewData.iobScale))
                overviewData.maxIobValueFound = maxOf(overviewData.maxIobValueFound, abs(absIob.iob))
                absLastIob = absIob.iob
            }

            // COB
            if (autosensData != null) {
                val cob = autosensData.cob.toInt()
                if (cob != lastCob) {
                    if (autosensData.carbsFromBolus > 0) cobArray.add(ScaledDataPoint(time, lastCob.toDouble(), overviewData.cobScale))
                    cobArray.add(ScaledDataPoint(time, cob.toDouble(), overviewData.cobScale))
                    overviewData.maxCobValueFound = max(overviewData.maxCobValueFound, cob.toDouble())
                    lastCob = cob
                }
                if (autosensData.failoverToMinAbsorbtionRate) {
                    autosensData.setScale(overviewData.cobScale)
                    autosensData.setChartTime(time)
                    minFailOverActiveList.add(autosensData)
                }
            }

            // ACTIVITY
            if (time <= now) actArrayHist.add(ScaledDataPoint(time, iob.activity, overviewData.actScale))
            else actArrayPrediction.add(ScaledDataPoint(time, iob.activity, overviewData.actScale))
            overviewData.maxIAValue = max(overviewData.maxIAValue, abs(iob.activity))

            // BGI
            val devBgiScale = overviewMenus.isEnabledIn(OverviewMenus.CharType.DEV) == overviewMenus.isEnabledIn(OverviewMenus.CharType.BGI)
            val deviation = if (devBgiScale) autosensData?.deviation ?: 0.0 else 0.0
            val bgi: Double = iob.activity * profile.getIsfMgdl(time) * 5.0
            if (time <= now) bgiArrayHist.add(ScaledDataPoint(time, bgi, overviewData.bgiScale))
            else bgiArrayPrediction.add(ScaledDataPoint(time, bgi, overviewData.bgiScale))
            overviewData.maxBGIValue = max(overviewData.maxBGIValue, max(abs(bgi), deviation))

            // DEVIATIONS
            if (autosensData != null) {
                var color = resourceHelper.getAttributeColor(null, R.attr.deviationCsf) // "="
                if (autosensData.type == "" || autosensData.type == "non-meal") {
                    if (autosensData.pastSensitivity == "C") color = resourceHelper.getAttributeColor(null, R.attr.deviationCsf)
                    if (autosensData.pastSensitivity == "+") color = resourceHelper.getAttributeColor(null, R.attr.deviationPlus)
                    if (autosensData.pastSensitivity == "-") color =resourceHelper.getAttributeColor(null, R.attr.deviationMinus)
                } else if (autosensData.type == "uam") {
                    color = resourceHelper.getAttributeColor(null, R.attr.uamColor)
                } else if (autosensData.type == "csf") {
                    color = resourceHelper.getAttributeColor(null, R.attr.deviationCsf)
                }
                devArray.add(DeviationDataPoint(time.toDouble(), autosensData.deviation, color, overviewData.devScale))
                overviewData.maxDevValueFound = maxOf(overviewData.maxDevValueFound, abs(autosensData.deviation), abs(bgi))
            }

            // RATIO
            if (autosensData != null) {
                ratioArray.add(ScaledDataPoint(time, 100.0 * (autosensData.autosensResult.ratio - 1), overviewData.ratioScale))
                overviewData.maxRatioValueFound = max(overviewData.maxRatioValueFound, 100.0 * (autosensData.autosensResult.ratio - 1))
                overviewData.minRatioValueFound = min(overviewData.minRatioValueFound, 100.0 * (autosensData.autosensResult.ratio - 1))
            }

            // DEV SLOPE
            if (autosensData != null) {
                dsMaxArray.add(ScaledDataPoint(time, autosensData.slopeFromMaxDeviation, overviewData.dsMaxScale))
                dsMinArray.add(ScaledDataPoint(time, autosensData.slopeFromMinDeviation, overviewData.dsMinScale))
                overviewData.maxFromMaxValueFound = max(overviewData.maxFromMaxValueFound, abs(autosensData.slopeFromMaxDeviation))
                overviewData.maxFromMinValueFound = max(overviewData.maxFromMinValueFound, abs(autosensData.slopeFromMinDeviation))
            }

            time += 5 * 60 * 1000L
        }
        // IOB
        overviewData.iobSeries = FixedLineGraphSeries(Array(iobArray.size) { i -> iobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.getAttributeColor(null, R.attr.iobColor) //50%
            it.color = resourceHelper.getAttributeColor(null, R.attr.iobColor)
            it.thickness = 3
        }
        overviewData.absIobSeries = FixedLineGraphSeries(Array(absIobArray.size) { i -> absIobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.getAttributeColor(null, R.attr.iobColor) //50%
            it.color = resourceHelper.getAttributeColor(null, R.attr.iobColor)
            it.thickness = 3
        }

        if (overviewMenus.setting[0][OverviewMenus.CharType.PRE.ordinal]) {
            val autosensData = adsData.getLastAutosensData("GraphData", aapsLogger, dateUtil)
            val lastAutosensResult = autosensData?.autosensResult ?: AutosensResult()
            val isTempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing
            val iobPrediction: MutableList<DataPointWithLabelInterface> = ArrayList()
            val iobPredictionArray = iobCobCalculator.calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
            for (i in iobPredictionArray) {
                iobPrediction.add(i.setColor(resourceHelper.getAttributeColor(null, R.attr.iobPredAS)))
                overviewData.maxIobValueFound = max(overviewData.maxIobValueFound, abs(i.iob))
            }
            overviewData.iobPredictions1Series = PointsWithLabelGraphSeries(Array(iobPrediction.size) { i -> iobPrediction[i] })
            val iobPrediction2: MutableList<DataPointWithLabelInterface> = ArrayList()
            val iobPredictionArray2 = iobCobCalculator.calculateIobArrayForSMB(AutosensResult(), SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
            for (i in iobPredictionArray2) {
                iobPrediction2.add(i.setColor(resourceHelper.getAttributeColor(null, R.attr.iobPred)))
                overviewData.maxIobValueFound = max(overviewData.maxIobValueFound, abs(i.iob))
            }
            overviewData.iobPredictions2Series = PointsWithLabelGraphSeries(Array(iobPrediction2.size) { i -> iobPrediction2[i] })
            aapsLogger.debug(LTag.AUTOSENS, "IOB prediction for AS=" + DecimalFormatter.to2Decimal(lastAutosensResult.ratio) + ": " + iobCobCalculator.iobArrayToString(iobPredictionArray))
            aapsLogger.debug(LTag.AUTOSENS, "IOB prediction for AS=" + DecimalFormatter.to2Decimal(1.0) + ": " + iobCobCalculator.iobArrayToString(iobPredictionArray2))
        } else {
            overviewData.iobPredictions1Series = PointsWithLabelGraphSeries()
            overviewData.iobPredictions2Series = PointsWithLabelGraphSeries()
        }

        // COB
        overviewData.cobSeries = FixedLineGraphSeries(Array(cobArray.size) { i -> cobArray[i] }).also {
            it.isDrawBackground = true
            it.backgroundColor = -0x7f000001 and resourceHelper.getAttributeColor(null, R.attr.cobColor) //50%
            it.color = resourceHelper.getAttributeColor(null, R.attr.cobColor)
            it.thickness = 3
        }
        overviewData.cobMinFailOverSeries = PointsWithLabelGraphSeries(Array(minFailOverActiveList.size) { i -> minFailOverActiveList[i] })

        // ACTIVITY
        overviewData.activitySeries = FixedLineGraphSeries(Array(actArrayHist.size) { i -> actArrayHist[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.getAttributeColor(null, R.attr.activity)
            it.thickness = 3
        }
        overviewData.activityPredictionSeries = FixedLineGraphSeries(Array(actArrayPrediction.size) { i -> actArrayPrediction[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                paint.color = resourceHelper.getAttributeColor(null, R.attr.activity)
            })
        }

        // BGI
        overviewData.minusBgiSeries = FixedLineGraphSeries(Array(bgiArrayHist.size) { i -> bgiArrayHist[i] }).also {
            it.isDrawBackground = false
            it.color = resourceHelper.getAttributeColor(null, R.attr.bgi)
            it.thickness = 3
        }
        overviewData.minusBgiHistSeries = FixedLineGraphSeries(Array(bgiArrayPrediction.size) { i -> bgiArrayPrediction[i] }).also {
            it.setCustomPaint(Paint().also { paint ->
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                paint.color = resourceHelper.getAttributeColor(null, R.attr.bgi)
            })
        }

        // DEVIATIONS
        overviewData.deviationsSeries = BarGraphSeries(Array(devArray.size) { i -> devArray[i] }).also {
            it.setValueDependentColor { data: DeviationDataPoint -> data.color }
        }

        // RATIO
        overviewData.ratioSeries = LineGraphSeries(Array(ratioArray.size) { i -> ratioArray[i] }).also {
            it.color = resourceHelper.getAttributeColor(null, R.attr.ratio)
            it.thickness = 3
        }

        // DEV SLOPE
        overviewData.dsMaxSeries = LineGraphSeries(Array(dsMaxArray.size) { i -> dsMaxArray[i] }).also {
            it.color = resourceHelper.getAttributeColor(null, R.attr.devslopepos)
            it.thickness = 3
        }
        overviewData.dsMinSeries = LineGraphSeries(Array(dsMinArray.size) { i -> dsMinArray[i] }).also {
            it.color = resourceHelper.getAttributeColor(null, R.attr.devslopeneg)
            it.thickness = 3
        }

//        profiler.log(LTag.UI, "prepareIobAutosensData() $from", start)
    }

    private fun addUpperChartMargin(maxBgValue: Double) =
        if (profileFunction.getUnits() == GlucoseUnit.MGDL) Round.roundTo(maxBgValue, 40.0) + 80 else Round.roundTo(maxBgValue, 2.0) + 4

    private fun getNearestBg(date: Long): Double {
        overviewData.bgReadingsArray.let { bgReadingsArray ->
            for (reading in bgReadingsArray) {
                if (reading.timestamp > date) continue
                return Profile.fromMgdlToUnits(reading.value, profileFunction.getUnits())
            }
            return if (bgReadingsArray.isNotEmpty()) Profile.fromMgdlToUnits(bgReadingsArray[0].value, profileFunction.getUnits())
            else Profile.fromMgdlToUnits(100.0, profileFunction.getUnits())
        }
    }

    private fun <E : DataPointWithLabelInterface> List<E>.filterTimeframe(fromTime: Long, endTime: Long): List<E> =
        filter { it.x + it.duration >= fromTime && it.x <= endTime }
}
