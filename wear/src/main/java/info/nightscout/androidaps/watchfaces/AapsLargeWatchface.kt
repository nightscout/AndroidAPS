package info.nightscout.androidaps.watchfaces

import android.graphics.Color
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.ustwo.clockwise.common.WatchMode
import info.nightscout.androidaps.R

class AapsLargeWatchface : BaseWatchFace() {

    @LayoutRes override fun layoutResource(): Int = R.layout.activity_home_large

    override fun setColorDark() {
        mLinearLayout?.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_mLinearLayout))
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
        } else {
            mRelativeLayout?.setBackgroundColor(Color.BLACK)
            mLinearLayout?.setBackgroundColor(if (dividerMatchesBg) Color.BLACK else Color.LTGRAY)
            when (singleBg.sgvLevel) {
                1L  -> {
                    mSgv?.setTextColor(Color.YELLOW)
                    mDirection?.setTextColor(Color.YELLOW)
                    mDelta?.setTextColor(Color.YELLOW)
                }

                0L  -> {
                    mSgv?.setTextColor(Color.WHITE)
                    mDirection?.setTextColor(Color.WHITE)
                    mDelta?.setTextColor(Color.WHITE)
                }

                -1L -> {
                    mSgv?.setTextColor(Color.RED)
                    mDirection?.setTextColor(Color.RED)
                    mDelta?.setTextColor(Color.RED)
                }
            }
            mUploaderBattery?.setTextColor(if (dividerMatchesBg) Color.WHITE else Color.BLACK)
            mTimestamp?.setTextColor(if (dividerMatchesBg) Color.WHITE else Color.BLACK)
            mStatus?.setTextColor(if (dividerMatchesBg) Color.WHITE else Color.BLACK)
            mTime?.setTextColor(Color.WHITE)
        }
    }

    override fun setColorLowRes() {
        mLinearLayout?.setBackgroundColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_background else R.color.dark_mLinearLayout))
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDirection?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mTimestamp?.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mTimestamp1_home))
        mUploaderBattery?.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_uploaderBattery))
        mStatus?.setTextColor(ContextCompat.getColor(this, if (dividerMatchesBg) R.color.dark_midColor else R.color.dark_mStatus_home))
    }
}