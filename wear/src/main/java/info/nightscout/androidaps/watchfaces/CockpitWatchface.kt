package info.nightscout.androidaps.watchfaces

import android.view.LayoutInflater
import android.view.View
import androidx.viewbinding.ViewBinding
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityCockpitBinding
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace

/**
 * Created by andrew-warrington on 18/11/2017.
 * Refactored by MilosKozak 24/04/2022
 */
class CockpitWatchface : BaseWatchFace() {

    private lateinit var binding: ActivityCockpitBinding

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityCockpitBinding.inflate(inflater)
        return binding
    }

    override fun setColorDark() {
        binding.mainLayout.setBackgroundResource(R.drawable.airplane_cockpit_outside_clouds)
        setTextSizes()
        val led = when (singleBg.sgvLevel) {
            1L  -> R.drawable.airplane_led_yellow_lit
            0L  -> R.drawable.airplane_led_grey_unlit
            -1L -> R.drawable.airplane_led_red_lit
            else -> R.drawable.airplane_led_grey_unlit
        }

        binding.highLight.setBackgroundResource(led)
        binding.lowLight.setBackgroundResource(led)

        when (loopLevel) {
            -1   -> binding.loop.setBackgroundResource(R.drawable.loop_grey_25)
            1    -> binding.loop.setBackgroundResource(R.drawable.loop_green_25)
            else -> binding.loop.setBackgroundResource(R.drawable.loop_red_25)
        }
        invalidate()
    }

    override fun setColorLowRes() {
        binding.mainLayout.setBackgroundResource(R.drawable.airplane_cockpit_outside_clouds_lowres)
    }

    override fun setColorBright() {
        setColorDark()
    }

    private fun setTextSizes() {
        if (detailedIob) {
            if (bIsRound) binding.iob2.textSize = 10f
            else binding.iob2.textSize = 9f
        } else {
            if (bIsRound) binding.iob2.textSize = 13f
            else binding.iob2.textSize = 12f
        }
        if (binding.uploaderBattery.visibility != View.GONE && binding.rigBattery.visibility != View.GONE) {
            if (bIsRound) {
                binding.uploaderBattery.textSize = 12f
                binding.rigBattery.textSize = 12f
            } else {
                binding.uploaderBattery.textSize = 10f
                binding.rigBattery.textSize = 10f
            }
        } else {
            if (bIsRound) {
                binding.uploaderBattery.textSize = 13f
                binding.rigBattery.textSize = 13f
            } else {
                binding.uploaderBattery.textSize = 12f
                binding.rigBattery.textSize = 12f
            }
        }
    }
}
