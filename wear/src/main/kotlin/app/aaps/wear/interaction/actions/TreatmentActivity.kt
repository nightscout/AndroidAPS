@file:Suppress("DEPRECATION")

package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionBolusPreCheck
import app.aaps.core.interfaces.utils.SafeParse.stringToDouble
import app.aaps.core.interfaces.utils.SafeParse.stringToInt
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.nondeprecated.GridPagerAdapterNonDeprecated
import java.text.DecimalFormat
import kotlin.math.roundToInt

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

    private inner class MyGridViewPagerAdapter : GridPagerAdapterNonDeprecated() {

        override fun getColumnCount(arg0: Int): Int = 3
        override fun getRowCount(): Int = 1

        val incrementInsulin1 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement1) * 10).roundToInt() / 10.0
        val incrementInsulin2 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement2) * 10).roundToInt() / 10.0
        val stepValuesInsulin = listOf(0.1, incrementInsulin1, incrementInsulin2)
        val incrementCarbs1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble()
        val incrementCarbs2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble()
        val stepValuesCarbs = listOf(1.0, incrementCarbs1, incrementCarbs2)

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when (col) {
            0    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, true)
                val view = viewAdapter.root
                var initValue = stringToDouble(editInsulin?.editText?.text.toString(), 0.0)
                val maxBolus = sp.getDouble(getString(R.string.key_treatments_safety_max_bolus), 3.0)
                editInsulin = PlusMinusEditText(viewAdapter, initValue, 0.0, maxBolus, stepValuesInsulin, DecimalFormat("#0.0"), false, getString(R.string.action_insulin_units))
                container.addView(view)
                view.requestFocus()
                view
            }

            1    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, true)
                val view = viewAdapter.root
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()
                val initValue = stringToDouble(editCarbs?.editText?.text.toString(), 0.0)
                editCarbs = PlusMinusEditText(viewAdapter, initValue, -maxCarbs, maxCarbs, stepValuesCarbs, DecimalFormat("0"), false, getString(R.string.action_carbs_gram))
                container.addView(view)
                view
            }

            else -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    // check if it can happen that the fragment is never created that hold data?
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
