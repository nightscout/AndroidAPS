package app.aaps.wear.watchfaces

import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import app.aaps.wear.R
import app.aaps.wear.databinding.ActivityHomeLargeBinding
import app.aaps.wear.watchfaces.utils.BaseWatchFace
import com.ustwo.clockwise.common.WatchMode

class AapsLargeWatchface : BaseWatchFace() {

    private lateinit var binding: ActivityHomeLargeBinding

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityHomeLargeBinding.inflate(inflater)
        return binding
    }

    override fun setColorDark() {
        binding.secondaryLayout.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_mLinearLayout))
        binding.time.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
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
        } else {
            binding.mainLayout.setBackgroundColor(Color.BLACK)
            binding.secondaryLayout.setBackgroundColor(if (dividerMatchesBg) Color.BLACK else Color.LTGRAY)
            val color = when (singleBg.sgvLevel) {
                1L   -> Color.YELLOW
                0L   -> Color.WHITE
                -1L  -> Color.RED
                else -> Color.WHITE
            }
            binding.sgv.setTextColor(ContextCompat.getColor(this, color))
            binding.delta.setTextColor(ContextCompat.getColor(this, color))
            binding.direction.setTextColor(ContextCompat.getColor(this, color))

            binding.uploaderBattery.setTextColor(if (dividerMatchesBg) Color.WHITE else Color.BLACK)
            binding.timestamp.setTextColor(if (dividerMatchesBg) Color.WHITE else Color.BLACK)
            binding.status.setTextColor(if (dividerMatchesBg) Color.WHITE else Color.BLACK)
            binding.time.setTextColor(Color.WHITE)
        }
    }

    override fun setColorLowRes() {
        binding.secondaryLayout.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_mLinearLayout))
        binding.time.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.sgv.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.delta.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.direction.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.timestamp.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mTimestamp1_home))
        binding.uploaderBattery.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_uploaderBattery))
        binding.status.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mStatus_home))
    }
}
