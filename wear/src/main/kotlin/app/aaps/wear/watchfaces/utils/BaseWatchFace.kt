@file:Suppress("DEPRECATION")

package app.aaps.wear.watchfaces.utils

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
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.ActionResendData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.extensions.toVisibilityKeepSpace
import app.aaps.wear.R
import app.aaps.wear.data.RawDisplayData
import app.aaps.wear.events.EventWearPreferenceChange
import app.aaps.wear.interaction.menus.MainMenuActivity
import app.aaps.wear.interaction.utils.Persistence
import com.ustwo.clockwise.common.WatchFaceTime
import com.ustwo.clockwise.common.WatchMode
import com.ustwo.clockwise.common.WatchShape
import com.ustwo.clockwise.wearable.WatchFace
import dagger.android.AndroidInjection
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.floor

/**
 * Created by emmablack on 12/29/14.
 * Updated by andrew-warrington on 02-Jan-2018.
 * Refactored by dlvoy on 2019-11-2019
 * Refactored by MilosKozak 24/04/2022
 * Updated by Philoul to manage external data 06/11/2024
 */

abstract class BaseWatchFace : WatchFace() {

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

    var loopLevel = -1
    var loopLevelExt1 = -1
    var loopLevelExt2 = -1
    var enableExt1 = false
    var enableExt2 = false
    var highColor = Color.YELLOW
    var lowColor = Color.RED
    var midColor = Color.WHITE
    var gridColor = Color.WHITE
    var basalBackgroundColor = Color.BLUE
    var basalCenterColor = Color.BLUE
    var carbColor = Color.GREEN
    var tempTargetColor = Color.YELLOW
    var tempTargetProfileColor = Color.WHITE
    var tempTargetLoopColor = Color.GREEN
    var reservoirColor = Color.WHITE
    var reservoirUrgentColor = Color.RED
    var reservoirWarningColor = Color.YELLOW
    private var bolusColor = Color.MAGENTA
    private var lowResMode = false
    private var layoutSet = false
    var bIsRound = false
    var dividerMatchesBg = false
    var pointSize = 2
    var enableSecond = false
    var detailedIob = false
    var externalStatus = ""
    var externalStatusExt1 = ""
    var externalStatusExt2 = ""
    var dayNameFormat = "E"
    var monthFormat = "MMM"
    val showSecond: Boolean
        get() = enableSecond && currentWatchMode == WatchMode.INTERACTIVE

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

    fun ageLevel(id: Int = 0): Int =
        if (timeSince(id) <= 1000 * 60 * 12) 1 else 0

    fun timeSince(id: Int = 0): Double = (System.currentTimeMillis() - singleBg[id].timeStamp).toDouble()

    private fun readingAge(id: Int = 0): String {
        val localBg = singleBg[id]
        if (localBg.timeStamp == 0L) {
            return "--"
        }
        val minutesAgo = floor(timeSince(id) / (1000 * 60)).toInt()
        return minutesAgo.toString() + "'"
    }

    override fun onDestroy() {
        disposable.clear()
        simpleUi.onDestroy()
        super.onDestroy()
    }

    override fun getInteractiveModeUpdateRate(): Long {
        return if (showSecond) 1000L else 60 * 1000L // Only call onTimeChanged every 60 seconds
    }

    override fun onDraw(canvas: Canvas) {
        if (simpleUi.isEnabled(currentWatchMode)) {
            simpleUi.onDraw(canvas, singleBg[0])
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
        } else if (layoutSet && !simpleUi.isEnabled(currentWatchMode) && showSecond && newTime.hasSecondChanged(oldTime)) {
            setSecond()
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
        detailedIob = sp.getBoolean(R.string.key_show_detailed_iob, false)
        val showBgi = sp.getBoolean(R.string.key_show_bgi, false)
        val detailedDelta = sp.getBoolean(R.string.key_show_detailed_delta, false)
        setDateAndTime()
        binding.patientName?.text = status[0].patientName
        binding.sgv?.text = singleBg[0].sgvString
        binding.sgv?.visibility = sp.getBoolean(R.string.key_show_bg, true).toVisibilityKeepSpace()
        binding.direction?.text = "${singleBg[0].slopeArrow}\uFE0E"
        binding.direction?.visibility = sp.getBoolean(R.string.key_show_direction, true).toVisibility()
        binding.delta?.text = if (detailedDelta) singleBg[0].deltaDetailed else singleBg[0].delta
        binding.delta?.visibility = sp.getBoolean(R.string.key_show_delta, true).toVisibility()
        binding.avgDelta?.text = if (detailedDelta) singleBg[0].avgDeltaDetailed else singleBg[0].avgDelta
        binding.avgDelta?.visibility = sp.getBoolean(R.string.key_show_avg_delta, true).toVisibility()
        binding.tempTarget?.text = status[0].tempTarget
        binding.tempTarget?.visibility = sp.getBoolean(R.string.key_show_temp_target, true).toVisibility()
        binding.reservoir?.text = status[0].reservoirString
        binding.reservoir?.visibility = sp.getBoolean(R.string.key_show_reservoir_level, true).toVisibility()
        binding.cob1?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
        binding.cob1?.text = getString(R.string.activity_carb)
        binding.cob2?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
        binding.cob2?.text = status[0].cob
        binding.iob1?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
        binding.iob1?.text = if (detailedIob) status[0].iobSum else getString(R.string.activity_IOB)
        binding.iob2?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
        binding.iob2?.text = if (detailedIob) status[0].iobDetail else status[0].iobSum
        binding.timestamp.visibility = sp.getBoolean(R.string.key_show_ago, true).toVisibility()
        binding.timestamp.text = readingAge()
        binding.uploaderBattery?.visibility = sp.getBoolean(R.string.key_show_uploader_battery, true).toVisibility()
        binding.uploaderBattery?.text = status[0].battery + "%"
        binding.rigBattery?.visibility = sp.getBoolean(R.string.key_show_rig_battery, false).toVisibility()
        binding.rigBattery?.text = status[0].rigBattery
        binding.basalRate?.visibility = sp.getBoolean(R.string.key_show_temp_basal, true).toVisibility()
        binding.basalRate?.text = status[0].currentBasal
        binding.bgi?.visibility = showBgi.toVisibility()
        binding.bgi?.text = status[0].bgi
        val iobString =
            if (detailedIob) "${status[0].iobSum} ${status[0].iobDetail}"
            else status[0].iobSum + getString(R.string.units_short)
        externalStatus = if (showBgi)
            "${status[0].externalStatus} $iobString ${status[0].bgi}"
        else
            "${status[0].externalStatus} $iobString"
        binding.status?.text = externalStatus
        binding.status?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
        binding.loop?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
        if (status[0].openApsStatus != -1L) {
            val minutes = ((System.currentTimeMillis() - status[0].openApsStatus) / 1000 / 60).toInt()
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
        //Management of External data 1
        if (enableExt1) {
            binding.patientNameExt1?.text = status[1].patientName
            binding.sgvExt1?.text = singleBg[1].sgvString
            binding.sgvExt1?.visibility = sp.getBoolean(R.string.key_show_bg, true).toVisibilityKeepSpace()
            binding.deltaExt1?.text = if (detailedDelta) singleBg[1].deltaDetailed else singleBg[1].delta
            binding.deltaExt1?.visibility = sp.getBoolean(R.string.key_show_delta, true).toVisibility()
            binding.avgDeltaExt1?.text = if (detailedDelta) singleBg[1].avgDeltaDetailed else singleBg[1].avgDelta
            binding.avgDeltaExt1?.visibility = sp.getBoolean(R.string.key_show_avg_delta, true).toVisibility()
            binding.tempTargetExt1?.text = status[1].tempTarget
            binding.tempTargetExt1?.visibility = sp.getBoolean(R.string.key_show_temp_target, false).toVisibility()
            binding.reservoirExt1?.text = status[1].reservoirString
            binding.reservoirExt1?.visibility = sp.getBoolean(R.string.key_show_reservoir_level, true).toVisibility()
            binding.cob1Ext1?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
            binding.cob1Ext1?.text = getString(R.string.activity_carb)
            binding.cob2Ext1?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
            binding.cob2Ext1?.text = status[1].cob
            binding.iob1Ext1?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
            binding.iob1Ext1?.text = if (detailedIob) status[1].iobSum else getString(R.string.activity_IOB)
            binding.iob2Ext1?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
            binding.iob2Ext1?.text = if (detailedIob) status[1].iobDetail else status[1].iobSum
            binding.timestampExt1?.visibility = sp.getBoolean(R.string.key_show_ago, true).toVisibility()
            binding.timestampExt1?.text = readingAge(id = 1)
            binding.rigBatteryExt1?.visibility = sp.getBoolean(R.string.key_show_rig_battery, false).toVisibility()
            binding.rigBatteryExt1?.text = status[1].rigBattery
            binding.basalRateExt1?.visibility = sp.getBoolean(R.string.key_show_temp_basal, true).toVisibility()
            binding.basalRateExt1?.text = status[1].currentBasal
            binding.bgiExt1?.visibility = showBgi.toVisibility()
            binding.bgiExt1?.text = status[1].bgi
            val iobStringExt1 =
                if (detailedIob) "${status[1].iobSum} ${status[1].iobDetail}"
                else status[1].iobSum + getString(R.string.units_short)
            externalStatusExt1 = if (showBgi)
                "${status[1].externalStatus} $iobStringExt1 ${status[1].bgi}"
            else
                "${status[1].externalStatus} $iobStringExt1"
            binding.statusExt1?.text = externalStatusExt1
            binding.statusExt1?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
            binding.loopExt1?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
            if (status[1].openApsStatus != -1L) {
                val minutes = ((System.currentTimeMillis() - status[1].openApsStatus) / 1000 / 60).toInt()
                binding.loopExt1?.text = "$minutes'"
                if (minutes > 14) {
                    loopLevelExt1 = 0
                    binding.loopExt1?.setBackgroundResource(R.drawable.loop_red_25)
                } else {
                    loopLevelExt1 = 1
                    binding.loopExt1?.setBackgroundResource(R.drawable.loop_green_25)
                }
            } else {
                loopLevelExt1 = -1
                binding.loopExt1?.text = "-"
                binding.loopExt1?.setBackgroundResource(R.drawable.loop_grey_25)
            }
        }
        //Management of External data 2
        if (enableExt2) {
            binding.patientNameExt2?.text = status[2].patientName
            binding.sgvExt2?.text = singleBg[2].sgvString
            binding.sgvExt2?.visibility = sp.getBoolean(R.string.key_show_bg, true).toVisibilityKeepSpace()
            binding.deltaExt2?.text = if (detailedDelta) singleBg[2].deltaDetailed else singleBg[2].delta
            binding.deltaExt2?.visibility = sp.getBoolean(R.string.key_show_delta, true).toVisibility()
            binding.avgDeltaExt2?.text = if (detailedDelta) singleBg[2].avgDeltaDetailed else singleBg[2].avgDelta
            binding.avgDeltaExt2?.visibility = sp.getBoolean(R.string.key_show_avg_delta, true).toVisibility()
            binding.tempTargetExt2?.text = status[2].tempTarget
            binding.tempTargetExt2?.visibility = sp.getBoolean(R.string.key_show_temp_target, false).toVisibility()
            binding.reservoirExt2?.text = status[2].reservoirString
            binding.reservoirExt2?.visibility = sp.getBoolean(R.string.key_show_reservoir_level, true).toVisibility()
            binding.cob1Ext2?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
            binding.cob1Ext2?.text = getString(R.string.activity_carb)
            binding.cob2Ext2?.visibility = sp.getBoolean(R.string.key_show_cob, true).toVisibility()
            binding.cob2Ext2?.text = status[2].cob
            binding.iob1Ext2?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
            binding.iob1Ext2?.text = if (detailedIob) status[2].iobSum else getString(R.string.activity_IOB)
            binding.iob2Ext2?.visibility = sp.getBoolean(R.string.key_show_iob, true).toVisibility()
            binding.iob2Ext2?.text = if (detailedIob) status[2].iobDetail else status[2].iobSum
            binding.timestampExt2?.visibility = sp.getBoolean(R.string.key_show_ago, true).toVisibility()
            binding.timestampExt2?.text = readingAge(id = 2)
            binding.rigBatteryExt2?.visibility = sp.getBoolean(R.string.key_show_rig_battery, false).toVisibility()
            binding.rigBatteryExt2?.text = status[2].rigBattery
            binding.basalRateExt2?.visibility = sp.getBoolean(R.string.key_show_temp_basal, true).toVisibility()
            binding.basalRateExt2?.text = status[2].currentBasal
            binding.bgiExt2?.visibility = showBgi.toVisibility()
            binding.bgiExt2?.text = status[2].bgi
            val iobStringExt2 =
                if (detailedIob) "${status[2].iobSum} ${status[2].iobDetail}"
                else status[2].iobSum + getString(R.string.units_short)
            externalStatusExt2 = if (showBgi)
                "${status[2].externalStatus} $iobStringExt2 ${status[2].bgi}"
            else
                "${status[2].externalStatus} $iobStringExt2"
            binding.statusExt2?.text = externalStatusExt2
            binding.statusExt2?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
            binding.loopExt2?.visibility = sp.getBoolean(R.string.key_show_external_status, true).toVisibility()
            if (status[2].openApsStatus != -1L) {
                val minutes = ((System.currentTimeMillis() - status[2].openApsStatus) / 1000 / 60).toInt()
                binding.loopExt2?.text = "$minutes'"
                if (minutes > 14) {
                    loopLevelExt2 = 0
                    binding.loopExt2?.setBackgroundResource(R.drawable.loop_red_25)
                } else {
                    loopLevelExt2 = 1
                    binding.loopExt2?.setBackgroundResource(R.drawable.loop_green_25)
                }
            } else {
                loopLevelExt2 = -1
                binding.loopExt2?.text = "-"
                binding.loopExt2?.setBackgroundResource(R.drawable.loop_grey_25)
            }
        }
        //************************************************************************
        strikeThroughSgvIfNeeded()
        setColor()
    }

    override fun on24HourFormatChanged(is24HourFormat: Boolean) {
        if (!simpleUi.isEnabled(currentWatchMode)) {
            setDataFields()
        }
        invalidate()
    }

    private fun setDateAndTime() {
        binding.time?.text = if (binding.timePeriod == null) dateUtil.timeString() else dateUtil.hourString() + ":" + dateUtil.minuteString()
        binding.hour?.text = dateUtil.hourString()
        binding.minute?.text = dateUtil.minuteString()
        binding.dateTime?.visibility = sp.getBoolean(R.string.key_show_date, false).toVisibility()
        binding.dayName?.text = dateUtil.dayNameString(dayNameFormat).substringBeforeLast(".")
        binding.day?.text = dateUtil.dayString()
        binding.month?.text = dateUtil.monthString(monthFormat).substringBeforeLast(".")
        binding.timePeriod?.visibility = android.text.format.DateFormat.is24HourFormat(this).not().toVisibility()
        binding.timePeriod?.text = dateUtil.amPm()
        binding.weekNumber?.visibility = sp.getBoolean(R.string.key_show_week_number, false).toVisibility()
        binding.weekNumber?.text = "(" + dateUtil.weekString() + ")"
        if (showSecond)
            setSecond()
    }

    open fun setSecond() {
        binding.time?.text = if (binding.timePeriod == null) dateUtil.timeString() else dateUtil.hourString() + ":" + dateUtil.minuteString() + if (showSecond) ":" + dateUtil.secondString() else ""
        binding.second?.text = dateUtil.secondString()
    }

    open fun updateSecondVisibility() {
        binding.second?.visibility = showSecond.toVisibility()
    }

    fun setColor() {
        dividerMatchesBg = sp.getBoolean(R.string.key_match_divider, false)
        when {
            lowResMode                             -> setColorLowRes()
            sp.getBoolean(R.string.key_dark, true) -> setColorDark()
            else                                   -> setColorBright()
        }
    }

    private fun strikeThroughSgvIfNeeded() {
        binding.sgv?.let { mSgv ->
            if (ageLevel() <= 0 && singleBg[0].timeStamp > 0) mSgv.paintFlags = mSgv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else mSgv.paintFlags = mSgv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        binding.sgvExt1?.let { mSgv ->
            if (ageLevel(id = 1) <= 0 && singleBg[1].timeStamp > 0) mSgv.paintFlags = mSgv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else mSgv.paintFlags = mSgv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        binding.sgvExt2?.let { mSgv ->
            if (ageLevel(id = 2) <= 0 && singleBg[2].timeStamp > 0) mSgv.paintFlags = mSgv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else mSgv.paintFlags = mSgv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    override fun onWatchModeChanged(watchMode: WatchMode) {
        updateSecondVisibility()    // will show second if enabledSecond and Interactive mode, hide in other situation
        setSecond()                 // will remove second from main date and time if not in Interactive mode
        lowResMode = isLowRes(watchMode)
        if (simpleUi.isEnabled(currentWatchMode)) simpleUi.setAntiAlias(currentWatchMode)
        else
            setDataFields()
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
        if (singleBg[0].timeStamp == 0L || minutesSince >= 16 && (minutesSince - 16) % 5 == 0) {
            // Attempt endTime recover missing data
            rxBus.send(EventWearToMobile(ActionResendData("BaseWatchFace:missedReadingAlert")))
        }
    }

    fun setupCharts() {
        if (simpleUi.isEnabled(currentWatchMode)) {
            return
        }
        if (binding.chart != null && graphData.entries.isNotEmpty()) {
            val timeframe = sp.getInt(R.string.key_chart_time_frame, 3)
            val bgGraphBuilder =
                if (lowResMode)
                    BgGraphBuilder(
                        sp, dateUtil, graphData.entries, treatmentData.predictions, treatmentData.temps, treatmentData.basals, treatmentData.boluses, pointSize,
                        midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, carbColor, timeframe
                    )
                else
                    BgGraphBuilder(
                        sp, dateUtil, graphData.entries, treatmentData.predictions, treatmentData.temps, treatmentData.basals, treatmentData.boluses,
                        pointSize, highColor, lowColor, midColor, gridColor, basalBackgroundColor, basalCenterColor, bolusColor, carbColor, timeframe
                    )
            binding.chart?.lineChartData = bgGraphBuilder.lineData()
            binding.chart?.isViewportCalculationEnabled = true
        }
    }

    private fun needUpdate(): Boolean {
        if (mLastSvg == singleBg[0].sgvString && mLastDirection == singleBg[0].sgvString) {
            return false
        }
        mLastSvg = singleBg[0].sgvString
        mLastDirection = singleBg[0].sgvString
        return true
    }
}
