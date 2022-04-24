package info.nightscout.androidaps.watchfaces

import android.graphics.Color
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.ustwo.clockwise.common.WatchMode
import info.nightscout.androidaps.R

class AapsWatchface : BaseWatchFace() {

    @LayoutRes override fun layoutResource(): Int = R.layout.activity_home

    override fun setColorDark() {
        mLinearLayout?.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_statusView))
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        when (singleBg.sgvLevel) {
            1L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
                mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
            }

            0L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
                mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
            }

            -1L -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
                mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
            }
        }
        if (ageLevel == 1) mTimestamp?.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mTimestamp1_home))
        else mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        if (status.batteryLevel == 1) mUploaderBattery?.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_uploaderBattery))
        else mUploaderBattery?.setTextColor(ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty))
        mStatus?.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mStatus_home))
        if (chart != null) {
            highColor = ContextCompat.getColor(this, R.color.dark_highColor)
            lowColor = ContextCompat.getColor(this, R.color.dark_lowColor)
            midColor = ContextCompat.getColor(this, R.color.dark_midColor)
            gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
            basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark)
            basalCenterColor = ContextCompat.getColor(this, R.color.basal_light)
            pointSize = 2
            setupCharts()
        }
    }

    override fun setColorLowRes() {
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
        if (chart != null) {
            highColor = ContextCompat.getColor(this, R.color.dark_midColor)
            lowColor = ContextCompat.getColor(this, R.color.dark_midColor)
            midColor = ContextCompat.getColor(this, R.color.dark_midColor)
            gridColor = ContextCompat.getColor(this, R.color.dark_gridColor)
            basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_dark_lowres)
            basalCenterColor = ContextCompat.getColor(this, R.color.basal_light_lowres)
            pointSize = 2
            setupCharts()
        }
    }

    override fun setColorBright() {
        if (currentWatchMode == WatchMode.INTERACTIVE) {
            mLinearLayout?.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.light_background else R.color.light_stripe_background))
            mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            when (singleBg.sgvLevel) {
                1L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                    mDirection?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                }

                0L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                    mDirection?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                }

                -1L -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                    mDirection?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                }
            }
            if (ageLevel == 1) mTimestamp?.setTextColor(if (dividerMatchesBg) Color.BLACK else Color.WHITE)
            else mTimestamp?.setTextColor(Color.RED)
            if (status.batteryLevel == 1) mUploaderBattery?.setTextColor(if (dividerMatchesBg) Color.BLACK else Color.WHITE)
            else mUploaderBattery?.setTextColor(Color.RED)
            mStatus?.setTextColor(if (dividerMatchesBg) Color.BLACK else Color.WHITE)
            mTime?.setTextColor(Color.BLACK)
            if (chart != null) {
                highColor = ContextCompat.getColor(this, R.color.light_highColor)
                lowColor = ContextCompat.getColor(this, R.color.light_lowColor)
                midColor = ContextCompat.getColor(this, R.color.light_midColor)
                gridColor = ContextCompat.getColor(this, R.color.light_gridColor)
                basalBackgroundColor = ContextCompat.getColor(this, R.color.basal_light)
                basalCenterColor = ContextCompat.getColor(this, R.color.basal_dark)
                pointSize = 2
                setupCharts()
            }
        } else {
            setColorDark()
        }
    }
}