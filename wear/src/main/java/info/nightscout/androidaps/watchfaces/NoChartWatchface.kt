package info.nightscout.androidaps.watchfaces

import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.ustwo.clockwise.common.WatchMode
import info.nightscout.androidaps.R

class NoChartWatchface : BaseWatchFace() {

    @LayoutRes override fun layoutResource(): Int =
        if (resources.displayMetrics.widthPixels < SCREEN_SIZE_SMALL || resources.displayMetrics.heightPixels < SCREEN_SIZE_SMALL) R.layout.activity_nochart_small
        else R.layout.activity_nochart

    override fun setColorLowRes() {
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        mStatus?.setTextColor(ContextCompat.getColor(this, R.color.dark_statusView))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
        mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
    }

    override fun setColorDark() {
        mTime?.setTextColor(ContextCompat.getColor(this, R.color.dark_mTime))
        mStatus?.setTextColor(ContextCompat.getColor(this, R.color.dark_statusView))
        mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        when (singleBg.sgvLevel) {
            1L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
                mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_highColor))
            }

            0L  -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
                mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_midColor))
            }

            -1L -> {
                mSgv?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
                mDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
                mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.dark_lowColor))
            }
        }
        if (ageLevel == 1) {
            mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_Timestamp))
        } else {
            mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.dark_TimestampOld))
        }
    }

    override fun setColorBright() {
        if (currentWatchMode == WatchMode.INTERACTIVE) {
            mTime?.setTextColor(ContextCompat.getColor(this, R.color.light_bigchart_time))
            mStatus?.setTextColor(ContextCompat.getColor(this, R.color.light_bigchart_status))
            mRelativeLayout?.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
            when (singleBg.sgvLevel) {
                1L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                    mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_highColor))
                }

                0L  -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                    mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_midColor))
                }

                -1L -> {
                    mSgv?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                    mDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                    mAvgDelta?.setTextColor(ContextCompat.getColor(this, R.color.light_lowColor))
                }
            }
            if (ageLevel == 1) {
                mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.light_mTimestamp1))
            } else {
                mTimestamp?.setTextColor(ContextCompat.getColor(this, R.color.light_mTimestamp))
            }
        } else {
            setColorDark()
        }
    }
}