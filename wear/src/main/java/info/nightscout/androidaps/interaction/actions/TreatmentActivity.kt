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
import info.nightscout.shared.SafeParse.stringToDouble
import info.nightscout.shared.SafeParse.stringToInt
import info.nightscout.shared.weardata.EventData.ActionBolusPreCheck
import java.text.DecimalFormat

class TreatmentActivity : ViewSelectorActivity() {

    var editCarbs: PlusMinusEditText? = null
    var editInsulin: PlusMinusEditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                var def = 0.0
                if (editInsulin != null) def = stringToDouble(editInsulin?.editText?.text.toString())
                val maxBolus = sp.getDouble(getString(R.string.key_treatments_safety_max_bolus), 3.0)
                editInsulin = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0.0, maxBolus, 0.1, DecimalFormat("#0.0"), false)
                setLabelToPlusMinusView(view, getString(R.string.action_insulin))
                container.addView(view)
                view.requestFocus()
                view
            } else if (col == 1) {
                val view = getInflatedPlusMinusView(container)
                var def = 0.0
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48)
                if (editCarbs != null) def = stringToDouble(editCarbs?.editText?.text.toString())
                editCarbs = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), false)
                setLabelToPlusMinusView(view, getString(R.string.action_carbs))
                container.addView(view)
                view
            } else {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    //check if it can happen that the fragment is never created that hold data?
                    // (you have to swipe past them anyways - but still)
                    val bolus = ActionBolusPreCheck(stringToDouble(editInsulin?.editText?.text.toString()), stringToInt(editCarbs?.editText?.text.toString()))
                    rxBus.send(EventWearToMobile(bolus))
                    showToast(this@TreatmentActivity, R.string.action_treatment_confirmation)
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

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
    }
}