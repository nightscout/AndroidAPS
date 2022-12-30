@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.annotation.SuppressLint
import android.support.wearable.watchface.WatchFaceStyle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ActivityDigitalstyleBinding
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace
import info.nightscout.rx.logging.LTag


class DigitalStyleWatchface : BaseWatchFace() {

    private lateinit var binding: ActivityDigitalstyleBinding

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityDigitalstyleBinding.inflate(inflater)
        return binding
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this)
            .setAcceptsTapEvents(true)
            .setHideNotificationIndicator(false)
            .setShowUnreadCountIndicator(true)
            .build()
    }

    override fun setColorDark() {
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

        val colorBat = if (status.batteryLevel == 1) R.color.dark_midColor else R.color.dark_uploaderBatteryEmpty
        binding.uploaderBattery.setTextColor(ContextCompat.getColor(this, colorBat))

        highColor = ContextCompat.getColor(this, R.color.dark_highColor)
        lowColor = ContextCompat.getColor(this, R.color.dark_lowColor)
        midColor = ContextCompat.getColor(this, R.color.dark_midColor)
        gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
        basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
        basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)
        pointSize = 1
        setupCharts()
        setWatchfaceStyle()

    }

    @SuppressLint("SetTextI18n")
    private fun setWatchfaceStyle() {
        /* frame styles*/
        val mShapesElements = layoutView?.findViewById<LinearLayout>(R.id.shapes_elements)
        if (mShapesElements != null) {
            val displayStyle = sp.getString(R.string.key_digital_style_frame_style, "full")
            val displayFrameColor = sp.getString(R.string.key_digital_style_frame_color, "red")
            val displayFrameColorSaturation = sp.getString(R.string.key_digital_style_frame_color_saturation, "500")
            val displayFrameColorOpacity = sp.getString(R.string.key_digital_style_frame_color_opacity, "1")

            // Load image with shapes
            val styleDrawableName = "digital_style_bg_" + displayStyle
            try {
                mShapesElements.background = ContextCompat.getDrawable(this, resources.getIdentifier(styleDrawableName, "drawable", this.packageName))
            } catch (e: Exception) {
                aapsLogger.error("digital_style_frameStyle", "RESOURCE NOT FOUND >> $styleDrawableName")
            }

            // set background-tint-color
            if (displayFrameColor.equals("multicolor", ignoreCase = true) || displayStyle.equals("none", ignoreCase = true)) {
                mShapesElements.backgroundTintList = null
            } else {
                val strColorName = if (displayFrameColor == "white" || displayFrameColor == "black") displayFrameColor else displayFrameColor + "_" + displayFrameColorSaturation
                aapsLogger.debug(LTag.WEAR, "digital_style_strColorName", strColorName)
                try {
                    val colorStateList = ContextCompat.getColorStateList(this, resources.getIdentifier(strColorName, "color", this.packageName))
                    mShapesElements.backgroundTintList = colorStateList
                } catch (e: Exception) {
                    mShapesElements.backgroundTintList = null
                    aapsLogger.error("digital_style_colorName", "COLOR NOT FOUND >> $strColorName")
                }
            }

            // set opacity of shapes
            mShapesElements.alpha = displayFrameColorOpacity.toFloat()
        }

        /* optimize font-size  --> when date is off then increase font-size of time */
        val isShowDate = sp.getBoolean(R.string.key_show_date, false)
        if (!isShowDate) {
            layoutView?.findViewById<View>(R.id.date_time)?.visibility = View.GONE
            binding.hour.textSize = 62f
            binding.minute.textSize = 40f
            binding.hour.letterSpacing = (-0.066).toFloat()
            binding.minute.letterSpacing = (-0.066).toFloat()
        } else {
            layoutView?.findViewById<View>(R.id.date_time)?.visibility = View.VISIBLE
            binding.hour.textSize = 40f
            binding.minute.textSize = 26f
            binding.hour.letterSpacing = 0.toFloat()
            binding.minute.letterSpacing = 0.toFloat()

            /* display week number */
            val mWeekNumber = layoutView?.findViewById<TextView>(R.id.week_number)
            mWeekNumber?.visibility = sp.getBoolean(R.string.key_show_week_number, false).toVisibility()
            mWeekNumber?.text = "(" + dateUtil.weekString() + ")"
        }
    }

    override fun setColorLowRes() {
        setColorDark()
    }

    override fun setColorBright() {
        setColorDark() /* getCurrentWatchMode() == WatchMode.AMBIENT or WatchMode.INTERACTIVE */
    }
}
