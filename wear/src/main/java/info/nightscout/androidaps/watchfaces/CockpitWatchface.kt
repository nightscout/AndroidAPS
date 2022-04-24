@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.watchfaces

import android.annotation.SuppressLint
import android.content.Intent
import android.support.wearable.watchface.WatchFaceStyle
import android.view.LayoutInflater
import android.view.View
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.menus.MainMenuActivity

/**
 * Created by andrew-warrington on 18/11/2017.
 * Refactored by MilosKozak 24/04/2022
 */
class CockpitWatchface : BaseWatchFace() {

    private var sgvTapTime: Long = 0

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        layoutView = inflater.inflate(R.layout.activity_cockpit, null)
        performViewSetup()
    }

    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
        if (tapType == TAP_TYPE_TAP) {
            if (eventTime - sgvTapTime < 800) {
                val intent = Intent(this, MainMenuActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            sgvTapTime = eventTime
        }
    }

    override fun getWatchFaceStyle(): WatchFaceStyle {
        return WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build()
    }

    override fun setColorDark() {
        mRelativeLayout?.setBackgroundResource(R.drawable.airplane_cockpit_outside_clouds)
        setTextSizes()
        when (singleBg.sgvLevel) {
            1L  -> {
                mHighLight?.setBackgroundResource(R.drawable.airplane_led_yellow_lit)
                mLowLight?.setBackgroundResource(R.drawable.airplane_led_grey_unlit)
            }

            0L  -> {
                mHighLight?.setBackgroundResource(R.drawable.airplane_led_grey_unlit)
                mLowLight?.setBackgroundResource(R.drawable.airplane_led_grey_unlit)
            }

            -1L -> {
                mHighLight?.setBackgroundResource(R.drawable.airplane_led_grey_unlit)
                mLowLight?.setBackgroundResource(R.drawable.airplane_led_red_lit)
            }
        }
        when (loopLevel) {
            -1   -> mLoop?.setBackgroundResource(R.drawable.loop_grey_25)
            1    -> mLoop?.setBackgroundResource(R.drawable.loop_green_25)
            else -> mLoop?.setBackgroundResource(R.drawable.loop_red_25)
        }
        invalidate()
    }

    override fun setColorLowRes() {
        mRelativeLayout?.setBackgroundResource(R.drawable.airplane_cockpit_outside_clouds_lowres)
    }

    override fun setColorBright() {
        setColorDark()
    }

    private fun setTextSizes() {
            if (status.detailedIob) {
                if (bIsRound) mIOB2?.textSize = 10f
                else mIOB2?.textSize = 9f
            } else {
                if (bIsRound) mIOB2?.textSize = 13f
                else mIOB2?.textSize = 12f
            }
        if (mUploaderBattery?.visibility != View.GONE && mRigBattery?.visibility != View.GONE) {
            if (bIsRound) {
                mUploaderBattery?.textSize = 12f
                mRigBattery?.textSize = 12f
            } else {
                mUploaderBattery?.textSize = 10f
                mRigBattery?.textSize = 10f
            }
        } else {
            if (bIsRound) {
                mUploaderBattery?.textSize = 13f
                mRigBattery?.textSize = 13f
            } else {
                mUploaderBattery?.textSize = 12f
                mRigBattery?.textSize = 12f
            }
        }
    }
}