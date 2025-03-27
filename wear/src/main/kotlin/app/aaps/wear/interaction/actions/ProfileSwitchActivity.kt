@file:Suppress("DEPRECATION")

package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionProfileSwitchPreCheck
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.nondeprecated.GridPagerAdapterNonDeprecated
import java.text.DecimalFormat

class ProfileSwitchActivity : ViewSelectorActivity() {

    var editPercentage: PlusMinusEditText? = null
    var editTimeshift: PlusMinusEditText? = null
    var editDuration: PlusMinusEditText? = null
    var percentage = -1
    var timeshift = -25
    var duration = 0.0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        percentage = intent.extras?.getInt("percentage", -1) ?: -1
        timeshift = intent.extras?.getInt("timeshift", -25) ?: -25
        if (percentage == -1 || timeshift == -25) {
            finish()
            return
        }
        setAdapter(MyGridViewPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapterNonDeprecated() {

        override fun getColumnCount(arg0: Int): Int = 4
        override fun getRowCount(): Int = 1

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when (col) {
            0    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                val initValue = SafeParse.stringToDouble(editTimeshift?.editText?.text.toString(), timeshift.toDouble())
                editTimeshift = PlusMinusEditText(viewAdapter, initValue, Constants.CPP_MIN_TIMESHIFT.toDouble(), Constants.CPP_MAX_TIMESHIFT.toDouble(), 1.0, DecimalFormat("0"), true, getString(R.string.action_timeshift_hours), true)
                container.addView(view)
                view.requestFocus()
                view
            }

            1    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                val initValue = SafeParse.stringToDouble(editPercentage?.editText?.text.toString(), percentage.toDouble())
                editPercentage = PlusMinusEditText(viewAdapter, initValue, Constants.CPP_MIN_PERCENTAGE.toDouble(), Constants.CPP_MAX_PERCENTAGE.toDouble(), 5.0, DecimalFormat("0"), false, getString(R.string.action_percentage))
                container.addView(view)
                view
            }

            2   -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                val initValue = SafeParse.stringToDouble(editDuration?.editText?.text.toString(), duration)
                editDuration = PlusMinusEditText(viewAdapter, initValue, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, getString(R.string.action_duration_minutes))
                container.addView(view)
                view
            }

            else -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    // check if it can happen that the fragment is never created that hold data?
                    // (you have to swipe past them anyways - but still)
                    val ps = ActionProfileSwitchPreCheck(SafeParse.stringToInt(editTimeshift?.editText?.text.toString()), SafeParse.stringToInt(editPercentage?.editText?.text.toString()), SafeParse.stringToInt(editDuration?.editText?.text.toString()))
                    rxBus.send(EventWearToMobile(ps))
                    showToast(this@ProfileSwitchActivity, R.string.action_profile_switch_confirmation)
                    finishAffinity()
                }
                container.addView(view)
                view
            }
        }

        override fun destroyItem(container: ViewGroup, row: Int, col: Int, view: Any) {
            // Handle this to get the data before the view is destroyed?
            // Object should still be kept by this, just setup for re-init?
            container.removeView(view as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }
    }
}