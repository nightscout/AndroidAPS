package app.aaps.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import app.aaps.core.data.time.T
import app.aaps.core.graph.data.GraphViewWithCleanup
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventCustomCalculationFinished
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventScale
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewGraph
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.extensions.toVisibilityKeepSpace
import app.aaps.databinding.ActivityHistorybrowseBinding
import app.aaps.plugins.main.general.overview.graphData.GraphData
import com.google.android.material.datepicker.MaterialDatePicker
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.min

class HistoryBrowseActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var historyBrowserData: HistoryBrowserData
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var calculationWorkflow: CalculationWorkflow
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var graphDataProvider: Provider<GraphData>

    private val disposable = CompositeDisposable()

    private val secondaryGraphs = ArrayList<GraphViewWithCleanup>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

    private var axisWidth: Int = 0
    private var rangeToDisplay = 24 // for graph
//    private var start: Long = 0

    private lateinit var binding: ActivityHistorybrowseBinding
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorybrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(app.aaps.plugins.main.R.string.nav_history_browser)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.left.setOnClickListener {
            adjustTimeRange(historyBrowserData.overviewData.fromTime - T.hours(rangeToDisplay.toLong()).msecs())
            loadAll("onClickLeft")
        }
        binding.right.setOnClickListener {
            adjustTimeRange(historyBrowserData.overviewData.fromTime + T.hours(rangeToDisplay.toLong()).msecs())
            loadAll("onClickRight")
        }
        binding.end.setOnClickListener {
            setTime(dateUtil.now())
            loadAll("onClickEnd")
        }
        binding.zoom.setOnClickListener {
            var hours = rangeToDisplay + 6
            hours = if (hours > 24) 6 else hours
            rxBus.send(EventScale(hours))
        }
        binding.zoom.setOnLongClickListener {
            Calendar.getInstance().also { calendar ->
                calendar.timeInMillis = historyBrowserData.overviewData.fromTime
                calendar[Calendar.MILLISECOND] = 0
                calendar[Calendar.SECOND] = 0
                calendar[Calendar.MINUTE] = 0
                calendar[Calendar.HOUR_OF_DAY] = 0
                setTime(calendar.timeInMillis)
            }
            loadAll("onLongClickZoom")
            true
        }
        binding.chartMenuButton.visibility = preferences.simpleMode.not().toVisibility()

        binding.date.setOnClickListener {
            MaterialDatePicker.Builder.datePicker()
                .setSelection(dateUtil.getTimestampWithCurrentTimeOfDay(historyBrowserData.overviewData.fromTime))
                .setTheme(app.aaps.core.ui.R.style.DatePicker)
                .build()
                .apply {
                    addOnPositiveButtonClickListener { selection ->
                        setTime(dateUtil.mergeUtcDateToTimestamp(historyBrowserData.overviewData.fromTime, selection))
                        binding.date.text = dateUtil.dateAndTimeString(historyBrowserData.overviewData.fromTime)
                        loadAll("onClickDate")
                    }
                }
                .show(supportFragmentManager, "history_date_picker")
        }

        windowManager.currentWindowMetrics

        axisWidth = when {
            resources.displayMetrics.densityDpi <= 120 -> 3
            resources.displayMetrics.densityDpi <= 160 -> 10
            resources.displayMetrics.densityDpi <= 320 -> 35
            resources.displayMetrics.densityDpi <= 420 -> 50
            resources.displayMetrics.densityDpi <= 560 -> 70
            else                                       -> 80
        }
        binding.bgGraph.gridLabelRenderer?.gridColor = rh.gac(this, app.aaps.core.ui.R.attr.graphGrid)
        binding.bgGraph.gridLabelRenderer?.reloadStyles()
        binding.bgGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth

        overviewMenus.setupChartMenu(binding.chartMenuButton, binding.scaleButton)
        prepareGraphsIfNeeded(overviewMenus.setting.size)
        savedInstanceState?.let { bundle ->
            rangeToDisplay = bundle.getInt("rangeToDisplay", 0)
            historyBrowserData.overviewData.fromTime = bundle.getLong("start", 0)
            historyBrowserData.overviewData.toTime = bundle.getLong("end", 0)
        }
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        calculationWorkflow.stopCalculation(CalculationWorkflow.HISTORY_CALCULATION, "onPause")
    }

    @Synchronized
    override fun onDestroy() {
        destroyed = true
        binding.left.setOnClickListener(null)
        binding.right.setOnClickListener(null)
        binding.end.setOnClickListener(null)
        binding.zoom.setOnClickListener(null)
        binding.zoom.setOnLongClickListener(null)
        binding.date.setOnClickListener(null)
        secondaryGraphs.clear()
        secondaryGraphsLabel.clear()
        historyBrowserData.onDestroy()
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
            .subscribe({ updateCalcProgress(it.finalPercent) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventUpdateOverviewGraph::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI("EventRefreshOverview") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI("EventRefreshOverview") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventScale::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           rangeToDisplay = it.hours
                           setTime(historyBrowserData.overviewData.fromTime)
                           loadAll("rangeChange")
                       }, fabricPrivacy::logException)
        updateCalcProgress(100)
        if (historyBrowserData.overviewData.fromTime == 0L) {
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
        outState.putLong("start", historyBrowserData.overviewData.fromTime)
        outState.putLong("end", historyBrowserData.overviewData.toTime)
    }

    private fun prepareGraphsIfNeeded(numOfGraphs: Int) {
        if (numOfGraphs != secondaryGraphs.size - 1) {
            //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
            // rebuild needed
            secondaryGraphs.clear()
            secondaryGraphsLabel.clear()
            binding.secondaryGraphs.removeAllViews()
            (1 until numOfGraphs).forEach { i ->
                val relativeLayout = RelativeLayout(this)
                relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                val graph = GraphViewWithCleanup(this)
                graph.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rh.dpToPx(100)).also { it.setMargins(0, rh.dpToPx(15), 0, rh.dpToPx(10)) }
                graph.gridLabelRenderer?.gridColor = rh.gac(app.aaps.core.ui.R.attr.graphGrid)
                graph.gridLabelRenderer?.reloadStyles()
                graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                graph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                graph.gridLabelRenderer?.numVerticalLabels = 3
                graph.viewport.backgroundColor = rh.gac(this, app.aaps.core.ui.R.attr.viewPortBackgroundColor)
                relativeLayout.addView(graph)

                val label = TextView(this)
                val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(rh.dpToPx(30), rh.dpToPx(25), 0, 0) }
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                label.layoutParams = layoutParams
                relativeLayout.addView(label)
                secondaryGraphsLabel.add(label)

                binding.secondaryGraphs.addView(relativeLayout)
                secondaryGraphs.add(graph)
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun loadAll(from: String) {
        updateDate()
        runCalculation(from)
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
        historyBrowserData.overviewData.fromTime = start + 100000 // little bit more to avoid wrong rounding - GraphView specific
        historyBrowserData.overviewData.toTime =
            historyBrowserData.overviewData.fromTime +
            T.hours(rangeToDisplay.toLong()).msecs()
        historyBrowserData.overviewData.endTime = historyBrowserData.overviewData.toTime
    }

    private fun runCalculation(from: String) {
        calculationWorkflow.runCalculation(
            job = CalculationWorkflow.HISTORY_CALCULATION,
            iobCobCalculator = historyBrowserData.iobCobCalculator,
            overviewData = historyBrowserData.overviewData,
            reason = from,
            end = historyBrowserData.overviewData.toTime,
            bgDataReload = true,
            cause = EventCustomCalculationFinished()
        )
    }

    @Volatile
    var runningRefresh = false

    @Suppress("SameParameterValue")
    private fun refreshLoop(from: String) {
        if (runningRefresh) return
        runningRefresh = true
        rxBus.send(EventRefreshOverview(from))
        aapsLogger.debug(LTag.UI, "refreshLoop finished")
        runningRefresh = false
    }

    private fun updateDate() {
        binding.date.text = dateUtil.dateAndTimeString(historyBrowserData.overviewData.fromTime)
        binding.zoom.text = rangeToDisplay.toString()
    }

    @SuppressLint("SetTextI18n")
    fun updateGUI(from: String) {
        aapsLogger.debug(LTag.UI, "updateGui $from")

        updateDate()
        binding.scaleButton.text = overviewMenus.scaleString(rangeToDisplay)
        val pump = activePlugin.activePump
        val graphData = graphDataProvider.get().with(binding.bgGraph, historyBrowserData.overviewData)
        val menuChartSettings = overviewMenus.setting
        graphData.addInRangeArea(
            historyBrowserData.overviewData.fromTime, historyBrowserData.overviewData.endTime,
            preferences.get(UnitDoubleKey.OverviewLowMark),
            preferences.get(UnitDoubleKey.OverviewHighMark)
        )
        graphData.addBgReadings(menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal], this)
        graphData.addBucketedData()
        graphData.addTreatments(this)
        graphData.addEps(this, 0.95)
        if (menuChartSettings[0][OverviewMenus.CharType.TREAT.ordinal])
            graphData.addTherapyEvents()
        if (menuChartSettings[0][OverviewMenus.CharType.ACT.ordinal])
            graphData.addActivity(0.8)
        if (pump.pumpDescription.isTempBasalCapable && menuChartSettings[0][OverviewMenus.CharType.BAS.ordinal])
            graphData.addBasals()
        graphData.addTargetLine()
        graphData.addRunningModes()
        graphData.addNowLine(dateUtil.now())

        // set manual x bounds to have nice steps
        graphData.setNumVerticalLabels()
        graphData.formatAxis(historyBrowserData.overviewData.fromTime, historyBrowserData.overviewData.endTime)

        graphData.performUpdate()

        // 2nd graphs
        prepareGraphsIfNeeded(menuChartSettings.size)
        val secondaryGraphsData: ArrayList<GraphData> = ArrayList()

        val now = System.currentTimeMillis()
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
            val secondGraphData = graphDataProvider.get().with(secondaryGraphs[g], historyBrowserData.overviewData)
            var useABSForScale = false
            var useIobForScale = false
            var useCobForScale = false
            var useDevForScale = false
            var useRatioForScale = false
            var useVarSensForScale = false
            var useDSForScale = false
            var useBGIForScale = false
            var useHRForScale = false
            var useSTEPSForScale = false
            when {
                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]      -> useABSForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]      -> useIobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]      -> useCobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]      -> useDevForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]      -> useBGIForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]      -> useRatioForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.VAR_SEN.ordinal]  -> useVarSensForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal]       -> useHRForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.STEPS.ordinal]    -> useSTEPSForScale = true
            }
            val alignDevBgiScale = menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] && menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]

            if (menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(useABSForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(useIobForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(useCobForScale, if (useCobForScale) 1.0 else 0.5)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(useDevForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]) secondGraphData.addMinusBGI(useBGIForScale, if (alignDevBgiScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(useRatioForScale, if (useRatioForScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.VAR_SEN.ordinal]) secondGraphData.addVarSens(useVarSensForScale, if (useVarSensForScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && config.isDev()) secondGraphData.addDeviationSlope(useDSForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal] && config.isDev()) secondGraphData.addHeartRate(useHRForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.STEPS.ordinal] && config.isDev()) secondGraphData.addSteps(useSTEPSForScale, 1.0)
            // set manual x bounds to have nice steps
            secondGraphData.formatAxis(historyBrowserData.overviewData.fromTime, historyBrowserData.overviewData.endTime)
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
                    menuChartSettings[g + 1][OverviewMenus.CharType.VAR_SEN.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.STEPS.ordinal]
                ).toVisibility()
            secondaryGraphsData[g].performUpdate()
        }
    }

    private fun updateCalcProgress(percent: Int) {
        binding.progressBar.visibility = (percent != 100).toVisibilityKeepSpace()
        binding.progressBar.progress = percent
    }
}
