package app.aaps.wear.watchfaces

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.PowerManager
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventUpdateSelectedWatchface
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.ActionResendData
import app.aaps.core.interfaces.rx.weardata.EventData.SingleBg
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.interaction.menus.MainMenuActivity
import app.aaps.wear.watchfaces.utils.WatchFace
import app.aaps.wear.watchfaces.utils.WatchFaceTime
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace
import dagger.android.AndroidInjection
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@SuppressLint("Deprecated")
class CircleWatchface : WatchFace() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var complicationDataRepository: app.aaps.wear.data.ComplicationDataRepository

    private var disposable = CompositeDisposable()
    private val watchfaceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // DataStore as single source of truth - using EventData models directly
    private var complicationData: app.aaps.wear.data.ComplicationData = app.aaps.wear.data.ComplicationData()

    private val singleBg
        get() = arrayOf(
            complicationData.bgData,
            complicationData.bgData1,
            complicationData.bgData2
        )
    private val status
        get() = arrayOf(
            complicationData.statusData,
            complicationData.statusData1,
            complicationData.statusData2
        )
    private val graphData get() = complicationData.graphData

    companion object {

        const val PADDING = 20f
        const val CIRCLE_WIDTH = 10f
        const val BIG_HAND_WIDTH = 16
        const val SMALL_HAND_WIDTH = 8
        const val NEAR = 2 //how near do the hands have endTime be endTime activate overlapping mode
        const val ALWAYS_HIGHLIGHT_SMALL = false
        const val fraction = .5
    }

    //variables for time
    private var angleBig = 0f
    private var angleSMALL = 0f
    private var color = 0
    private val circlePaint = Paint()
    private val removePaint = Paint()
    private lateinit var rect: RectF
    private lateinit var rectDelete: RectF
    private var overlapping = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var bgDataList = ArrayList<SingleBg>()
    private var specW = 0
    private var specH = 0
    private var myLayout: View? = null
    private var mSgv: TextView? = null
    private var sgvTapTime: Long = 0
    private var lastMenuOpenTime: Long = 0


    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        sp.putInt(R.string.key_last_selected_watchface, SelectedWatchFace.CIRCLE.ordinal)
        rxBus.send(EventUpdateSelectedWatchface())
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:CircleWatchface")
        wakeLock.acquire(30000)
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = windowManager.currentWindowMetrics.bounds
        displayWidth = bounds.width()
        displayHeight = bounds.height()
        specW = View.MeasureSpec.makeMeasureSpec(displayWidth, View.MeasureSpec.EXACTLY)
        specH = View.MeasureSpec.makeMeasureSpec(displayHeight, View.MeasureSpec.EXACTLY)

        // Layout inflation deferred to first onDraw to avoid deadlock in headless engine creation
        // prepareDrawTime() also deferred as it depends on display size setup

        // Observe DataStore for automatic updates
        watchfaceScope.launch {
            complicationDataRepository.complicationData.collect { data ->
                complicationData = data
                if (myLayout != null) {  // Only update if layout initialized
                    addToWatchSet()
                    prepareLayout()
                    prepareDrawTime()
                    invalidate()
                }
            }
        }

        disposable += rxBus
            .toObservable(EventData.Preferences::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                if (myLayout != null) {  // Only update if layout initialized
                    prepareDrawTime()
                    prepareLayout()
                    invalidate()
                }
            }

        rxBus.send(EventWearToMobile(ActionResendData("CircleWatchFace::onCreate")))
        wakeLock.release()
    }

    override fun onDestroy() {
        disposable.clear()
        watchfaceScope.cancel()
        super.onDestroy()
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        // Lazy initialization of layout on first render (called on main thread)
        // This avoids deadlock during headless engine creation on background thread
        if (myLayout == null) {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            myLayout = inflater.inflate(R.layout.activity_circle, null)
            prepareLayout()
            prepareDrawTime()
        }

        aapsLogger.debug(LTag.WEAR, "start onDraw")
        canvas.drawColor(backgroundColor)
        drawTime(canvas)
        drawOtherStuff(canvas)
        myLayout?.draw(canvas)
    }

    @Synchronized
    private fun prepareLayout() {
        aapsLogger.debug(LTag.WEAR, "start startPrepareLayout")

        // prepare fields
        mSgv = myLayout?.findViewById(R.id.sgvString)
        if (sp.getBoolean(R.string.key_show_bg, true)) {
            mSgv?.visibility = View.VISIBLE
            mSgv?.text = singleBg[0].sgvString
            mSgv?.setTextColor(textColor)
        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            mSgv?.visibility = View.INVISIBLE
        }
        val detailedIob = sp.getBoolean(R.string.key_show_detailed_iob, false)
        val showBgi = sp.getBoolean(R.string.key_show_bgi, false)
        val iobString =
            if (detailedIob) "${status[0].iobSum} ${status[0].iobDetail}"
            else status[0].iobSum + getString(R.string.units_short)
        val externalStatus = if (showBgi)
            "${status[0].externalStatus} $iobString ${status[0].bgi}"
        else
            "${status[0].externalStatus} $iobString"
        var textView = myLayout?.findViewById<TextView>(R.id.statusString)
        if (sp.getBoolean(R.string.key_show_external_status, true)) {
            textView?.visibility = View.VISIBLE
            textView?.text = externalStatus
            textView?.setTextColor(textColor)
        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            textView?.visibility = View.GONE
        }
        textView = myLayout?.findViewById(R.id.agoString)
        if (sp.getBoolean(R.string.key_show_ago, true)) {
            textView?.visibility = View.VISIBLE
            if (sp.getBoolean(R.string.key_show_big_numbers, false)) {
                textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            } else {
                (myLayout?.findViewById<View>(R.id.agoString) as TextView).setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            }
            textView?.text = minutes
            textView?.setTextColor(textColor)
        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            textView?.visibility = View.INVISIBLE
        }
        textView = myLayout?.findViewById(R.id.deltaString)
        val detailedDelta = sp.getBoolean(R.string.key_show_detailed_delta, false)
        if (sp.getBoolean(R.string.key_show_delta, true)) {
            textView?.visibility = View.VISIBLE
            textView?.text = if (detailedDelta) singleBg[0].deltaDetailed else singleBg[0].delta
            textView?.setTextColor(textColor)
            if (sp.getBoolean(R.string.key_show_big_numbers, false)) {
                textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            } else {
                textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            }
            if (sp.getBoolean(R.string.key_show_avg_delta, true)) {
                textView?.append("  " + if (detailedDelta) singleBg[0].avgDeltaDetailed else singleBg[0].avgDelta)
            }
        } else {
            //Also possible: View.INVISIBLE instead of View.GONE (no layout change)
            textView?.visibility = View.INVISIBLE
        }
        myLayout?.measure(specW, specH)
        myLayout?.layout(0, 0, myLayout?.measuredWidth ?: 0, myLayout?.measuredHeight ?: 0)
    }

    private val minutes: String
        get() {
            var minutes = "--'"
            if (singleBg[0].timeStamp != 0L) {
                minutes = floor((System.currentTimeMillis() - singleBg[0].timeStamp) / 60000.0).toInt().toString() + "'"
            }
            return minutes
        }

    private fun drawTime(canvas: Canvas) {

        //draw circle
        circlePaint.color = color
        circlePaint.strokeWidth = CIRCLE_WIDTH
        canvas.drawArc(rect, 0f, 360f, false, circlePaint)
        //"remove" hands from circle
        removePaint.strokeWidth = CIRCLE_WIDTH * 3
        canvas.drawArc(rectDelete, angleBig, BIG_HAND_WIDTH.toFloat(), false, removePaint)
        canvas.drawArc(rectDelete, angleSMALL, SMALL_HAND_WIDTH.toFloat(), false, removePaint)
        if (overlapping) {
            //add small hand with extra
            circlePaint.strokeWidth = CIRCLE_WIDTH * 2
            circlePaint.color = color
            canvas.drawArc(rect, angleSMALL, SMALL_HAND_WIDTH.toFloat(), false, circlePaint)

            //remove inner part of hands
            removePaint.strokeWidth = CIRCLE_WIDTH
            canvas.drawArc(rect, angleBig, BIG_HAND_WIDTH.toFloat(), false, removePaint)
            canvas.drawArc(rect, angleSMALL, SMALL_HAND_WIDTH.toFloat(), false, removePaint)
        }
    }

    @Synchronized
    private fun prepareDrawTime() {
        aapsLogger.debug(LTag.WEAR, "start prepareDrawTime")
        val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY] % 12
        val minute = Calendar.getInstance()[Calendar.MINUTE]
        angleBig = ((hour + minute / 60f) / 12f * 360 - 90 - BIG_HAND_WIDTH / 2f + 360) % 360
        angleSMALL = (minute / 60f * 360 - 90 - SMALL_HAND_WIDTH / 2f + 360) % 360
        color = 0
        when (singleBg[0].sgvLevel.toInt()) {
            -1 -> color = lowColor
            0  -> color = inRangeColor
            1  -> color = highColor
        }
        circlePaint.shader = null
        circlePaint.style = Paint.Style.STROKE
        circlePaint.strokeWidth = CIRCLE_WIDTH
        circlePaint.isAntiAlias = true
        circlePaint.color = color
        removePaint.style = Paint.Style.STROKE
        removePaint.strokeWidth = CIRCLE_WIDTH
        removePaint.isAntiAlias = true
        removePaint.color = backgroundColor
        rect = RectF(PADDING, PADDING, displayWidth - PADDING, displayHeight - PADDING)
        rectDelete = RectF(PADDING - CIRCLE_WIDTH / 2, PADDING - CIRCLE_WIDTH / 2, displayWidth - PADDING + CIRCLE_WIDTH / 2, displayHeight - PADDING + CIRCLE_WIDTH / 2)
        overlapping = ALWAYS_HIGHLIGHT_SMALL || areOverlapping(angleSMALL, angleSMALL + SMALL_HAND_WIDTH + NEAR, angleBig, angleBig + BIG_HAND_WIDTH + NEAR)
        aapsLogger.debug(LTag.WEAR, "end prepareDrawTime")
    }

    private fun areOverlapping(aBegin: Float, aEnd: Float, bBegin: Float, bEnd: Float): Boolean {
        return bBegin in aBegin..aEnd || aBegin <= bBegin && bEnd > 360 && bEnd % 360 > aBegin || aBegin in bBegin..bEnd || bBegin <= aBegin && aEnd > 360 && aEnd % 360 > bBegin
    }

    override fun onTimeChanged(oldTime: WatchFaceTime, newTime: WatchFaceTime) {
        if (oldTime.hasMinuteChanged(newTime) && myLayout != null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:CircleWatchface_onTimeChanged")
            wakeLock.acquire(30000)
            /*Preparing the layout just on every minute tick:
             *  - hopefully better battery life
             *  - drawback: might update the minutes since last reading up endTime one minute late*/prepareLayout()
            prepareDrawTime()
            invalidate() //redraw the time
            wakeLock.release()
        }
    }

    // defining color for dark and bright
    private val lowColor: Int
        get() = if (sp.getBoolean(R.string.key_dark, true)) Color.argb(255, 255, 120, 120) else Color.argb(255, 255, 80, 80)
    private val inRangeColor: Int
        get() = if (sp.getBoolean(R.string.key_dark, true)) Color.argb(255, 120, 255, 120) else Color.argb(255, 0, 240, 0)
    private val highColor: Int
        get() = if (sp.getBoolean(R.string.key_dark, true)) Color.argb(255, 255, 255, 120) else Color.argb(255, 255, 200, 0)
    private val backgroundColor: Int
        get() = if (sp.getBoolean(R.string.key_dark, true)) Color.BLACK else Color.WHITE
    val textColor: Int
        get() = if (sp.getBoolean(R.string.key_dark, true)) Color.WHITE else Color.BLACK

    private fun drawOtherStuff(canvas: Canvas) {
        aapsLogger.debug(LTag.WEAR, "start onDrawOtherStuff. bgDataList.size(): " + bgDataList.size)
        if (sp.getBoolean(R.string.key_show_ring_history, false)) {
            //Perfect low and High indicators
            if (bgDataList.isNotEmpty()) {
                addIndicator(canvas, 100f, Color.LTGRAY)
                addIndicator(canvas, bgDataList.iterator().next().low.toFloat(), lowColor)
                addIndicator(canvas, bgDataList.iterator().next().high.toFloat(), highColor)
                if (sp.getBoolean("softRingHistory", true)) {
                    for (data in bgDataList) {
                        addReadingSoft(canvas, data)
                    }
                } else {
                    for (data in bgDataList) {
                        addReading(canvas, data)
                    }
                }
            }
        }
    }

    @Synchronized
    fun addToWatchSet() {
        bgDataList.clear()
        if (!sp.getBoolean(R.string.key_show_ring_history, false)) return
        val threshold = (System.currentTimeMillis() - 1000L * 60 * 30).toDouble() // 30 min
        for (entry in graphData.entries) if (entry.timeStamp >= threshold) bgDataList.add(entry)
        aapsLogger.debug(LTag.WEAR, "addToWatchSet size=" + bgDataList.size)
    }

    private fun darken(color: Int): Int {
        var red = Color.red(color)
        var green = Color.green(color)
        var blue = Color.blue(color)
        red = darkenColor(red)
        green = darkenColor(green)
        blue = darkenColor(blue)
        val alpha = Color.alpha(color)
        return Color.argb(alpha, red, green, blue)
    }

    private fun darkenColor(color: Int): Int {
        return max(color - color * fraction, 0.0).toInt()
    }

    private fun addArch(canvas: Canvas, offset: Float, color: Int, size: Float) {
        val paint = Paint()
        paint.color = color
        val rectTemp =
            RectF(PADDING + offset - CIRCLE_WIDTH / 2, PADDING + offset - CIRCLE_WIDTH / 2, displayWidth - PADDING - offset + CIRCLE_WIDTH / 2, displayHeight - PADDING - offset + CIRCLE_WIDTH / 2)
        canvas.drawArc(rectTemp, 270f, size, true, paint)
    }

    private fun addArch(canvas: Canvas, start: Float, offset: Float, color: Int, size: Float) {
        val paint = Paint()
        paint.color = color
        val rectTemp =
            RectF(PADDING + offset - CIRCLE_WIDTH / 2, PADDING + offset - CIRCLE_WIDTH / 2, displayWidth - PADDING - offset + CIRCLE_WIDTH / 2, displayHeight - PADDING - offset + CIRCLE_WIDTH / 2)
        canvas.drawArc(rectTemp, start + 270, size, true, paint)
    }

    private fun addIndicator(canvas: Canvas, bg: Float, color: Int) {
        var convertedBg: Float = bgToAngle(bg)
        convertedBg += 270f
        val paint = Paint()
        paint.color = color
        val offset = 9f
        val rectTemp =
            RectF(PADDING + offset - CIRCLE_WIDTH / 2, PADDING + offset - CIRCLE_WIDTH / 2, displayWidth - PADDING - offset + CIRCLE_WIDTH / 2, displayHeight - PADDING - offset + CIRCLE_WIDTH / 2)
        canvas.drawArc(rectTemp, convertedBg, 2f, true, paint)
    }

    private fun bgToAngle(bg: Float): Float {
        return if (bg > 100) {
            (bg - 100f) / 300f * 225f + 135
        } else {
            bg / 100 * 135
        }
    }

    private fun addReadingSoft(canvas: Canvas, entry: SingleBg) {
        aapsLogger.debug(LTag.WEAR, "addReadingSoft")
        var color = Color.LTGRAY
        if (sp.getBoolean(R.string.key_dark, true)) {
            color = Color.DKGRAY
        }
        val offsetMultiplier = (displayWidth / 2f - PADDING) / 12f
        val offset = max(1.0, ceil((System.currentTimeMillis() - entry.timeStamp) / (1000 * 60 * 5.0))).toFloat()
        val size: Double = bgToAngle(entry.sgv.toFloat()).toDouble()
        addArch(canvas, offset * offsetMultiplier + 10, color, size.toFloat())
        addArch(canvas, size.toFloat(), offset * offsetMultiplier + 10, backgroundColor, (360 - size).toFloat())
        addArch(canvas, (offset + .8f) * offsetMultiplier + 10, backgroundColor, 360f)
    }

    private fun addReading(canvas: Canvas, entry: SingleBg) {
        aapsLogger.debug(LTag.WEAR, "addReading")
        var color = Color.LTGRAY
        var indicatorColor = Color.DKGRAY
        if (sp.getBoolean(R.string.key_dark, true)) {
            color = Color.DKGRAY
            indicatorColor = Color.LTGRAY
        }
        var barColor = Color.GRAY
        if (entry.sgv >= entry.high) {
            indicatorColor = highColor
            barColor = darken(highColor)
        } else if (entry.sgv <= entry.low) {
            indicatorColor = lowColor
            barColor = darken(lowColor)
        }
        val offsetMultiplier = (displayWidth / 2f - PADDING) / 12f
        val offset = max(1.0, ceil((System.currentTimeMillis() - entry.timeStamp) / (1000 * 60 * 5.0))).toFloat()
        val size: Double = bgToAngle(entry.sgv.toFloat()).toDouble()
        addArch(canvas, offset * offsetMultiplier + 11, barColor, size.toFloat() - 2) // Dark Color Bar
        addArch(canvas, size.toFloat() - 2, offset * offsetMultiplier + 11, indicatorColor, 2f) // Indicator at end of bar
        addArch(canvas, size.toFloat(), offset * offsetMultiplier + 11, color, (360f - size).toFloat()) // Dark fill
        addArch(canvas, (offset + .8f) * offsetMultiplier + 11, backgroundColor, 360f)
    }

    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
        // Only respond to actual taps (tapType=2), ignore touch-down (tapType=0) and other events
        if (tapType != 2) {
            return
        }

        mSgv?.let { mSgv ->
            if (x >= mSgv.left && x <= mSgv.right && y >= mSgv.top && y <= mSgv.bottom) {
                if (eventTime - sgvTapTime < 800 && sgvTapTime != 0L) {
                    if (eventTime - lastMenuOpenTime > 2000) {
                        lastMenuOpenTime = eventTime
                        val intent = Intent(this, MainMenuActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                    }
                    sgvTapTime = 0
                } else {
                    sgvTapTime = eventTime
                }
            }
        }
    }
}
