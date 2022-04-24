@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.support.wearable.watchface.WatchFaceStyle
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import com.ustwo.clockwise.common.WatchMode
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.menus.MainMenuActivity

class AapsLargeWatchface : BaseWatchFace() {

    private var sgvTapTime: Long = 0

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        layoutView = inflater.inflate(R.layout.activity_home_large, null)
        performViewSetup()
    }

    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
        mSgv?.let { mSgv ->
            val extra = (mSgv.right - mSgv.left) / 2
            if (tapType == TAP_TYPE_TAP && x + extra >= mSgv.left && x - extra <= mSgv.right && y >= mSgv.top && y <= mSgv.bottom) {
                if (eventTime - sgvTapTime < 800) {
                    val intent = Intent(this, MainMenuActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                sgvTapTime = eventTime
            }
        }
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build()
    }

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