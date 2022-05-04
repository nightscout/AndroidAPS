package info.nightscout.androidaps.watchfaces

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.ustwo.clockwise.common.WatchMode
import info.nightscout.androidaps.R

class AapsV2Watchface : BaseWatchFace() {

    @LayoutRes override fun layoutResource(): Int = R.layout.activity_home_2

    override fun setColorDark() {
        @ColorInt val dividerTxtColor = if (dividerMatchesBg) ContextCompat.getColor(this, R.color.dark_midColor) else Color.BLACK
        @ColorInt val dividerBatteryOkColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_uploaderBattery)
        @ColorInt val dividerBgColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_statusView)
        mLinearLayout?.setBackgroundColor(dividerBgColor)
        mLinearLayout2?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mIOB1?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mIOB2?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mCOB1?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mCOB2?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDay?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mMonth?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mLoop?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        setTextSizes()
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

        if (status.batteryLevel == 1) mUploaderBattery?.setTextColor(dividerBatteryOkColor)
        else mUploaderBattery?.setTextColor(ContextCompat.getColor(this, R.color.dark_uploaderBatteryEmpty))

        mRigBattery?.setTextColor(dividerTxtColor)
        mDelta?.setTextColor(dividerTxtColor)
        mAvgDelta?.setTextColor(dividerTxtColor)
        mBasalRate?.setTextColor(dividerTxtColor)
        mBgi?.setTextColor(dividerTxtColor)
        when (loopLevel) {
            -1   -> mLoop?.setBackgroundResource(R.drawable.loop_grey_25)
            1    -> mLoop?.setBackgroundResource(R.drawable.loop_green_25)
            else -> mLoop?.setBackgroundResource(R.drawable.loop_red_25)
        }
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
        @ColorInt val dividerTxtColor = if (dividerMatchesBg) ContextCompat.getColor(this, R.color.dark_midColor) else Color.BLACK
        @ColorInt val dividerBgColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_statusView)
        mLinearLayout?.setBackgroundColor(dividerBgColor)
        mLinearLayout2?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mLoop?.setBackgroundResource(R.drawable.loop_grey_25)
        mLoop?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
        mDelta?.setTextColor(dividerTxtColor)
        mAvgDelta?.setTextColor(dividerTxtColor)
        mRigBattery?.setTextColor(dividerTxtColor)
        mUploaderBattery?.setTextColor(dividerTxtColor)
        mBasalRate?.setTextColor(dividerTxtColor)
        mBgi?.setTextColor(dividerTxtColor)
        mIOB1?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mIOB2?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mCOB1?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mCOB2?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDay?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mMonth?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
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
        setTextSizes()
    }

    override fun setColorBright() {
        if (currentWatchMode == WatchMode.INTERACTIVE) {
            @ColorInt val dividerTxtColor = if (dividerMatchesBg) Color.BLACK else ContextCompat.getColor(this, R.color.dark_midColor)
            @ColorInt val dividerBgColor = ContextCompat.getColor(this, if (dividerMatchesBg) R.color.light_background else R.color.light_stripe_background)
            mLinearLayout?.setBackgroundColor(dividerBgColor)
            mLinearLayout2?.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            mTime?.setTextColor(Color.BLACK)
            mIOB1?.setTextColor(Color.BLACK)
            mIOB2?.setTextColor(Color.BLACK)
            mCOB1?.setTextColor(Color.BLACK)
            mCOB2?.setTextColor(Color.BLACK)
            mDay?.setTextColor(Color.BLACK)
            mMonth?.setTextColor(Color.BLACK)
            mLoop?.setTextColor(Color.BLACK)
            setTextSizes()
            when (singleBg.sgvLevel) {
                1L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                    mDirection?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                }

                0L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                    mDirection?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                }

                -1L -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                    mDirection?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                }
            }
            if (ageLevel == 1) mTimestamp?.setTextColor(Color.BLACK)
            else mTimestamp?.setTextColor(Color.RED)
            if (status.batteryLevel == 1) mUploaderBattery?.setTextColor(dividerTxtColor)
            else mUploaderBattery?.setTextColor(Color.RED)
            mRigBattery?.setTextColor(dividerTxtColor)
            mDelta?.setTextColor(dividerTxtColor)
            mAvgDelta?.setTextColor(dividerTxtColor)
            mBasalRate?.setTextColor(dividerTxtColor)
            mBgi?.setTextColor(dividerTxtColor)
            when (loopLevel) {
                -1   -> mLoop?.setBackgroundResource(R.drawable.loop_grey_25)
                1    -> mLoop?.setBackgroundResource(R.drawable.loop_green_25)
                else -> mLoop?.setBackgroundResource(R.drawable.loop_red_25)
            }
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

    private fun setTextSizes() {
        if (status.detailedIob) {
            mIOB1?.textSize = 14f
            mIOB2?.textSize = 10f
        } else {
            mIOB1?.textSize = 10f
            mIOB2?.textSize = 14f
        }
    }
}