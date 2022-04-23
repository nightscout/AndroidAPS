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
import info.nightscout.shared.weardata.EventData.ActionECarbsPreCheck
import java.text.DecimalFormat

class CarbActivity : ViewSelectorActivity() {

    var editCarbs: PlusMinusEditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyGridViewPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapter() {

        override fun getColumnCount(arg0: Int): Int = 2
        override fun getRowCount(): Int = 1

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): Any {
            val view: View
            if (col == 0) {
                view = getInflatedPlusMinusView(container)
                var def = 0.0
                if (editCarbs != null) {
                    def = SafeParse.stringToDouble(editCarbs?.editText?.text.toString())
                }
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48)
                editCarbs = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), true)
                setLabelToPlusMinusView(view, getString(R.string.action_carbs))
                container.addView(view)
                view.requestFocus()
            } else {
                view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    // With start time 0 and duration 0
                    val bolus = ActionECarbsPreCheck(SafeParse.stringToInt(editCarbs?.editText?.text.toString()), 0, 0)
                    rxBus.send(EventWearToMobile(bolus))
                    showToast(this@CarbActivity, R.string.action_ecarb_confirmation)
                    finishAffinity()
                }
                container.addView(view)
            }
            return view
        }

        override fun destroyItem(container: ViewGroup, row: Int, col: Int, view: Any) {
            // Handle this to get the data before the view is destroyed?
            // Object should still be kept by this, just setup for re-init?
            container.removeView(view as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
    }
}