package info.nightscout.androidaps.historyBrowser

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.jjoe64.graphview.GraphView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.events.EventCustomCalculationFinished
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.OverviewMenus
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensBgLoaded
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_historybrowse.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

class HistoryBrowseActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var iobCobCalculatorPluginHistory: IobCobCalculatorPluginHistory
    @Inject lateinit var treatmentsPluginHistory: TreatmentsPluginHistory
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

    private var axisWidth: Int = 0
    private var rangeToDisplay = 24 // for graph
    private var start: Long = 0

    private val graphLock = Object()

    private var eventCustomCalculationFinished = EventCustomCalculationFinished()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historybrowse)

        historybrowse_left.setOnClickListener {
            start -= T.hours(rangeToDisplay.toLong()).msecs()
            runCalculation("onClickLeft")
        }
        historybrowse_right.setOnClickListener {
            start += T.hours(rangeToDisplay.toLong()).msecs()
            runCalculation("onClickRight")
        }
        historybrowse_end.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar[Calendar.MILLISECOND] = 0
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MINUTE] = 0
            calendar[Calendar.HOUR_OF_DAY] = 0
            start = calendar.timeInMillis
            runCalculation("onClickEnd")
        }
        historybrowse_zoom.setOnClickListener {
            rangeToDisplay += 6
            rangeToDisplay = if (rangeToDisplay > 24) 6 else rangeToDisplay
            updateGUI("rangeChange", false)
        }
        historybrowse_zoom.setOnLongClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = start
            calendar[Calendar.MILLISECOND] = 0
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MINUTE] = 0
            calendar[Calendar.HOUR_OF_DAY] = 0
            start = calendar.timeInMillis
            runCalculation("onLongClickZoom")
            true
        }

        // create an OnDateSetListener
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = start
            cal[Calendar.YEAR] = year
            cal[Calendar.MONTH] = monthOfYear
            cal[Calendar.DAY_OF_MONTH] = dayOfMonth
            cal[Calendar.MILLISECOND] = 0
            cal[Calendar.SECOND] = 0
            cal[Calendar.MINUTE] = 0
            cal[Calendar.HOUR_OF_DAY] = 0
            start = cal.timeInMillis
            historybrowse_date?.text = dateUtil.dateAndTimeString(start)
            runCalculation("onClickDate")
        }

        historybrowse_date.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = start
            DatePickerDialog(this, dateSetListener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dm = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(dm)

        axisWidth = if (dm.densityDpi <= 120) 3 else if (dm.densityDpi <= 160) 10 else if (dm.densityDpi <= 320) 35 else if (dm.densityDpi <= 420) 50 else if (dm.densityDpi <= 560) 70 else 80
        historybrowse_bggraph?.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
        historybrowse_bggraph?.gridLabelRenderer?.reloadStyles()
        historybrowse_bggraph?.gridLabelRenderer?.labelVerticalWidth = axisWidth

        overviewMenus.setupChartMenu(overview_chartMenuButton)
        prepareGraphsIfNeeded(overviewMenus.setting.size)
        savedInstanceState?.let { bundle ->
            rangeToDisplay = bundle.getInt("rangeToDisplay", 0)
            start = bundle.getLong("start", 0)
        }

    }

    public override fun onPause() {
        super.onPause()
        disposable.clear()
        iobCobCalculatorPluginHistory.stopCalculation("onPause")
    }

    public override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                // catch only events from iobCobCalculatorPluginHistory
                if (it.cause is EventCustomCalculationFinished) {
                    updateGUI("EventAutosensCalculationFinished", bgOnly = false)
                }
            }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventAutosensBgLoaded::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                // catch only events from iobCobCalculatorPluginHistory
                if (it.cause is EventCustomCalculationFinished) {
                    updateGUI("EventAutosensCalculationFinished", bgOnly = true)
                }
            }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventIobCalculationProgress::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ overview_iobcalculationprogess?.text = it.progress }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (it.now) {
                    updateGUI("EventRefreshOverview", bgOnly = false)
                }
            }, fabricPrivacy::logException)
        )
        if (start == 0L) {
            // set start of current day
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar[Calendar.MILLISECOND] = 0
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MINUTE] = 0
            calendar[Calendar.HOUR_OF_DAY] = 0
            start = calendar.timeInMillis
            runCalculation("onResume")
        } else {
            updateGUI("onResume", bgOnly = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("rangeToDisplay", rangeToDisplay)
        outState.putLong("start", start)

    }

    private fun prepareGraphsIfNeeded(numOfGraphs: Int) {
        synchronized(graphLock) {
            if (numOfGraphs != secondaryGraphs.size - 1) {
                //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
                // rebuild needed
                secondaryGraphs.clear()
                secondaryGraphsLabel.clear()
                history_iobgraph.removeAllViews()
                for (i in 1 until numOfGraphs) {
                    val relativeLayout = RelativeLayout(this)
                    relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                    val graph = GraphView(this)
                    graph.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resourceHelper.dpToPx(100)).also { it.setMargins(0, resourceHelper.dpToPx(15), 0, resourceHelper.dpToPx(10)) }
                    graph.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
                    graph.gridLabelRenderer?.reloadStyles()
                    graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                    graph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                    graph.gridLabelRenderer?.numVerticalLabels = 3
                    graph.viewport.backgroundColor = Color.argb(20, 255, 255, 255) // 8% of gray
                    relativeLayout.addView(graph)

                    val label = TextView(this)
                    val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(resourceHelper.dpToPx(30), resourceHelper.dpToPx(25), 0, 0) }
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    label.layoutParams = layoutParams
                    relativeLayout.addView(label)
                    secondaryGraphsLabel.add(label)

                    history_iobgraph.addView(relativeLayout)
                    secondaryGraphs.add(graph)
                }
            }
        }
    }

    private fun runCalculation(from: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            treatmentsPluginHistory.initializeData(start - T.hours(8).msecs())
            val end = start + T.hours(rangeToDisplay.toLong()).msecs()
            iobCobCalculatorPluginHistory.stopCalculation(from)
            iobCobCalculatorPluginHistory.clearCache()
            iobCobCalculatorPluginHistory.runCalculation(from, end, true, false, eventCustomCalculationFinished)
        }
    }

    fun updateGUI(from: String, bgOnly: Boolean) {
        val menuChartSettings = overviewMenus.setting
        prepareGraphsIfNeeded(menuChartSettings.size)
        aapsLogger.debug(LTag.UI, "updateGUI from: $from")
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()

        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()

        lifecycleScope.launch(Dispatchers.Main) {
            historybrowse_noprofile?.visibility = (profile == null).toVisibility()
            profile ?: return@launch

            historybrowse_bggraph ?: return@launch
            historybrowse_date?.text = dateUtil.dateAndTimeString(start)
            historybrowse_zoom?.text = rangeToDisplay.toString()
            val graphData = GraphData(injector, historybrowse_bggraph, iobCobCalculatorPluginHistory, treatmentsPluginHistory)
            val secondaryGraphsData: ArrayList<GraphData> = ArrayList()

            // do preparation in different thread
            withContext(Dispatchers.Default) {
                val fromTime: Long = start + T.secs(100).msecs()
                val toTime: Long = start + T.hours(rangeToDisplay.toLong()).msecs() + T.secs(100).msecs()
                aapsLogger.debug(LTag.UI, "Period: " + dateUtil.dateAndTimeString(fromTime) + " - " + dateUtil.dateAndTimeString(toTime))
                val pointer = System.currentTimeMillis()

                // **** In range Area ****
                graphData.addInRangeArea(fromTime, toTime, lowLine, highLine)

                // **** BG ****
                graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null)

                // set manual x bounds to have nice steps
                graphData.formatAxis(fromTime, toTime)

                // add target line
                graphData.addTargetLine(fromTime, toTime, profile, null)

                // **** NOW line ****
                graphData.addNowLine(pointer)

                if (!bgOnly) {
                    // Treatments
                    graphData.addTreatments(fromTime, toTime)
                    if (menuChartSettings[0][OverviewMenus.CharType.ACT.ordinal])
                        graphData.addActivity(fromTime, toTime, false, 0.8)

                    // add basal data
                    if (pump.pumpDescription.isTempBasalCapable && menuChartSettings[0][OverviewMenus.CharType.BAS.ordinal]) {
                        graphData.addBasals(fromTime, toTime, lowLine / graphData.maxY / 1.2)
                    }
                    // ------------------ 2nd graph
                    synchronized(graphLock) {
                        for (g in 0 until secondaryGraphs.size) {
                            val secondGraphData = GraphData(injector, secondaryGraphs[g], iobCobCalculatorPluginHistory, treatmentsPluginHistory)
                            var useIobForScale = false
                            var useCobForScale = false
                            var useDevForScale = false
                            var useRatioForScale = false
                            var useDSForScale = false
                            var useIAForScale = false
                            var useABSForScale = false
                            when {
                                menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]      -> useIobForScale = true
                                menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]      -> useCobForScale = true
                                menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]      -> useDevForScale = true
                                menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]      -> useRatioForScale = true
                                menuChartSettings[g + 1][OverviewMenus.CharType.ACT.ordinal]      -> useIAForScale = true
                                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]      -> useABSForScale = true
                                menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                            }

                            if (menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(fromTime, toTime, useIobForScale, 1.0, menuChartSettings[g + 1][OverviewMenus.CharType.PRE.ordinal])
                            if (menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(fromTime, toTime, useCobForScale, if (useCobForScale) 1.0 else 0.5)
                            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(fromTime, toTime, useDevForScale, 1.0)
                            if (menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(fromTime, toTime, useRatioForScale, 1.0)
                            if (menuChartSettings[g + 1][OverviewMenus.CharType.ACT.ordinal]) secondGraphData.addActivity(fromTime, toTime, useIAForScale, 0.8)
                            if (menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(fromTime, toTime, useABSForScale, 1.0)
                            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && buildHelper.isDev()) secondGraphData.addDeviationSlope(fromTime, toTime, useDSForScale, 1.0)

                            // set manual x bounds to have nice steps
                            secondGraphData.formatAxis(fromTime, toTime)
                            secondGraphData.addNowLine(pointer)
                            secondaryGraphsData.add(secondGraphData)
                        }
                    }
                }
            }
            // finally enforce drawing of graphs in UI thread
            graphData.performUpdate()
            if (!bgOnly)
                synchronized(graphLock) {
                    for (g in 0 until secondaryGraphs.size) {
                        secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)
                        secondaryGraphs[g].visibility = (!bgOnly && (
                            menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal] ||
                                menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal] ||
                                menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] ||
                                menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal] ||
                                menuChartSettings[g + 1][OverviewMenus.CharType.ACT.ordinal] ||
                                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal] ||
                                menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal]
                            )).toVisibility()
                        secondaryGraphsData[g].performUpdate()
                    }
                }
        }
    }
}