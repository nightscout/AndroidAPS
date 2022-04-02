package info.nightscout.androidaps.activities

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.jjoe64.graphview.GraphView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.databinding.ActivityHistorybrowseBinding
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.events.EventCustomCalculationFinished
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.general.overview.OverviewMenus
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventBucketedDataCreated
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.*
import javax.inject.Inject
import kotlin.math.min

class HistoryBrowseActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var sensitivityOref1Plugin: SensitivityOref1Plugin
    @Inject lateinit var sensitivityAAPSPlugin: SensitivityAAPSPlugin
    @Inject lateinit var sensitivityWeightedAveragePlugin: SensitivityWeightedAveragePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var loop: Loop
    @Inject lateinit var nsDeviceStatus: NSDeviceStatus
    @Inject lateinit var translator: Translator
    @Inject lateinit var context: Context
    @Inject lateinit var dataWorker: DataWorker

    private val disposable = CompositeDisposable()

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

    private var axisWidth: Int = 0
    private var rangeToDisplay = 24 // for graph
//    private var start: Long = 0

    private lateinit var iobCobCalculator: IobCobCalculatorPlugin
    private lateinit var overviewData: OverviewData

    private lateinit var binding: ActivityHistorybrowseBinding
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorybrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // We don't want to use injected singletons but own instance working on top of different data
        iobCobCalculator =
            IobCobCalculatorPlugin(
                injector,
                aapsLogger,
                aapsSchedulers,
                rxBus,
                sp,
                rh,
                profileFunction,
                activePlugin,
                sensitivityOref1Plugin,
                sensitivityAAPSPlugin,
                sensitivityWeightedAveragePlugin,
                fabricPrivacy,
                dateUtil,
                repository,
                context,
                dataWorker
            )
        overviewData =
            OverviewData(
                injector,
                aapsLogger,
                rh,
                dateUtil,
                sp,
                activePlugin,
                defaultValueHelper,
                profileFunction,
                config,
                loop,
                nsDeviceStatus,
                repository,
                overviewMenus,
                iobCobCalculator,
                translator
            )

        binding.left.setOnClickListener {
            adjustTimeRange(overviewData.fromTime - T.hours(rangeToDisplay.toLong()).msecs())
            loadAll("onClickLeft")
        }
        binding.right.setOnClickListener {
            adjustTimeRange(overviewData.fromTime + T.hours(rangeToDisplay.toLong()).msecs())
            loadAll("onClickRight")
        }
        binding.end.setOnClickListener {
            setTime(dateUtil.now())
            loadAll("onClickEnd")
        }
        binding.zoom.setOnClickListener {
            rangeToDisplay += 6
            rangeToDisplay = if (rangeToDisplay > 24) 6 else rangeToDisplay
            setTime(overviewData.fromTime)
            loadAll("rangeChange")
        }
        binding.zoom.setOnLongClickListener {
            Calendar.getInstance().also { calendar ->
                calendar.timeInMillis = overviewData.fromTime
                calendar[Calendar.MILLISECOND] = 0
                calendar[Calendar.SECOND] = 0
                calendar[Calendar.MINUTE] = 0
                calendar[Calendar.HOUR_OF_DAY] = 0
                setTime(calendar.timeInMillis)
            }
            loadAll("onLongClickZoom")
            true
        }

        // create an OnDateSetListener
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            Calendar.getInstance().also { calendar ->
                calendar.timeInMillis = overviewData.fromTime
                calendar[Calendar.YEAR] = year
                calendar[Calendar.MONTH] = monthOfYear
                calendar[Calendar.DAY_OF_MONTH] = dayOfMonth
                calendar[Calendar.MILLISECOND] = 0
                calendar[Calendar.SECOND] = 0
                calendar[Calendar.MINUTE] = 0
                calendar[Calendar.HOUR_OF_DAY] = 0
                setTime(calendar.timeInMillis)
                binding.date.text = dateUtil.dateAndTimeString(overviewData.fromTime)
            }
            loadAll("onClickDate")
        }

        binding.date.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = overviewData.fromTime
            DatePickerDialog(
                this, R.style.MaterialPickerTheme,
                dateSetListener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
            display?.getRealMetrics(dm)
        else
            windowManager.defaultDisplay.getMetrics(dm)


        axisWidth = if (dm.densityDpi <= 120) 3 else if (dm.densityDpi <= 160) 10 else if (dm.densityDpi <= 320) 35 else if (dm.densityDpi <= 420) 50 else if (dm.densityDpi <= 560) 70 else 80
        binding.bgGraph.gridLabelRenderer?.gridColor = rh.gac(this,  R.attr.graphgrid)
        binding.bgGraph.gridLabelRenderer?.reloadStyles()
        binding.bgGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth

        overviewMenus.setupChartMenu(context, binding.chartMenuButton)
        prepareGraphsIfNeeded(overviewMenus.setting.size)
        savedInstanceState?.let { bundle ->
            rangeToDisplay = bundle.getInt("rangeToDisplay", 0)
            overviewData.fromTime = bundle.getLong("start", 0)
            overviewData.toTime = bundle.getLong("end", 0)
        }
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        iobCobCalculator.stopCalculation("onPause")
    }

    @Synchronized
    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           // catch only events from iobCobCalculator
                           if (it.cause is EventCustomCalculationFinished)
                               refreshLoop("EventAutosensCalculationFinished")
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventIobCalculationProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.cause is EventCustomCalculationFinished)
                               binding.overviewIobcalculationprogess.text = it.progressPct.toString() + "%"
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI("EventRefreshOverview") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           overviewData.prepareBucketedData("EventBucketedDataCreated")
                           overviewData.prepareBgData("EventBucketedDataCreated")
                           rxBus.send(EventRefreshOverview("EventBucketedDataCreated"))
                       }, fabricPrivacy::logException)

        if (overviewData.fromTime == 0L) {
            // set start of current day
            setTime(dateUtil.now())
            loadAll("onResume")
        } else {
            updateGUI("onResume")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("rangeToDisplay", rangeToDisplay)
        outState.putLong("start", overviewData.fromTime)
        outState.putLong("end", overviewData.toTime)
    }

    private fun prepareGraphsIfNeeded(numOfGraphs: Int) {
        if (numOfGraphs != secondaryGraphs.size - 1) {
            //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
            // rebuild needed
            secondaryGraphs.clear()
            secondaryGraphsLabel.clear()
            binding.iobGraph.removeAllViews()
            for (i in 1 until numOfGraphs) {
                val relativeLayout = RelativeLayout(this)
                relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                val graph = GraphView(this)
                graph.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rh.dpToPx(100)).also { it.setMargins(0, rh.dpToPx(15), 0, rh.dpToPx(10)) }
                graph.gridLabelRenderer?.gridColor = rh.gac( R.attr.graphgrid)
                graph.gridLabelRenderer?.reloadStyles()
                graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                graph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                graph.gridLabelRenderer?.numVerticalLabels = 3
                graph.viewport.backgroundColor =rh.gac(this , R.attr.viewPortbackgroundColor)
                relativeLayout.addView(graph)

                val label = TextView(this)
                val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(rh.dpToPx(30), rh.dpToPx(25), 0, 0) }
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                label.layoutParams = layoutParams
                relativeLayout.addView(label)
                secondaryGraphsLabel.add(label)

                binding.iobGraph.addView(relativeLayout)
                secondaryGraphs.add(graph)
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun loadAll(from: String) {
        updateDate()
        Thread {
            overviewData.prepareBgData(from)
            overviewData.prepareTreatmentsData(from)
            rxBus.send(EventRefreshOverview("loadAll_$from"))
            overviewData.prepareTemporaryTargetData(from)
            rxBus.send(EventRefreshOverview("loadAll_$from"))
            overviewData.prepareBasalData(from)
            rxBus.send(EventRefreshOverview(from))
            aapsLogger.debug(LTag.UI, "loadAll $from finished")
            runCalculation(from)
        }.start()
    }

    private fun setTime(start: Long) {
        GregorianCalendar().also { calendar ->
            calendar.timeInMillis = start
            calendar[Calendar.MILLISECOND] = 0
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MINUTE] = 0
            calendar[Calendar.HOUR_OF_DAY] -= (calendar[Calendar.HOUR_OF_DAY] % rangeToDisplay)
            adjustTimeRange(calendar.timeInMillis)
        }
    }

    private fun adjustTimeRange(start: Long) {
        overviewData.fromTime = start
        overviewData.toTime = overviewData.fromTime + T.hours(rangeToDisplay.toLong()).msecs()
        overviewData.endTime = overviewData.toTime
    }

    private fun runCalculation(from: String) {
        iobCobCalculator.runCalculation(from, overviewData.toTime, bgDataReload = true, limitDataToOldestAvailable = false, cause = EventCustomCalculationFinished())
    }

    @Volatile
    var runningRefresh = false
    @Suppress("SameParameterValue")
    private fun refreshLoop(from: String) {
        if (runningRefresh) return
        runningRefresh = true
        overviewData.prepareIobAutosensData(from)
        rxBus.send(EventRefreshOverview(from))
        aapsLogger.debug(LTag.UI, "refreshLoop finished")
        runningRefresh = false
    }

    fun updateDate() {
        binding.date.text = dateUtil.dateAndTimeString(overviewData.fromTime)
        binding.zoom.text = rangeToDisplay.toString()
    }

    @Suppress("UNUSED_PARAMETER")
    @SuppressLint("SetTextI18n")
    fun updateGUI(from: String) {
        aapsLogger.debug(LTag.UI, "updateGui $from")

        updateDate()

        val pump = activePlugin.activePump
        val graphData = GraphData(injector, binding.bgGraph, overviewData)
        val menuChartSettings = overviewMenus.setting
        graphData.addInRangeArea(overviewData.fromTime, overviewData.endTime, defaultValueHelper.determineLowLine(), defaultValueHelper.determineHighLine())
        graphData.addBgReadings(menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal])
        if (buildHelper.isDev()) graphData.addBucketedData()
        graphData.addTreatments()
        if (menuChartSettings[0][OverviewMenus.CharType.ACT.ordinal])
            graphData.addActivity(0.8)
        if (pump.pumpDescription.isTempBasalCapable && menuChartSettings[0][OverviewMenus.CharType.BAS.ordinal])
            graphData.addBasals()
        graphData.addTargetLine()
        graphData.addNowLine(dateUtil.now())

        // set manual x bounds to have nice steps
        graphData.setNumVerticalLabels()
        graphData.formatAxis(overviewData.fromTime, overviewData.endTime)

        graphData.performUpdate()

        // 2nd graphs
        prepareGraphsIfNeeded(menuChartSettings.size)
        val secondaryGraphsData: ArrayList<GraphData> = ArrayList()

        val now = System.currentTimeMillis()
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
            val secondGraphData = GraphData(injector, secondaryGraphs[g], overviewData)
            var useABSForScale = false
            var useIobForScale = false
            var useCobForScale = false
            var useDevForScale = false
            var useRatioForScale = false
            var useDSForScale = false
            var useBGIForScale = false
            when {
                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal] -> useABSForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal] -> useIobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal] -> useCobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] -> useDevForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal] -> useBGIForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal] -> useRatioForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
            }
            val alignDevBgiScale = menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] && menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]

            if (menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(useABSForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(useIobForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(useCobForScale, if (useCobForScale) 1.0 else 0.5)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(useDevForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]) secondGraphData.addMinusBGI(useBGIForScale, if (alignDevBgiScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(useRatioForScale, if (useRatioForScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && buildHelper.isDev()) secondGraphData.addDeviationSlope(useDSForScale, 1.0)

            // set manual x bounds to have nice steps
            secondGraphData.formatAxis(overviewData.fromTime, overviewData.endTime)
            secondGraphData.addNowLine(now)
            secondaryGraphsData.add(secondGraphData)
        }
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
            secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)
            secondaryGraphs[g].visibility = (
                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal]
                ).toVisibility()
            secondaryGraphsData[g].performUpdate()
        }
    }
}