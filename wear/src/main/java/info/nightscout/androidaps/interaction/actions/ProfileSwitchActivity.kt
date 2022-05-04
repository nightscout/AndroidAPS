@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.interaction.actions

import android.os.Bundle
import android.support.wearable.view.GridPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventWearToMobile
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText
import info.nightscout.shared.SafeParse
import info.nightscout.shared.weardata.EventData.ActionProfileSwitchPreCheck
import java.text.DecimalFormat

class ProfileSwitchActivity : ViewSelectorActivity() {

    var editPercentage: PlusMinusEditText? = null
    var editTimeshift: PlusMinusEditText? = null
    var percentage = -1
    var timeshift = -25
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        percentage = intent.extras?.getInt("percentage", -1) ?: -1
        timeshift = intent.extras?.getInt("timeshift", -25) ?: -25
        if (percentage == -1 || timeshift == -25) {
            finish()
            return
        }
        if (timeshift < 0) timeshift += 24
        setAdapter(MyGridViewPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapter() {

        override fun getColumnCount(arg0: Int): Int = 3
        override fun getRowCount(): Int = 1

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): Any {
            return if (col == 0) {
                val view = getInflatedPlusMinusView(container)
                var def = timeshift.toDouble()
                if (editTimeshift != null) {
                    def = SafeParse.stringToDouble(editTimeshift?.editText?.text.toString())
                }
                editTimeshift = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0.0, 23.0, 1.0, DecimalFormat("0"), true, true)
                setLabelToPlusMinusView(view, getString(R.string.action_timeshift))
                container.addView(view)
                view.requestFocus()
                view
            } else if (col == 1) {
                val view = getInflatedPlusMinusView(container)
                var def = percentage.toDouble()
                if (editPercentage != null) {
                    def = SafeParse.stringToDouble(editPercentage?.editText?.text.toString())
                }
                editPercentage = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 30.0, 250.0, 1.0, DecimalFormat("0"), false)
                setLabelToPlusMinusView(view, getString(R.string.action_percentage))
                container.addView(view)
                view
            } else {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    //check if it can happen that the fragment is never created that hold data?
                    // (you have to swipe past them anyways - but still)
                    val ps = ActionProfileSwitchPreCheck(SafeParse.stringToInt(editTimeshift?.editText?.text.toString()), SafeParse.stringToInt(editPercentage?.editText?.text.toString()))
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