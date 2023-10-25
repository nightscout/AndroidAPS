@file:Suppress("DEPRECATION")

package app.aaps.wear.watchfaces

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import app.aaps.wear.R
import app.aaps.wear.databinding.ActivityBigchartBinding
import app.aaps.wear.watchfaces.utils.BaseWatchFace
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter
import com.ustwo.clockwise.common.WatchMode

class BigChartWatchface : BaseWatchFace() {

    private lateinit var binding: WatchfaceViewAdapter

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        val layoutBinding = ActivityBigchartBinding.inflate(inflater)
        binding = WatchfaceViewAdapter.getBinding(layoutBinding)
        return layoutBinding
    }

    @SuppressLint("SetTextI18n")
    override fun setDataFields() {
        super.setDataFields()
        binding.status?.text = externalStatus + if (sp.getBoolean(R.string.key_show_cob, true)) (" " + this.status.cob) else ""
    }

    override fun setColorLowRes() {
        binding.time?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        binding.status?.setTextColor(ContextCompat.getColor(this, R.color.dark_statusView))
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        binding.sgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.delta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.avgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        binding.timestamp.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
        binding.timePeriod?.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))

        highColor = ContextCompat.getColor(this, R.color.dark_midColor)
        lowColor = ContextCompat.getColor(this, R.color.dark_midColor)
        midColor = ContextCompat.getColor(this, R.color.dark_midColor)
        gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark_lowres)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light_lowres)
        pointSize = 2
        setupCharts()
    }

    override fun setColorDark() {
        binding.time?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        binding.status?.setTextColor(ContextCompat.getColor(this, R.color.dark_statusView))
        binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        val color = when (singleBg.sgvLevel) {
            1L   -> R.color.dark_highColor
            0L   -> R.color.dark_midColor
            -1L  -> R.color.dark_lowColor
            else -> R.color.dark_midColor
        }
        binding.sgv?.setTextColor(ContextCompat.getColor(this, color))
        binding.delta?.setTextColor(ContextCompat.getColor(this, color))
        binding.avgDelta?.setTextColor(ContextCompat.getColor(this, color))

        val colorTime = if (ageLevel == 1) R.color.dark_Timestamp else R.color.dark_TimestampOld
        binding.timestamp.setTextColor(ContextCompat.getColor(this, colorTime))
        binding.timePeriod?.setTextColor(ContextCompat.getColor(this, colorTime))

        highColor = ContextCompat.getColor(this, R.color.dark_highColor)
        lowColor = ContextCompat.getColor(this, R.color.dark_lowColor)
        midColor = ContextCompat.getColor(this, R.color.dark_midColor)
        gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)
        pointSize = 2
        setupCharts()
    }

    override fun setColorBright() {
        if (currentWatchMode == WatchMode.INTERACTIVE) {
            binding.time?.setTextColor(ContextCompat.getColor(this, R.color.light_bigchart_time))
            binding.status?.setTextColor(ContextCompat.getColor(this, R.color.light_bigchart_status))
            binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            val color = when (singleBg.sgvLevel) {
                1L   -> R.color.light_highColor
                0L   -> R.color.light_midColor
                -1L  -> R.color.light_lowColor
                else -> R.color.light_midColor
            }
            binding.sgv?.setTextColor(ContextCompat.getColor(this, color))
            binding.delta?.setTextColor(ContextCompat.getColor(this, color))
            binding.avgDelta?.setTextColor(ContextCompat.getColor(this, color))

            val colorTime = if (ageLevel == 1) R.color.light_mTimestamp1 else R.color.light_mTimestamp
            binding.timestamp.setTextColor(ContextCompat.getColor(this, colorTime))
            binding.timePeriod?.setTextColor(ContextCompat.getColor(this, colorTime))

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
