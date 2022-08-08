@file:Suppress("DEPRECATION")
package info.nightscout.androidaps.watchfaces.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.os.Vibrator
import android.support.wearable.watchface.WatchFaceStyle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.ustwo.clockwise.common.WatchFaceTime
import com.ustwo.clockwise.common.WatchMode
import com.ustwo.clockwise.common.WatchShape
import com.ustwo.clockwise.wearable.WatchFace
import dagger.android.AndroidInjection
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.RawDisplayData
import info.nightscout.androidaps.events.EventWearPreferenceChange
import info.nightscout.androidaps.events.EventWearToMobile
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.extensions.toVisibilityKeepSpace
import info.nightscout.androidaps.interaction.menus.MainMenuActivity
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import info.nightscout.shared.weardata.EventData.ActionResendData
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

    private var disposable = CompositeDisposable()
    private val rawData = RawDisplayData()

    protected val singleBg get() = rawData.singleBg
    protected val status get() = rawData.status
    private val treatmentData get() = rawData.treatmentData
    private val graphData get() = rawData.graphData

    // Layout
    // @LayoutRes abstract fun layoutResource(): Int
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
    private var batteryReceiver: BroadcastReceiver? = null
    private var colorDarkHigh = 0
    private var colorDarkMid = 0
    private var colorDarkLow = 0
    private var mBackgroundPaint = Paint()

    private lateinit var mTimePaint: Paint
    private lateinit var mSvgPaint: Paint
    private lateinit var mDirectionPaint: Paint
    private lateinit var binding: WatchfaceViewAdapter

    private var mLastSvg = ""
    private var mLastDirection = ""
    private var mYOffset = 0f

    override fun onCreate() {
        // Not derived from DaggerService, do injection here
        AndroidInjection.inject(this)
        super.onCreate()
        colorDarkHigh = ContextCompat.getColor(this, R.color.dark_highColor)
        colorDarkMid = ContextCompat.getColor(this, R.color.dark_midColor)
        colorDarkLow = ContextCompat.getColor(this, R.color.dark_lowColor)
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(displaySize)
        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY)
        specH = if (forceSquareCanvas) specW else View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY)
        disposable += rxBus
            .toObservable(EventWearPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { event: EventWearPreferenceChange ->
                setupBatteryReceiver()
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
                if (!isSimpleUi || !needUpdate()) {
                    setupCharts()
                    setDataFields()
                }
                invalidate()
            }
        rawData.updateFromPersistence(persistence)
        persistence.turnOff()
        setupBatteryReceiver()
        setupSimpleUi()

        val inflater = (getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        val bindLayout = inflateLayout(inflater)
        binding = WatchfaceViewAdapter.getBinding(bindLayout)
        layoutView = binding.root
        performViewSetup()
        rxBus.send(EventWearToMobile(ActionResendData("BaseWatchFace::onCreate")))
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

    private fun setupBatteryReceiver() {
        val setting = sp.getString(R.string.key_simplify_ui, "off")
        if ((setting == "charging" || setting == "ambient_charging") && batteryReceiver == null) {
            val intentBatteryFilter = IntentFilter()
            intentBatteryFilter.addAction(BatteryManager.ACTION_CHARGING)
            intentBatteryFilter.addAction(BatteryManager.ACTION_DISCHARGING)
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    setDataFields()
                    invalidate()
                }
            }
            registerReceiver(batteryReceiver, intentBatteryFilter)
        }
    }

    private fun setupSimpleUi() {
        val black = ContextCompat.getColor(this, R.color.black)
        mBackgroundPaint.color = black
        val white = ContextCompat.getColor(this, R.color.white)
        val resources = this.resources
        val textSizeSvg = resources.getDimension(R.dimen.simple_ui_svg_text_size)
        val textSizeDirection = resources.getDimension(R.dimen.simple_ui_direction_text_size)
        val textSizeTime = resources.getDimension(R.dimen.simple_ui_time_text_size)
        mYOffset = resources.getDimension(R.dimen.simple_ui_y_offset)
        mSvgPaint = createTextPaint(NORMAL_TYPEFACE, white, textSizeSvg)
        mDirectionPaint = createTextPaint(BOLD_TYPEFACE, white, textSizeDirection)
        mTimePaint = createTextPaint(NORMAL_TYPEFACE, white, textSizeTime)
    }

    private fun createTextPaint(typeface: Typeface, colour: Int, textSize: Float): Paint {
        val paint = Paint()
        paint.color = colour
        paint.typeface = typeface
        paint.isAntiAlias = true
        paint.textSize = textSize
        return paint
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
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver)
        }
        super.onDestroy()
    }

    override fun getInteractiveModeUpdateRate(): Long {
        return 60 * 1000L // Only call onTimeChanged every 60 seconds
    }

    override fun onDraw(canvas: Canvas) {
        if (isSimpleUi) {
            onDrawSimpleUi(canvas)
        } else {
            if (layoutSet) {
                binding.mainLayout.measure(specW, specH)
                val y = if (forceSquareCanvas) displaySize.x else displaySize.y // Square Steampunk
                binding.mainLayout.layout(0, 0, displaySize.x, y)
                binding.mainLayout.draw(canvas)
            }
        }
    }

    private fun onDrawSimpleUi(canvas: Canvas) {
        canvas.drawRect(0f, 0f, displaySize.x.toFloat(), displaySize.y.toFloat(), mBackgroundPaint)
        val xHalf = displaySize.x / 2f
        val yThird = displaySize.y / 3f
        val isOutdated = singleBg.timeStamp > 0 && ageLevel() <= 0
        mSvgPaint.isStrikeThruText = isOutdated
        mSvgPaint.color = getBgColour(singleBg.sgvLevel)
        mDirectionPaint.color = getBgColour(singleBg.sgvLevel)
        val sSvg = singleBg.sgvString
        val svgWidth = mSvgPaint.measureText(sSvg)
        val sDirection = " " + singleBg.slopeArrow + "\uFE0E"
        val directionWidth = mDirectionPaint.measureText(sDirection)
        val xSvg = xHalf - (svgWidth + directionWidth) / 2
        canvas.drawText(sSvg, xSvg, yThird + mYOffset, mSvgPaint)
        val xDirection = xSvg + svgWidth
        canvas.drawText(sDirection, xDirection, yThird + mYOffset, mDirectionPaint)
        val sTime = dateUtil.timeString()
        val xTime = xHalf - mTimePaint.measureText(sTime) / 2f
        canvas.drawText(sTime, xTime, yThird * 2f + mYOffset, mTimePaint)
    }

    private fun getBgColour(level: Long): Int {
        if (level == 1L) {
            return colorDarkHigh
        }
        return if (level == 0L) {
            colorDarkMid
        } else colorDarkLow
    }

    override fun onTimeChanged(oldTime: WatchFaceTime, newTime: WatchFaceTime) {
        if (layoutSet && (newTime.hasHourChanged(oldTime) || newTime.hasMinuteChanged(oldTime))) {
            missedReadingAlert()
            checkVibrateHourly(oldTime, newTime)
            if (!isSimpleUi) setDataFields()
        }
    }

    private val isCharging: Boolean
        get() {
            val mBatteryStatus = this.registerReceiver(null, iFilter)
            val status = mBatteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
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
                binding.AAPSv2 != null                                       -> status.battery + "%"
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
        if (!isSimpleUi) {
            setDataFields()
        }
        invalidate()
    }

    private fun setDateAndTime() {
        binding.time?.text = dateUtil.timeString()
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
        if (isSimpleUi) setSimpleUiAntiAlias()
        else setDataFields()
        invalidate()
    }

    private fun setSimpleUiAntiAlias() {
        val antiAlias = currentWatchMode == WatchMode.AMBIENT
        mSvgPaint.isAntiAlias = antiAlias
        mDirectionPaint.isAntiAlias = antiAlias
        mTimePaint.isAntiAlias = antiAlias
    }

    private fun isLowRes(watchMode: WatchMode): Boolean {
        return watchMode == WatchMode.LOW_BIT || watchMode == WatchMode.LOW_BIT_BURN_IN
    }

    private val isSimpleUi: Boolean
        get() {
            val simplify = sp.getString(R.string.key_simplify_ui, "off")
            return if (simplify == "off") false
            else if ((simplify == "ambient" || simplify == "ambient_charging") && currentWatchMode == WatchMode.AMBIENT) true
            else (simplify == "charging" || simplify == "ambient_charging") && isCharging
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
        if (isSimpleUi) {
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

        var iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val NORMAL_TYPEFACE: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val BOLD_TYPEFACE: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        const val SCREEN_SIZE_SMALL = 280
    }
}
