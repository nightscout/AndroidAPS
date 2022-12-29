@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.*
import android.os.Vibrator
import android.support.wearable.watchface.WatchFaceStyle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.viewbinding.ViewBinding
import com.ustwo.clockwise.common.WatchFaceTime
import com.ustwo.clockwise.common.WatchMode
import com.ustwo.clockwise.common.WatchShape
import com.ustwo.clockwise.wearable.WatchFace
import dagger.android.AndroidInjection
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.androidaps.events.EventWearPreferenceChange
import info.nightscout.androidaps.interaction.menus.MainMenuActivity
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventWearToMobile
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.EventData
import info.nightscout.rx.weardata.EventData.ActionResendData
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.extensions.toVisibilityKeepSpace
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.floor

/**
 * Created by emmablack on 12/29/14.
 * Updated by andrew-warrington on 02-Jan-2018.
 * Refactored by dlvoy on 2019-11-2019
 * Refactored by MilosKozak 24/04/2022
 */

abstract class BaseWatchFace : WatchFace() {

    @Inject lateinit var wearUtil: WearUtil
    @Inject lateinit var persistence: Persistence
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var simpleUi: SimpleUi

    private var disposable = CompositeDisposable()
    private val rawData = RawDisplayData()

    protected val singleBg get() = rawData.singleBg
    protected val status get() = rawData.status
    private val treatmentData get() = rawData.treatmentData
    private val graphData get() = rawData.graphData

    abstract fun inflateLayout(inflater: LayoutInflater): ViewBinding

    private val displaySize = Point()

    var ageLevel = 1
    var loopLevel = -1
    var highColor = Color.YELLOW
    var lowColor = Color.RED
    var midColor = Color.WHITE
    var gridColor = Color.WHITE
    var basalBackgroundColor = Color.BLUE
    var basalCenterColor = Color.BLUE
    private var bolusColor = Color.MAGENTA
    private var lowResMode = false
    private var layoutSet = false
    var bIsRound = false
    var dividerMatchesBg = false
    var pointSize = 2

    // Tapping times
    private var sgvTapTime: Long = 0
    private var chartTapTime: Long = 0
    private var mainMenuTapTime: Long = 0

    // related endTime manual layout
    var layoutView: View? = null
    private var specW = 0
    private var specH = 0
    var forceSquareCanvas = false // Set to true by the Steampunk watch face.

    private lateinit var binding: WatchfaceViewAdapter

    private var mLastSvg = ""
    private var mLastDirection = ""

    override fun onCreate() {
        // Not derived from DaggerService, do injection here
        AndroidInjection.inject(this)
        super.onCreate()
        simpleUi.onCreate(::forceUpdate)
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(displaySize)
        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY)
        specH = if (forceSquareCanvas) specW else View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY)
        disposable += rxBus
            .toObservable(EventWearPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { event: EventWearPreferenceChange ->
                simpleUi.updatePreferences()
                if (event.changedKey != null && event.changedKey == "delta_granularity") rxBus.send(EventWearToMobile(ActionResendData("BaseWatchFace:onSharedPreferenceChanged")))
                if (layoutSet) setDataFields()
                invalidate()
            }
        disposable += rxBus
            .toObservable(EventData.Status::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                // this event is received as last batch of data
                rawData.updateFromPersistence(persistence)
                if (!simpleUi.isEnabled(currentWatchMode) || !needUpdate()) {
                    setupCharts()
                    setDataFields()
                }
                invalidate()
            }
        rawData.updateFromPersistence(persistence)
        persistence.turnOff()

        val inflater = (getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        val bindLayout = inflateLayout(inflater)
        binding = WatchfaceViewAdapter.getBinding(bindLayout)
        layoutView = binding.root
        performViewSetup()
        rxBus.send(EventWearToMobile(ActionResendData("BaseWatchFace::onCreate")))
    }

    private fun forceUpdate() {
        setDataFields()
        invalidate()
    }

    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
        binding.chart?.let { chart ->
            if (tapType == TAP_TYPE_TAP && x >= chart.left && x <= chart.right && y >= chart.top && y <= chart.bottom) {
                if (eventTime - chartTapTime < 800) {
                    changeChartTimeframe()
                }
                chartTapTime = eventTime
                return
            }
        }
        binding.sgv?.let { mSgv ->
            val extra = (mSgv.right - mSgv.left) / 2
            if (tapType == TAP_TYPE_TAP && x + extra >= mSgv.left && x - extra <= mSgv.right && y >= mSgv.top && y <= mSgv.bottom) {
                if (eventTime - sgvTapTime < 800) {
                    startActivity(Intent(this, MainMenuActivity::class.java).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                sgvTapTime = eventTime
            }
        }
        binding.chartZoomTap?.let { mChartTap ->
            if (tapType == TAP_TYPE_TAP && x >= mChartTap.left && x <= mChartTap.right && y >= mChartTap.top && y <= mChartTap.bottom) {
                if (eventTime - chartTapTime < 800) {
                    changeChartTimeframe()
                }
                chartTapTime = eventTime
                return
            }
        }
        binding.mainMenuTap?.let { mMainMenuTap ->
            if (tapType == TAP_TYPE_TAP && x >= mMainMenuTap.left && x <= mMainMenuTap.right && y >= mMainMenuTap.top && y <= mMainMenuTap.bottom) {
                if (eventTime - mainMenuTapTime < 800) {
                    startActivity(Intent(this, MainMenuActivity::class.java).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                mainMenuTapTime = eventTime
                return
            }
        }
    }

    open fun changeChartTimeframe() {
        var timeframe = sp.getInt(R.string.key_chart_time_frame, 3)
        timeframe = timeframe % 5 + 1
        sp.putString(R.string.key_chart_time_frame, timeframe.toString())
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build()
    }

    override fun onLayout(shape: WatchShape, screenBounds: Rect, screenInsets: WindowInsets) {
        super.onLayout(shape, screenBounds, screenInsets)
        layoutView?.onApplyWindowInsets(screenInsets)
        bIsRound = screenInsets.isRound
    }

    private fun performViewSetup() {
        layoutSet = true
        setupCharts()
        setDataFields()
        missedReadingAlert()
    }

    fun ageLevel(): Int =
        if (timeSince() <= 1000 * 60 * 12) 1 else 0

    fun timeSince(): Double {
        return (System.currentTimeMillis() - singleBg.timeStamp).toDouble()
    }

    private fun readingAge(shortString: Boolean): String {
        if (singleBg.timeStamp == 0L) {
            return if (shortString) "--" else "-- Minute ago"
        }
        val minutesAgo = floor(timeSince() / (1000 * 60)).toInt()
        return if (minutesAgo == 1) {
            minutesAgo.toString() + if (shortString) "'" else " Minute ago"
        } else minutesAgo.toString() + if (shortString) "'" else " Minutes ago"
    }

    override fun onDestroy() {
        disposable.clear()
        simpleUi.onDestroy()
        super.onDestroy()
    }

    override fun getInteractiveModeUpdateRate(): Long {
        return 60 * 1000L // Only call onTimeChanged every 60 seconds
    }

    override fun onDraw(canvas: Canvas) {
        if (simpleUi.isEnabled(currentWatchMode)) {
            simpleUi.onDraw(canvas, singleBg)
        } else {
            if (layoutSet) {
                binding.mainLayout.measure(specW, specH)
                val y = if (forceSquareCanvas) displaySize.x else displaySize.y // Square Steampunk
                binding.mainLayout.layout(0, 0, displaySize.x, y)
                binding.mainLayout.draw(canvas)
            }
        }
    }

    override fun onTimeChanged(oldTime: WatchFaceTime, newTime: WatchFaceTime) {
        if (layoutSet && (newTime.hasHourChanged(oldTime) || newTime.hasMinuteChanged(oldTime))) {
            missedReadingAlert()
            checkVibrateHourly(oldTime, newTime)
            if (!simpleUi.isEnabled(currentWatchMode)) setDataFields()
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun checkVibrateHourly(oldTime: WatchFaceTime, newTime: WatchFaceTime) {
        val hourlyVibratePref = sp.getBoolean(R.string.key_vibrate_hourly, false)
        if (hourlyVibratePref && layoutSet && newTime.hasHourChanged(oldTime)) {
            aapsLogger.info(LTag.WEAR, "hourlyVibratePref", "true --> $newTime")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val vibrationPattern = longArrayOf(0, 150, 125, 100)
            vibrator.vibrate(vibrationPattern, -1)
        }
    }

    @SuppressLint("SetTextI18n")
    open fun setDataFields() {
        setDateAndTime()
        binding.sgv?.text = singleBg.sgvString
        binding.sgv?.visibility = sp.getBoolean(R.string.key_show_bg, true).toVisibilityKeepSpace()
        strikeThroughSgvIfNeeded()
        binding.direction?.text = "${singleBg.slopeArrow}\uFE0E"
        binding.direction?.visibility = sp.getBoolean(R.string.key_show_direction, true).toVisibility()
        binding.delta?.text = singleBg.delta
        binding.delta?.visibility = sp.getBoolean(R.string.key_show_delta, true).toVisibility()
        binding.avgDelta?.text = singleBg.avgDelta
        binding.avgDelta?.visibility = sp.getBoolean(R.string.key_show_avg_delta, true).toVisibility()
        binding.cob1?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
        binding.cob2?.text = status.cob
        binding.cob2?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
        binding.iob1?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
        binding.iob2?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
        binding.iob1?.text = if (status.detailedIob) status.iobSum else getString(R.string.activity_IOB)
        binding.iob2?.text = if (status.detailedIob) status.iobDetail else status.iobSum
        binding.timestamp.visibility = sp.getBoolean(R.string.key_show_ago, true).toVisibility()
        binding.timestamp.text = readingAge(if (binding.AAPSv2 != null) true else sp.getBoolean(R.string.key_show_external_status, true))
        binding.uploaderBattery?.visibility = sp.getBoolean(R.string.key_show_uploader_battery, true).toVisibility()
        binding.uploaderBattery?.text =
            when {
                binding.AAPSv2 != null                                 -> status.battery + "%"
                sp.getBoolean(R.string.key_show_external_status, true) -> "U: ${status.battery}%"
                else                                                   -> "Uploader: ${status.battery}%"
            }
        binding.rigBattery?.visibility = sp.getBoolean(R.string.key_show_rig_battery, false).toVisibility()
        binding.rigBattery?.text = status.rigBattery
        binding.basalRate?.text = status.currentBasal
        binding.basalRate?.visibility = sp.getBoolean(R.string.key_show_temp_basal, true).toVisibility()
        binding.bgi?.text = status.bgi
        binding.bgi?.visibility = status.showBgi.toVisibility()
        binding.status?.text = status.externalStatus
        binding.status?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
        binding.loop?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
        if (status.openApsStatus != -1L) {
            val minutes = ((System.currentTimeMillis() - status.openApsStatus) / 1000 / 60).toInt()
            binding.loop?.text = "$minutes'"
            if (minutes > 14) {
                loopLevel = 0
                binding.loop?.setBackgroundResource(R.drawable.loop_red_25)
            } else {
                loopLevel = 1
                binding.loop?.setBackgroundResource(R.drawable.loop_green_25)
            }
        } else {
            loopLevel = -1
            binding.loop?.text = "-"
            binding.loop?.setBackgroundResource(R.drawable.loop_grey_25)
        }
        setColor()
    }

    override fun on24HourFormatChanged(is24HourFormat: Boolean) {
        if (!simpleUi.isEnabled(currentWatchMode)) {
            setDataFields()
        }
        invalidate()
    }

    private fun setDateAndTime() {
        binding.time?.text = if(binding.timePeriod == null) dateUtil.timeString() else dateUtil.hourString() + ":" + dateUtil.minuteString()
        binding.hour?.text = dateUtil.hourString()
        binding.minute?.text = dateUtil.minuteString()
        binding.dateTime?.visibility = sp.getBoolean(R.string.key_show_date, false).toVisibility()
        binding.dayName?.text = dateUtil.dayNameString()
        binding.day?.text = dateUtil.dayString()
        binding.month?.text = dateUtil.monthString()
        binding.timePeriod?.visibility = android.text.format.DateFormat.is24HourFormat(this).not().toVisibility()
        binding.timePeriod?.text = dateUtil.amPm()
    }

    private fun setColor() {
        dividerMatchesBg = sp.getBoolean(R.string.key_match_divider, false)
        when {
            lowResMode                             -> setColorLowRes()
            sp.getBoolean(R.string.key_dark, true) -> setColorDark()
            else                                   -> setColorBright()
        }
    }

    private fun strikeThroughSgvIfNeeded() {
        @Suppress("DEPRECATION")
        binding.sgv?.let { mSgv ->
            if (ageLevel() <= 0 && singleBg.timeStamp > 0) mSgv.paintFlags = mSgv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else mSgv.paintFlags = mSgv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    override fun onWatchModeChanged(watchMode: WatchMode) {
        lowResMode = isLowRes(watchMode)
        if (simpleUi.isEnabled(currentWatchMode)) simpleUi.setAntiAlias(currentWatchMode)
        else setDataFields()
        invalidate()
    }

    private fun isLowRes(watchMode: WatchMode): Boolean {
        return watchMode == WatchMode.LOW_BIT || watchMode == WatchMode.LOW_BIT_BURN_IN
    }

    protected abstract fun setColorDark()
    protected abstract fun setColorBright()
    protected abstract fun setColorLowRes()
    private fun missedReadingAlert() {
        val minutesSince = floor(timeSince() / (1000 * 60)).toInt()
        if (singleBg.timeStamp == 0L || minutesSince >= 16 && (minutesSince - 16) % 5 == 0) {
            // Attempt endTime recover missing data
            rxBus.send(EventWearToMobile(ActionResendData("BaseWatchFace:missedReadingAlert")))
        }
    }

    fun setupCharts() {
        if (simpleUi.isEnabled(currentWatchMode)) {
            return
        }
        if (binding.chart != null && graphData.entries.size > 0) {
            val timeframe = sp.getInt(R.string.key_chart_time_frame, 3)
            val bgGraphBuilder =
                if (lowResMode)
                    BgGraphBuilder(
                        sp, dateUtil, graphData.entries, treatmentData.predictions, treatmentData.temps, treatmentData.basals, treatmentData.boluses, pointSize,
                        midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe
                    )
                else
                    BgGraphBuilder(
                        sp, dateUtil, graphData.entries, treatmentData.predictions, treatmentData.temps, treatmentData.basals, treatmentData.boluses,
                        pointSize, highColor, lowColor, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, Color.GREEN, timeframe
                    )
            binding.chart?.lineChartData = bgGraphBuilder.lineData()
            binding.chart?.isViewportCalculationEnabled = true
        }
    }

    private fun needUpdate(): Boolean {
        if (mLastSvg == singleBg.sgvString && mLastDirection == singleBg.sgvString) {
            return false
        }
        mLastSvg = singleBg.sgvString
        mLastDirection = singleBg.sgvString
        return true
    }

    companion object {

        const val SCREEN_SIZE_SMALL = 280
    }
}
