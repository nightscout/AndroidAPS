package app.aaps.wear.watchfaces

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.events.EventUpdateSelectedWatchface
import app.aaps.wear.R
import app.aaps.wear.databinding.ActivityDigitalstyleBinding
import app.aaps.wear.watchfaces.utils.BaseWatchFace
import app.aaps.wear.watchfaces.utils.WatchfaceViewAdapter.Companion.SelectedWatchFace

@SuppressLint("Deprecated")
class DigitalStyleWatchface : BaseWatchFace() {

    private lateinit var binding: ActivityDigitalstyleBinding

    override fun inflateLayout(inflater: LayoutInflater): ViewBinding {
        binding = ActivityDigitalstyleBinding.inflate(inflater)
        sp.putInt(R.string.key_last_selected_watchface, SelectedWatchFace.DIGITAL.ordinal)
        rxBus.send(EventUpdateSelectedWatchface())
        return binding
    }


    override fun setColorDark() {
        val color = when (singleBg[0].sgvLevel) {
            1L   -> R.color.dark_highColor
            0L   -> R.color.dark_midColor
            -1L  -> R.color.dark_lowColor
            else -> R.color.dark_midColor
        }
        binding.sgv.setTextColor(ContextCompat.getColor(this, color))
        binding.direction.setTextColor(ContextCompat.getColor(this, color))

        val colorTime = if (ageLevel() == 1) R.color.dark_midColor else R.color.dark_TimestampOld
        binding.timestamp.setTextColor(ContextCompat.getColor(this, colorTime))

        val colorBat = if (status[0].batteryLevel == 1) R.color.dark_midColor else R.color.dark_uploaderBatteryEmpty
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

    private fun setWatchfaceStyle() {
        /* frame styles*/
        val mShapesElements = layoutView?.findViewById<LinearLayout>(R.id.shapes_elements)
        if (mShapesElements != null) {
            val displayStyle = sp.getString(R.string.key_digital_style_frame_style, "full")
            val displayFrameColor = sp.getString(R.string.key_digital_style_frame_color, "red")
            val displayFrameColorSaturation = sp.getString(R.string.key_digital_style_frame_color_saturation, "500")
            val displayFrameColorOpacity = sp.getString(R.string.key_digital_style_frame_color_opacity, "1")

            // Load image with shapes
            // Note: getIdentifier is used here because resource names are constructed dynamically
            // from user preferences. There are many possible combinations of styles and colors,
            // making a static mapping impractical.
            val styleDrawableName = "digital_style_bg_$displayStyle"
            try {
                @Suppress("DiscouragedApi")
                val drawableId = resources.getIdentifier(styleDrawableName, "drawable", this.packageName)
                mShapesElements.background = ContextCompat.getDrawable(this, drawableId)
            } catch (_: Exception) {
                aapsLogger.error("digital_style_frameStyle", "RESOURCE NOT FOUND >> $styleDrawableName")
            }

            // set background-tint-color
            if (displayFrameColor.equals("multicolor", ignoreCase = true) || displayStyle.equals("none", ignoreCase = true)) {
                mShapesElements.backgroundTintList = null
            } else {
                val strColorName = if (displayFrameColor == "white" || displayFrameColor == "black") displayFrameColor else displayFrameColor + "_" + displayFrameColorSaturation
                aapsLogger.debug(LTag.WEAR, "digital_style_strColorName", strColorName)
                try {
                    @Suppress("DiscouragedApi")
                    val colorId = resources.getIdentifier(strColorName, "color", this.packageName)
                    val colorStateList = ContextCompat.getColorStateList(this, colorId)
                    mShapesElements.backgroundTintList = colorStateList
                } catch (_: Exception) {
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
        }
    }

    override fun setColorLowRes() {
        setColorDark()
    }

    override fun setColorBright() {
        setColorDark() /* getCurrentWatchMode() == WatchMode.AMBIENT or WatchMode.INTERACTIVE */
    }
}
