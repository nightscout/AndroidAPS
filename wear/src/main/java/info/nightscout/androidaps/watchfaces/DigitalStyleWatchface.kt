@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.annotation.SuppressLint
import android.support.wearable.watchface.WatchFaceStyle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.R
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.shared.logging.LTag

class DigitalStyleWatchface : BaseWatchFace() {

    @LayoutRes override fun layoutResource(): Int = R.layout.activity_digitalstyle

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this)
            .setAcceptsTapEvents(true)
            .setHideNotificationIndicator(false)
            .setShowUnreadCountIndicator(true)
            .build()
    }

    override fun setColorDark() {
        when (singleBg.sgvLevel) {
            1L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
                mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
            }

            0L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
                mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
            }

            -1L -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
                mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
            }
        }
        if (ageLevel == 1) mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        else mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))

        if (status.batteryLevel == 1) mUploaderBattery?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        else mUploaderBattery?.setTextColor(ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty))

        if (chart != null) {
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
    }

    @SuppressLint("SetTextI18n")
    private fun setWatchfaceStyle() {
        /* frame styles*/
        val mShapesElements = layoutView?.findViewById<LinearLayout>(R.id.shapes_elements)
        if (mShapesElements != null) {
            val displayFormatType = if (mShapesElements.contentDescription.toString().startsWith("round")) "round" else "rect"
            val displayStyle = sp.getString(R.string.key_digital_style_frame_style, "full")
            val displayFrameColor = sp.getString(R.string.key_digital_style_frame_color, "red")
            val displayFrameColorSaturation = sp.getString(R.string.key_digital_style_frame_color_saturation, "500")
            val displayFrameColorOpacity = sp.getString(R.string.key_digital_style_frame_color_opacity, "1")

            // Load image with shapes
            val styleDrawableName = "digitalstyle_bg_" + displayStyle + "_" + displayFormatType
            try {
                mShapesElements.background = ContextCompat.getDrawable(this, resources.getIdentifier(styleDrawableName, "drawable", this.packageName))
            } catch (e: Exception) {
                aapsLogger.error("digitalstyle_frameStyle", "RESOURCE NOT FOUND >> $styleDrawableName")
            }

            // set background-tint-color
            if (displayFrameColor.equals("multicolor", ignoreCase = true) || displayStyle.equals("none", ignoreCase = true)) {
                mShapesElements.backgroundTintList = null
            } else {
                val strColorName = if (displayFrameColor == "white" || displayFrameColor == "black") displayFrameColor else displayFrameColor + "_" + displayFrameColorSaturation
                aapsLogger.debug(LTag.WEAR, "digitalstyle_strColorName", strColorName)
                try {
                    val colorStateList = ContextCompat.getColorStateList(this, resources.getIdentifier(strColorName, "color", this.packageName))
                    mShapesElements.backgroundTintList = colorStateList
                } catch (e: Exception) {
                    mShapesElements.backgroundTintList = null
                    aapsLogger.error("digitalstyle_colorName", "COLOR NOT FOUND >> $strColorName")
                }
            }

            // set opacity of shapes
            mShapesElements.alpha = displayFrameColorOpacity.toFloat()
        }

        /* optimize font-size  --> when date is off then increase font-size of time */
        val isShowDate = sp.getBoolean(R.string.key_show_date, false)
        if (!isShowDate) {
            layoutView?.findViewById<View>(R.id.date_time)?.visibility = View.GONE
            mHour?.textSize = 62f
            mMinute?.textSize = 40f
            mHour?.letterSpacing = (-0.066).toFloat()
            mMinute?.letterSpacing = (-0.066).toFloat()
        } else {
            layoutView?.findViewById<View>(R.id.date_time)?.visibility = View.VISIBLE
            mHour?.textSize = 40f
            mMinute?.textSize = 26f
            mHour?.letterSpacing = 0.toFloat()
            mMinute?.letterSpacing = 0.toFloat()

            /* display week number */
            val mWeekNumber = layoutView?.findViewById<TextView>(R.id.weeknumber)
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