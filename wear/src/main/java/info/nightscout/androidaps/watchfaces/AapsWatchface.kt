package info.nightscout.androidaps.watchfaces

import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.ustwo.clockwise.common.WatchMode
import info.nightscout.androidaps.R
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace
import info.nightscout.androidaps.databinding.ActivityHomeBinding

class AapsWatchface : BaseWatchFace() {

    private lateinit var binding: ActivityHomeBinding

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityHomeBinding.inflate(inflater)
        return binding
    }

    override fun setColorDark() {
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.secondaryLayout.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_statusView))
        binding.time.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        val color = when (singleBg.sgvLevel) {
            1L   -> R.color.dark_highColor
            0L   -> R.color.dark_midColor
            -1L  -> R.color.dark_lowColor
            else -> R.color.dark_midColor
        }
        binding.sgv.setTextColor(ContextCompat.getColor(this, color))
        binding.delta.setTextColor(ContextCompat.getColor(this, color))
        binding.direction.setTextColor(ContextCompat.getColor(this, color))

        val colorTime = if (ageLevel == 1) if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mTimestamp1_home else R.color.dark_TimestampOld
        binding.timestamp.setTextColor(ContextCompat.getColor(this, colorTime))

        val colourBat = if (status.batteryLevel == 1) if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_uploaderBattery else R.color.dark_uploaderBatteryEmpty
        binding.uploaderBattery.setTextColor(ContextCompat.getColor(this, colourBat))

        binding.status.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mStatus_home))

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
        binding.time.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.sgv.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.delta.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))

        highColor = ContextCompat.getColor(this, R.color.dark_midColor)
        lowColor = ContextCompat.getColor(this, R.color.dark_midColor)
        midColor = ContextCompat.getColor(this, R.color.dark_midColor)
        gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark_lowres)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light_lowres)
        pointSize = 2
        setupCharts()
    }

    override fun setColorBright() {
        if (currentWatchMode == WatchMode.INTERACTIVE) {
            binding.secondaryLayout.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.light_background else R.color.light_stripe_background))
            binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            val color = when (singleBg.sgvLevel) {
                1L   -> R.color.light_highColor
                0L   -> R.color.light_midColor
                -1L  -> R.color.light_lowColor
                else -> R.color.light_midColor
            }
            binding.sgv.setTextColor(ContextCompat.getColor(this, color))
            binding.delta.setTextColor(ContextCompat.getColor(this, color))
            binding.direction.setTextColor(ContextCompat.getColor(this, color))

            val colorTime = if (ageLevel == 1) if (dividerMatchesBg) Color.BLACK else Color.WHITE else Color.RED
            binding.timestamp.setTextColor(colorTime)

            val colourBat = if (status.batteryLevel == 1) if (dividerMatchesBg) Color.BLACK else Color.WHITE else Color.RED
            binding.uploaderBattery.setTextColor(colourBat)

            binding.status.setTextColor(if (dividerMatchesBg) Color.BLACK else Color.WHITE)
            binding.time.setTextColor(Color.BLACK)

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
}
