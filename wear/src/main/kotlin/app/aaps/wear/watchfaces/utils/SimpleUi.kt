package app.aaps.wear.watchfaces.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.view.WindowManager
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.R
import javax.inject.Inject

class SimpleUi @Inject constructor(
    private val context: Context,
    private val sp: SP,
    private val dateUtil: DateUtil
) {

    private var batteryReceiver: BroadcastReceiver? = null
    private var mBackgroundPaint = Paint()
    private lateinit var mTimePaint: Paint
    private lateinit var mSvgPaint: Paint
    private lateinit var mDirectionPaint: Paint
    private var mYOffset = 0f
    private val colorDarkHigh = ContextCompat.getColor(context, R.color.dark_highColor)
    private var colorDarkMid = ContextCompat.getColor(context, R.color.dark_midColor)
    private var colorDarkLow = ContextCompat.getColor(context, R.color.dark_lowColor)
    private var displayWidth = 0
    private var displayHeight = 0
    private lateinit var callback: () -> Unit

    fun onCreate(callback: () -> Unit) {
        this.callback = callback
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = windowManager.currentWindowMetrics.bounds
        displayWidth = bounds.width()
        displayHeight = bounds.height()
        setupBatteryReceiver()
        setupUi()
    }

    fun updatePreferences() {
        setupBatteryReceiver()
    }

    fun setAntiAlias(currentWatchMode: WatchMode) {
        val antiAlias = currentWatchMode == WatchMode.AMBIENT
        mSvgPaint.isAntiAlias = antiAlias
        mDirectionPaint.isAntiAlias = antiAlias
        mTimePaint.isAntiAlias = antiAlias
    }

    fun isEnabled(currentWatchMode: WatchMode): Boolean {
        val simplify = sp.getString(R.string.key_simplify_ui, "off")
        return if (simplify == "off") false
        else if ((simplify == "ambient" || simplify == "ambient_charging") && currentWatchMode == WatchMode.AMBIENT) true
        else (simplify == "charging" || simplify == "ambient_charging") && isCharging
    }

    fun onDraw(canvas: Canvas, singleBg: EventData.SingleBg) {
        canvas.drawRect(0f, 0f, displayWidth.toFloat(), displayHeight.toFloat(), mBackgroundPaint)
        val xHalf = displayWidth / 2f
        val yThird = displayHeight / 3f

        mSvgPaint.isStrikeThruText = isOutdated(singleBg)
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

    fun onDestroy() {
        batteryReceiver?.let {
            context.unregisterReceiver(it)
            batteryReceiver = null
        }
    }

    private fun isOutdated(singleBg: EventData.SingleBg): Boolean =
        singleBg.timeStamp > 0 && (System.currentTimeMillis() - singleBg.timeStamp) > 1000 * 60 * 12

    private fun getBgColour(level: Long): Int =
        when (level) {
            1L   -> colorDarkHigh
            0L   -> colorDarkMid
            else -> colorDarkLow
        }

    private val isCharging: Boolean
        get() {
            val mBatteryStatus = context.registerReceiver(null, iFilter)
            val status = mBatteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }

    private fun setupUi() {
        val black = ContextCompat.getColor(context, R.color.black)
        mBackgroundPaint.color = black
        val white = ContextCompat.getColor(context, R.color.white)
        val resources = context.resources
        val textSizeSvg = resources.getDimension(R.dimen.simple_ui_svg_text_size)
        val textSizeDirection = resources.getDimension(R.dimen.simple_ui_direction_text_size)
        val textSizeTime = resources.getDimension(R.dimen.simple_ui_time_text_size)
        mYOffset = resources.getDimension(R.dimen.simple_ui_y_offset)
        mSvgPaint = createTextPaint(NORMAL_TYPEFACE, white, textSizeSvg)
        mDirectionPaint = createTextPaint(BOLD_TYPEFACE, white, textSizeDirection)
        mTimePaint = createTextPaint(NORMAL_TYPEFACE, white, textSizeTime)
    }

    private fun setupBatteryReceiver() {
        val setting = sp.getString(R.string.key_simplify_ui, "off")
        if ((setting == "charging" || setting == "ambient_charging") && batteryReceiver == null) {
            val intentBatteryFilter = IntentFilter()
            intentBatteryFilter.addAction(BatteryManager.ACTION_CHARGING)
            intentBatteryFilter.addAction(BatteryManager.ACTION_DISCHARGING)
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    callback()
                }
            }
            context.registerReceiver(batteryReceiver, intentBatteryFilter)
        } else {
            batteryReceiver?.let {
                context.unregisterReceiver(it)
                batteryReceiver = null
            }
        }
    }

    private fun createTextPaint(typeface: Typeface, colour: Int, textSize: Float): Paint {
        val paint = Paint()
        paint.color = colour
        paint.typeface = typeface
        paint.isAntiAlias = true
        paint.textSize = textSize
        return paint
    }

    companion object {

        var iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val NORMAL_TYPEFACE: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val BOLD_TYPEFACE: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
}
