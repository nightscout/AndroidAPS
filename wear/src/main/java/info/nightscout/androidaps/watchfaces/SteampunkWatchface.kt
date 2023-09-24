@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.view.LayoutInflater
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import app.aaps.core.interfaces.utils.SafeParse.stringToFloat
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivitySteampunkBinding
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace
import org.joda.time.TimeOfDay

/**
 * Created by andrew-warrington on 01/12/2017.
 * Refactored by MilosKozak on 23/04/2022
 */
class SteampunkWatchface : BaseWatchFace() {

    private var lastEndDegrees = 0f
    private var deltaRotationAngle = 0f
    private lateinit var binding: ActivitySteampunkBinding

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivitySteampunkBinding.inflate(inflater)
        return binding
    }

    override fun onCreate() {
        forceSquareCanvas = true
        super.onCreate()
    }

    override fun setColorDark() {
        if (ageLevel() <= 0 && singleBg.timeStamp != 0L) {
            binding.tertiaryLayout.setBackgroundResource(R.drawable.redline)
            binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.red_600))
        } else {
            binding.tertiaryLayout.setBackgroundResource(0)
            binding.timestamp.setTextColor(ContextCompat.getColor(this, android.support.wearable.R.color.black_86p))
        }
        binding.loop.setTextColor(ContextCompat.getColor(this, if (loopLevel == 0) R.color.red_600 else android.support.wearable.R.color.black_86p))
        if (singleBg.sgvString != "---") {
            var rotationAngle = 0f // by default, show ? on the dial (? is at 0 degrees on the dial)
            if (singleBg.glucoseUnits != "-") {

                // ensure the glucose dial is the correct units
                binding.glucoseDial.setImageResource(if (singleBg.glucoseUnits == "mmol") R.drawable.steampunk_dial_mmol else R.drawable.steampunk_dial_mgdl)

                // convert the Sgv to degrees of rotation
                rotationAngle =
                    if (singleBg.glucoseUnits == "mmol") stringToFloat(singleBg.sgvString) * 18f //convert to mg/dL, which is equivalent to degrees
                    else stringToFloat(singleBg.sgvString) // if glucose a value is received, use it to determine the amount of rotation of the dial.
            }
            if (rotationAngle > 330) rotationAngle = 330f // if the glucose value is higher than 330 then show "HIGH" on the dial. ("HIGH" is at 330 degrees on the dial)
            if (rotationAngle != 0f && rotationAngle < 30) rotationAngle = 30f // if the glucose value is lower than 30 show "LOW" on the dial. ("LOW" is at 30 degrees on the dial)
            if (lastEndDegrees == 0f) lastEndDegrees = rotationAngle

            // rotate glucose dial
            val rotate = RotateAnimation(lastEndDegrees, rotationAngle - lastEndDegrees, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
                fillAfter = true
                interpolator = LinearInterpolator()
                duration = 1
            }
            binding.glucoseDial.startAnimation(rotate)
            lastEndDegrees = rotationAngle //store the final angle as a starting point for the next rotation.
        }

        // set the delta gauge and rotate the delta pointer
        var deltaIsNegative = 1f // by default go clockwise
        if (singleBg.avgDelta != "--") {      // if a legitimate delta value is
            // received,
            // then...
            if (singleBg.avgDelta[0] == '-') deltaIsNegative = -1f //if the delta is negative, go counter-clockwise
            val absAvgDelta = stringToFloat(singleBg.avgDelta.substring(1)) //get rid of the sign so it can be converted to float.
            var autoGranularity = "0" //auto-granularity off
            // ensure the delta gauge is the right units and granularity
            if (singleBg.glucoseUnits != "-") {
                if (singleBg.glucoseUnits == "mmol") {
                    if (sp.getString("delta_granularity", "2") == "4") {  //Auto granularity
                        autoGranularity =
                            when {
                                absAvgDelta < 0.3 -> "3" // high if below 0.3 mmol/l
                                absAvgDelta < 0.5 -> "2" // medium if below 0.5 mmol/l
                                else              -> "1" // low (init)
                            }
                    }
                    if (sp.getString("delta_granularity", "2") == "1" || autoGranularity == "1") {  //low
                        binding.secondaryLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_10)
                        deltaRotationAngle = absAvgDelta * 30f
                    }
                    if (sp.getString("delta_granularity", "2") == "2" || autoGranularity == "2") {  //medium
                        binding.secondaryLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_05)
                        deltaRotationAngle = absAvgDelta * 60f
                    }
                    if (sp.getString("delta_granularity", "2") == "3" || autoGranularity == "3") {  //high
                        binding.secondaryLayout.setBackgroundResource(R.drawable.steampunk_gauge_mmol_03)
                        deltaRotationAngle = absAvgDelta * 100f
                    }
                } else {
                    if (sp.getString("delta_granularity", "2") == "4") {  //Auto granularity
                        autoGranularity =
                            when {
                                absAvgDelta < 5  -> "3" // high if below 5 mg/dl
                                absAvgDelta < 10 -> "2" // medium if below 10 mg/dl
                                else             -> "1" // low (init)
                            }
                    }
                    if (sp.getString("delta_granularity", "2") == "1" || autoGranularity == "1") {  //low
                        binding.secondaryLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_20)
                        deltaRotationAngle = absAvgDelta * 1.5f
                    }
                    if (sp.getString("delta_granularity", "2") == "2" || autoGranularity == "2") {  //medium
                        binding.secondaryLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_10)
                        deltaRotationAngle = absAvgDelta * 3f
                    }
                    if (sp.getString("delta_granularity", "2") == "3" || autoGranularity == "3") {  //high
                        binding.secondaryLayout.setBackgroundResource(R.drawable.steampunk_gauge_mgdl_5)
                        deltaRotationAngle = absAvgDelta * 6f
                    }
                }
            }
            if (deltaRotationAngle > 40) deltaRotationAngle = 40f
            binding.deltaPointer.rotation = deltaRotationAngle * deltaIsNegative
        }

        // rotate the minute hand.
        binding.minuteHand.rotation = TimeOfDay().minuteOfHour * 6f

        // rotate the hour hand.
        binding.hourHand.rotation = TimeOfDay().hourOfDay * 30f + TimeOfDay().minuteOfHour * 0.5f
        setTextSizes()
        binding.loop.setBackgroundResource(0)

        highColor = ContextCompat.getColor(this, R.color.black)
        lowColor = ContextCompat.getColor(this, R.color.black)
        midColor = ContextCompat.getColor(this, R.color.black)
        gridColor = ContextCompat.getColor(this, R.color.grey_steampunk)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_dark)
        pointSize = if (sp.getInt(R.string.key_chart_time_frame, 3) < 3) 2 else 1
        setupCharts()

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

        // top row. large font unless text too big (i.e. detailedIOB)
        binding.cob2.textSize = fontLarge
        binding.basalRate.textSize = fontLarge
        val fontIob = if (status.iobDetail.length < 7) fontLarge else fontSmall
        binding.iob2.textSize = fontIob

        // bottom row. font medium unless text too long (i.e. longer than 9' timestamp)
        // always resize these fields together, for symmetry.
        val font = if (binding.timestamp.text.length < 3 || binding.loop.text.length < 3) fontMedium else fontSmall
        binding.loop.textSize = font
        binding.timestamp.textSize = font

        // if both batteries are shown, make them smaller.
        val fontBat = if (sp.getBoolean(R.string.key_show_uploader_battery, true) && sp.getBoolean(R.string.key_show_rig_battery, false)) fontSmall else fontMedium
        binding.uploaderBattery.textSize = fontBat
        binding.rigBattery.textSize = fontBat

    }

    override fun changeChartTimeframe() {
        var timeframe = sp.getInt(R.string.key_chart_time_frame, 3)
        timeframe = timeframe % 5 + 1
        pointSize = if (timeframe < 3) 2 else 1
        sp.putString(R.string.key_chart_time_frame, timeframe.toString())
    }
}
