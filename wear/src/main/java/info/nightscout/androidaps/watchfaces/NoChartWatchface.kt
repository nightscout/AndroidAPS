@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.PowerManager
import android.support.wearable.watchface.WatchFaceStyle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ustwo.clockwise.common.WatchFaceTime
import com.ustwo.clockwise.common.WatchMode
import com.ustwo.clockwise.common.WatchShape
import com.ustwo.clockwise.wearable.WatchFace
import dagger.android.AndroidInjection
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventWearToMobile
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interaction.menus.MainMenuActivity
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import info.nightscout.shared.weardata.EventData.ActionResendData
import info.nightscout.shared.weardata.EventData.SingleBg
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.floor

class NoChartWatchface : WatchFace() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil

    private var disposable = CompositeDisposable()

    private var singleBg = SingleBg(0, "---", "-", "--", "--", "--", 0, 0.0, 0.0, 0.0, 0)
    private var status = EventData.Status("no status", "IOB", "-.--", false, "--g", "-.--U/h", "--", "--", -1, "--", false, 1)

    private var mTime: TextView? = null
    private var mSgv: TextView? = null
    private var mTimestamp: TextView? = null
    private var mDelta: TextView? = null
    private var mAvgDelta: TextView? = null
    private var mRelativeLayout: RelativeLayout? = null
    private var sgvLevel: Long = 0
    private var ageLevel = 1
    private var lowResMode = false
    private var layoutSet = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var layoutView: View? = null
    private val displaySize = Point()
    private var specW = 0
    private var specH = 0
    private var statusView: TextView? = null
    private var sgvTapTime = 0L

    @SuppressLint("InflateParams")
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        val display = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
        display.getSize(displaySize)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NOChart")
        specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY)
        specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY)
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val metrics = resources.displayMetrics
        layoutView = if (metrics.widthPixels < SCREEN_SIZE_SMALL || metrics.heightPixels < SCREEN_SIZE_SMALL) {
            inflater.inflate(R.layout.activity_nochart_small, null)
        } else {
            inflater.inflate(R.layout.activity_nochart, null)
        }
        disposable += rxBus
            .toObservable(SingleBg::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { event: SingleBg ->
                aapsLogger.debug(LTag.WEAR, "SingleBg received")
                singleBg = event
                mSgv?.let { sgv ->
                    sgv.text = singleBg.sgvString
                    if (ageLevel() <= 0) sgv.paintFlags = sgv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    else sgv.paintFlags = sgv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
                mTime?.text = dateUtil.timeString()
                mDelta?.text = singleBg.delta
                mAvgDelta?.text = singleBg.avgDelta
            }
        disposable += rxBus
            .toObservable(EventData.Status::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { event: EventData.Status ->
                // this event is received as last batch of data
                aapsLogger.debug(LTag.WEAR, "Status received")
                status = event
                showAgeAndStatus()
                mRelativeLayout?.measure(specW, specH)
                mRelativeLayout?.layout(0, 0, mRelativeLayout?.measuredWidth ?: 0, mRelativeLayout?.measuredHeight ?: 0)
                invalidate()
                setColor()
            }
        disposable += rxBus
            .toObservable(EventData.Preferences::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                setColor()
                if (layoutSet) {
                    showAgeAndStatus()
                    mRelativeLayout?.measure(specW, specH)
                    mRelativeLayout?.layout(0, 0, mRelativeLayout?.measuredWidth ?: 0, mRelativeLayout?.measuredHeight ?: 0)
                }
                invalidate()
            }
        performViewSetup()
    }

    override fun onLayout(shape: WatchShape, screenBounds: Rect, screenInsets: WindowInsets) {
        super.onLayout(shape, screenBounds, screenInsets)
        layoutView?.onApplyWindowInsets(screenInsets)
    }

    private fun performViewSetup() {
        mTime = layoutView?.findViewById(R.id.watch_time)
        mSgv = layoutView?.findViewById(R.id.sgv)
        mTimestamp = layoutView?.findViewById(R.id.timestamp)
        mDelta = layoutView?.findViewById(R.id.delta)
        mAvgDelta = layoutView?.findViewById(R.id.avgdelta)
        mRelativeLayout = layoutView?.findViewById(R.id.main_layout)
        statusView = layoutView?.findViewById(R.id.aps_status)
        layoutSet = true
        showAgeAndStatus()
        mRelativeLayout?.measure(specW, specH)
        mRelativeLayout?.layout(0, 0, mRelativeLayout?.measuredWidth ?: 0, mRelativeLayout?.measuredHeight ?: 0)
        rxBus.send(EventWearToMobile(ActionResendData("NOChart:performViewSetup")))
    }

    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
        mSgv?.let { mSgv ->
            val extra = (mSgv.right - mSgv.left) / 2
            if (tapType == TAP_TYPE_TAP && x + extra >= mSgv.left && x - extra <= mSgv.right && y >= mSgv.top && y <= mSgv.bottom) {
                if (eventTime - sgvTapTime < 800) {
                    startActivity(Intent(this, MainMenuActivity::class.java).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                sgvTapTime = eventTime
            }
        }
    }

    override fun onWatchModeChanged(watchMode: WatchMode) {
        if (lowResMode xor isLowRes(watchMode)) { //if there was a change in lowResMode
            lowResMode = isLowRes(watchMode)
            setColor()
        } else if (!sp.getBoolean("dark", true)) {
            //in bright mode: different colours if active:
            setColor()
        }
    }

    private fun isLowRes(watchMode: WatchMode): Boolean {
        return watchMode == WatchMode.LOW_BIT || watchMode == WatchMode.LOW_BIT_BURN_IN
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build()
    }

    private fun ageLevel(): Int = if (timeSince() <= 1000 * 60 * 12) 1 else 0

    fun timeSince(): Double = (System.currentTimeMillis() - singleBg.timeStamp).toDouble()

    private fun readingAge(): String =
        if (singleBg.timeStamp == 0L) "--'"
        else "${floor(timeSince() / (1000 * 60)).toInt()}'"


    override fun onDestroy() {
        disposable.clear()
        super.onDestroy()
    }

    override fun onDraw(canvas: Canvas) {
        if (layoutSet) {
            mRelativeLayout?.draw(canvas)
        }
    }

    override fun onTimeChanged(oldTime: WatchFaceTime, newTime: WatchFaceTime) {
        if (layoutSet && (newTime.hasHourChanged(oldTime) || newTime.hasMinuteChanged(oldTime))) {
            mTime?.text = dateUtil.timeString()
            showAgeAndStatus()
            mSgv?.let { sgv ->
                if (ageLevel() <= 0) {
                    sgv.paintFlags = sgv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    sgv.paintFlags = sgv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }
            missedReadingAlert()
            mRelativeLayout?.measure(specW, specH)
            mRelativeLayout?.layout(0, 0, mRelativeLayout?.measuredWidth ?: 0, mRelativeLayout?.measuredHeight ?: 0)
        }
    }

    private fun showAgeAndStatus() {
        mTimestamp?.text = readingAge()
        mAvgDelta?.visibility = sp.getBoolean(R.string.key_show_avg_delta, true).toVisibility()
        statusView?.text = status.externalStatus
        statusView?.visibility = View.VISIBLE
    }

    private fun setColor() {
        when {
            lowResMode                  -> setColorLowRes()
            sp.getBoolean("dark", true) -> setColorDark()
            else                        -> setColorBright()
        }
    }

    private fun setColorLowRes() {
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        statusView?.setTextColor(ContextCompat.getColor(this, R.color.dark_statusView))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
    }

    private fun setColorDark() {
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        statusView?.setTextColor(ContextCompat.getColor(this, R.color.dark_statusView))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        when (sgvLevel) {
            1L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
                mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
            }

            0L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
                mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
            }

            -1L -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
                mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
            }
        }
        if (ageLevel == 1) {
            mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
        } else {
            mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        }
    }

    private fun setColorBright() {
        if (currentWatchMode == WatchMode.INTERACTIVE) {
            mTime?.setTextColor(ContextCompat.getColor(this, R.color.light_bigchart_time))
            statusView?.setTextColor(ContextCompat.getColor(this, R.color.light_bigchart_status))
            mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            when (sgvLevel) {
                1L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                    mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                }

                0L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                    mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                }

                -1L -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                    mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                }
            }
            if (ageLevel == 1) {
                mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.light_mTimestamp1))
            } else {
                mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.light_mTimestamp))
            }
        } else {
            setColorDark()
        }
    }

    private fun missedReadingAlert() {
        val minutesSince = floor(timeSince() / (1000 * 60)).toInt()
        if (minutesSince >= 16 && (minutesSince - 16) % 5 == 0) {
            // attempt endTime recover missing data
            rxBus.send(EventWearToMobile(ActionResendData("NOChart::missedReadingAlert")))
        }
    }

    companion object {

        const val SCREEN_SIZE_SMALL = 280
    }
}