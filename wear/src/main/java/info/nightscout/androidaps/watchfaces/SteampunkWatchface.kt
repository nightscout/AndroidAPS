@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.annotation.SuppressLint
import android.content.Intent
import android.support.wearable.watchface.WatchFaceStyle
import android.view.LayoutInflater
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.menus.MainMenuActivity
import info.nightscout.shared.SafeParse.stringToFloat
import org.joda.time.TimeOfDay

/**
 * Created by andrew-warrington on 01/12/2017.
 * Refactored by MilosKozak on 23/04/2022
 */
class SteampunkWatchface : BaseWatchFace() {

    private var chartTapTime: Long = 0
    private var mainMenuTapTime: Long = 0
    private var lastEndDegrees = 0f
    private var deltaRotationAngle = 0f

    @SuppressLint("InflateParams")
    override fun onCreate() {
        forceSquareCanvas = true
        super.onCreate()
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        layoutView = inflater.inflate(R.layout.activity_steampunk, null)
        performViewSetup()
    }

    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
        mChartTap?.let { mChartTap ->
            if (tapType == TAP_TYPE_TAP && x >= mChartTap.left && x <= mChartTap.right && y >= mChartTap.top && y <= mChartTap.bottom) {
                if (eventTime - chartTapTime < 800) {
                    changeChartTimeframe()
                }
                chartTapTime = eventTime
                return
            }
        }
        mMainMenuTap?.let { mMainMenuTap ->
            if (tapType == TAP_TYPE_TAP && x >= mMainMenuTap.left && x <= mMainMenuTap.right && y >= mMainMenuTap.top && y <= mMainMenuTap.bottom) {
                if (eventTime - mainMenuTapTime < 800) {
                    startActivity(Intent(this, MainMenuActivity::class.java).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
                mainMenuTapTime = eventTime
                return
            }
        }
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build()
    }

    override fun setColorDark() {
        if (ageLevel() <= 0 && singleBg.timeStamp != 0L) {
            mLinearLayout2?.setBackgroundResource(R.drawable.redline)
            mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.red_600))
        } else {
            mLinearLayout2?.setBackgroundResource(0)
            mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.black_86p))
        }
        if (loopLevel == 0) {
            mLoop?.setTextColor(ContextCompat.getColor(this, R.color.red_600))
        } else {
            mLoop?.setTextColor(ContextCompat.getColor(this, R.color.black_86p))
        }
        if (singleBg.sgvString != "---") {
            var rotationAngle = 0f //by default, show ? on the dial (? is at 0 degrees on the dial)
            if (singleBg.glucoseUnits != "-") {

                //ensure the glucose dial is the correct units
                if (singleBg.glucoseUnits == "mmol") {
                    mGlucoseDial?.setImageResource(R.drawable.steampunk_dial_mmol)
                } else {
                    mGlucoseDial?.setImageResource(R.drawable.steampunk_dial_mgdl)
                }

                //convert the Sgv to degrees of rotation
                rotationAngle = if (singleBg.glucoseUnits == "mmol") {
                    stringToFloat(singleBg.sgvString) * 18f //convert to
                    // mg/dL, which is equivalent to degrees
                } else {
                    stringToFloat(singleBg.sgvString) //if glucose a value is received, use it to determine the amount of rotation of the dial.
                }
            }
            if (rotationAngle > 330) rotationAngle = 330f //if the glucose value is higher than 330 then show "HIGH" on the dial. ("HIGH" is at 330 degrees on the dial)
            if (rotationAngle != 0f && rotationAngle < 30) rotationAngle = 30f //if the glucose value is lower than 30 show "LOW" on the dial. ("LOW" is at 30 degrees on the dial)
            if (lastEndDegrees == 0f) lastEndDegrees = rotationAngle

            //rotate glucose dial
            val rotate = RotateAnimation(
                lastEndDegrees, rotationAngle - lastEndDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            )
            rotate.fillAfter = true
            rotate.interpolator = LinearInterpolator()
            rotate.duration = 1
            mGlucoseDial?.startAnimation(rotate)
            lastEndDegrees = rotationAngle //store the final angle as a starting point for the next rotation.
        }

        //set the delta gauge and rotate the delta pointer
        var deltaIsNegative = 1f //by default go clockwise
        if (singleBg.avgDelta != "--") {      //if a legitimate delta value is
            // received,
            // then...
            if (singleBg.avgDelta[0] == '-') deltaIsNegative = -1f //if the delta is negative, go counter-clockwise
            val absAvgDelta = stringToFloat(singleBg.avgDelta.substring(1)) //get rid of the sign so it can be converted to float.
            var autoGranularity = "0" //auto-granularity off
            //ensure the delta gauge is the right units and granularity
            if (singleBg.glucoseUnits != "-") {
                if (singleBg.glucoseUnits == "mmol") {
                    if (sp.getString("delta_granularity", "2") == "4") {  //Auto granularity
                        autoGranularity = "1" // low (init)
                        if (absAvgDelta < 0.3) {
                            autoGranularity = "3" // high if below 0.3 mmol/l
                        } else if (absAvgDelta < 0.5) {
                            autoGranularity = "2" // medium if below 0.5 mmol/l
                        }
                    }
                    if (sp.getString("delta_granularity", "2") == "1" || autoGranularity == "1") {  //low
                        mLinearLayout?.setBackgroundResource(R.drawable.steampunk_gauge_mmol_10)
                        deltaRotationAngle = absAvgDelta * 30f
                    }
                    if (sp.getString("delta_granularity", "2") == "2" || autoGranularity == "2") {  //medium
                        mLinearLayout?.setBackgroundResource(R.drawable.steampunk_gauge_mmol_05)
                        deltaRotationAngle = absAvgDelta * 60f
                    }
                    if (sp.getString("delta_granularity", "2") == "3" || autoGranularity == "3") {  //high
                        mLinearLayout?.setBackgroundResource(R.drawable.steampunk_gauge_mmol_03)
                        deltaRotationAngle = absAvgDelta * 100f
                    }
                } else {
                    if (sp.getString("delta_granularity", "2") == "4") {  //Auto granularity
                        autoGranularity = "1" // low (init)
                        if (absAvgDelta < 5) {
                            autoGranularity = "3" // high if below 5 mg/dl
                        } else if (absAvgDelta < 10) {
                            autoGranularity = "2" // medium if below 10 mg/dl
                        }
                    }
                    if (sp.getString("delta_granularity", "2") == "1" || autoGranularity == "1") {  //low
                        mLinearLayout?.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_20)
                        deltaRotationAngle = absAvgDelta * 1.5f
                    }
                    if (sp.getString("delta_granularity", "2") == "2" || autoGranularity == "2") {  //medium
                        mLinearLayout?.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_10)
                        deltaRotationAngle = absAvgDelta * 3f
                    }
                    if (sp.getString("delta_granularity", "2") == "3" || autoGranularity == "3") {  //high
                        mLinearLayout?.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_5)
                        deltaRotationAngle = absAvgDelta * 6f
                    }
                }
            }
            if (deltaRotationAngle > 40) deltaRotationAngle = 40f
            mDeltaGauge?.rotation = deltaRotationAngle * deltaIsNegative
        }

        //rotate the minute hand.
        mMinuteHand?.rotation = TimeOfDay().minuteOfHour * 6f

        //rotate the hour hand.
        mHourHand?.rotation = TimeOfDay().hourOfDay * 30f + TimeOfDay().minuteOfHour * 0.5f
        setTextSizes()
        mLoop?.setBackgroundResource(0)
        if (chart != null) {
            highColor = ContextCompat.getColor(this, R.color.black)
            lowColor = ContextCompat.getColor(this, R.color.black)
            midColor = ContextCompat.getColor(this, R.color.black)
            gridColor = ContextCompat.getColor(this, R.color.grey_steampunk)
            basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
            basalCenterColor = ContextCompat.getColor(this, R.color.basal_dark)
            pointSize = if (sp.getInt(R.string.key_chart_time_frame, 3) < 3) {
                2
            } else {
                1
            }
            setupCharts()
        }
        invalidate()
    }

    override fun setColorLowRes() {
        setColorDark()
    }

    override fun setColorBright() {
        setColorDark()
    }

    private fun setTextSizes() {
        var fontSmall = 10f
        var fontMedium = 11f
        var fontLarge = 12f
        if (bIsRound) {
            fontSmall = 11f
            fontMedium = 12f
            fontLarge = 13f
        }

        //top row. large font unless text too big (i.e. detailedIOB)
        mCOB2?.textSize = fontLarge
        mBasalRate?.textSize = fontLarge
        if (status.iobDetail.length < 7) {
            mIOB2?.textSize = fontLarge
        } else {
            mIOB2?.textSize = fontSmall
        }

        //bottom row. font medium unless text too long (i.e. longer than 9' timestamp)
        mLoop?.let { mLoop ->
            mTimestamp?.let { mTimestamp ->
                if (mTimestamp.text.length < 3 || mLoop.text.length < 3) {     //always resize these fields together, for symmetry.
                    mTimestamp.textSize = fontMedium
                    mLoop.textSize = fontMedium
                } else {
                    mTimestamp.textSize = fontSmall
                    mLoop.textSize = fontSmall
                }
            }
        }

        //if both batteries are shown, make them smaller.
        if (sp.getBoolean("show_uploader_battery", true) && sp.getBoolean("show_rig_battery", false)) {
            mUploaderBattery?.textSize = fontSmall
            mRigBattery?.textSize = fontSmall
        } else {
            mUploaderBattery?.textSize = fontMedium
            mRigBattery?.textSize = fontMedium
        }
    }

    private fun changeChartTimeframe() {
        var timeframe = sp.getInt(R.string.key_chart_time_frame, 3)
        timeframe = timeframe % 5 + 1
        pointSize = if (timeframe < 3) {
            2
        } else {
            1
        }
        setupCharts()
        sp.putInt(R.string.key_chart_time_frame, timeframe)
    }
}