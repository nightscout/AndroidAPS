package info.nightscout.androidaps.watchfaces

import android.graphics.Color
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.ustwo.clockwise.common.WatchMode
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityHome2Binding
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace

class AapsV2Watchface : BaseWatchFace() {

    private lateinit var binding: ActivityHome2Binding

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityHome2Binding.inflate(inflater)
        return binding
    }

    override fun setColorDark() {
        @ColorInt val dividerTxtColor = if (dividerMatchesBg) ContextCompat.getColor(this, R.color.dark_midColor) else Color.BLACK
        @ColorInt val dividerBatteryOkColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_uploaderBattery)
        @ColorInt val dividerBgColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_statusView)
        binding.secondaryLayout.setBackgroundColor(dividerBgColor)
        binding.tertiaryLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.time.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.iob1.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.iob2.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.cob1.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.cob2.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.day.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.month.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.loop.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        setTextSizes()
        val color = when (singleBg.sgvLevel) {
            1L   -> R.color.dark_highColor
            0L   -> R.color.dark_midColor
            -1L  -> R.color.dark_lowColor
            else -> R.color.dark_midColor
        }
        binding.sgv.setTextColor(ContextCompat.getColor(this, color))
        binding.direction.setTextColor(ContextCompat.getColor(this, color))

        val colorTime = if (ageLevel == 1) R.color.dark_midColor else R.color.dark_TimestampOld
        binding.timestamp.setTextColor(ContextCompat.getColor(this, colorTime))
        val colourBat = if (status.batteryLevel == 1) dividerBatteryOkColor else ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty)
        binding.uploaderBattery.setTextColor(colourBat)

        binding.rigBattery.setTextColor(dividerTxtColor)
        binding.delta.setTextColor(dividerTxtColor)
        binding.avgDelta.setTextColor(dividerTxtColor)
        binding.basalRate.setTextColor(dividerTxtColor)
        binding.bgi.setTextColor(dividerTxtColor)
        when (loopLevel) {
            -1   -> binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
            1    -> binding.loop.setBackgroundResource(R.drawable.loop_green_25)
            else -> binding.loop.setBackgroundResource(R.drawable.loop_red_25)
        }

        highColor = ContextCompat.getColor(this, R.color.dark_highColor)
        lowColor = ContextCompat.getColor(this, R.color.dark_lowColor)
        midColor = ContextCompat.getColor(this, R.color.dark_midColor)
        gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)
        pointSize = 2
        setupCharts()
    }

    override fun setColorLowRes() {
        @ColorInt val dividerTxtColor = if (dividerMatchesBg) ContextCompat.getColor(this, R.color.dark_midColor) else Color.BLACK
        @ColorInt val dividerBgColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_statusView)
        binding.secondaryLayout.setBackgroundColor(dividerBgColor)
        binding.tertiaryLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
        binding.loop.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.sgv.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.direction.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
        binding.delta.setTextColor(dividerTxtColor)
        binding.avgDelta.setTextColor(dividerTxtColor)
        binding.rigBattery.setTextColor(dividerTxtColor)
        binding.uploaderBattery.setTextColor(dividerTxtColor)
        binding.basalRate.setTextColor(dividerTxtColor)
        binding.bgi.setTextColor(dividerTxtColor)
        binding.iob1.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.iob2.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.cob1.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.cob2.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.day.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.month.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.time.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))

        highColor = ContextCompat.getColor(this, R.color.dark_midColor)
        lowColor = ContextCompat.getColor(this, R.color.dark_midColor)
        midColor = ContextCompat.getColor(this, R.color.dark_midColor)
        gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark_lowres)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light_lowres)
        pointSize = 2
        setupCharts()

        setTextSizes()
    }

    override fun setColorBright() {
        if (currentWatchMode == WatchMode.INTERACTIVE) {
            @ColorInt val dividerTxtColor = if (dividerMatchesBg) Color.BLACK else ContextCompat.getColor(this, R.color.dark_midColor)
            @ColorInt val dividerBgColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.light_background else R.color.light_stripe_background)
            binding.secondaryLayout.setBackgroundColor(dividerBgColor)
            binding.tertiaryLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            binding.time.setTextColor(Color.BLACK)
            binding.iob1.setTextColor(Color.BLACK)
            binding.iob2.setTextColor(Color.BLACK)
            binding.cob1.setTextColor(Color.BLACK)
            binding.cob2.setTextColor(Color.BLACK)
            binding.day.setTextColor(Color.BLACK)
            binding.month.setTextColor(Color.BLACK)
            binding.loop.setTextColor(Color.BLACK)
            setTextSizes()
            val color = when (singleBg.sgvLevel) {
                1L   -> R.color.light_highColor
                0L   -> R.color.light_midColor
                -1L  -> R.color.light_lowColor
                else -> R.color.light_midColor
            }
            binding.sgv.setTextColor(ContextCompat.getColor(this, color))
            binding.direction.setTextColor(ContextCompat.getColor(this, color))
            val colorTime = if (ageLevel == 1) Color.BLACK else Color.RED
            binding.timestamp.setTextColor(colorTime)
            val colourBat = if (status.batteryLevel == 1) dividerTxtColor else Color.RED
            binding.uploaderBattery.setTextColor(colourBat)
            binding.rigBattery.setTextColor(dividerTxtColor)
            binding.delta.setTextColor(dividerTxtColor)
            binding.avgDelta.setTextColor(dividerTxtColor)
            binding.basalRate.setTextColor(dividerTxtColor)
            binding.bgi.setTextColor(dividerTxtColor)
            when (loopLevel) {
                -1   -> binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
                1    -> binding.loop.setBackgroundResource(R.drawable.loop_green_25)
                else -> binding.loop.setBackgroundResource(R.drawable.loop_red_25)
            }

            highColor = ContextCompat.getColor(this, R.color.light_highColor)
            lowColor = ContextCompat.getColor(this, R.color.light_lowColor)
            midColor = ContextCompat.getColor(this, R.color.light_midColor)
            gridColor = ContextCompat.getColor(this, R.color.light_gridColor)
            basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_light)
            basalCenterColor = ContextCompat.getColor(this, R.color.basal_dark)
            pointSize = 2
            setupCharts()

        } else {
            setColorDark()
        }
    }

    private fun setTextSizes() {
        if (detailedIob) {
            binding.iob1.textSize = 14f
            binding.iob2.textSize = 10f
        } else {
            binding.iob1.textSize = 10f
            binding.iob2.textSize = 14f
        }
    }
}
