@file:Suppress("DEPRECATION")

package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionECarbsPreCheck
import app.aaps.core.interfaces.utils.SafeParse.stringToDouble
import app.aaps.core.interfaces.utils.SafeParse.stringToInt
import app.aaps.core.keys.IntKey
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.nondeprecated.GridPagerAdapterNonDeprecated
import java.text.DecimalFormat

class ECarbActivity : ViewSelectorActivity() {

    var editCarbs: PlusMinusEditText? = null
    var editStartTime: PlusMinusEditText? = null
    var editDuration: PlusMinusEditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyGridViewPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapterNonDeprecated() {

        override fun getColumnCount(arg0: Int): Int = 4
        override fun getRowCount(): Int = 1

        val increment1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble()
        val increment2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble()
        val stepValues = listOf(1.0, increment1, increment2)

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when (col) {
            0    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, true)
                val view = viewAdapter.root
                var initValue = stringToDouble(editCarbs?.editText?.text.toString(), 0.0)
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()
                editCarbs = PlusMinusEditText(viewAdapter, initValue, 0.0, maxCarbs, stepValues, DecimalFormat("0"), true, getString(R.string.action_carbs_gram))
                container.addView(view)
                view.requestFocus()
                view
            }

            1    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                var initValue = stringToDouble(editStartTime?.editText?.text.toString(), 0.0)
                editStartTime = PlusMinusEditText(viewAdapter, initValue, -60.0, 300.0, 15.0, DecimalFormat("0"), false, getString(R.string.action_start_minutes))
                container.addView(view)
                view
            }

            2    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                var initValue = stringToDouble(editDuration?.editText?.text.toString(), 0.0)
                editDuration = PlusMinusEditText(viewAdapter, initValue, 0.0, 8.0, 1.0, DecimalFormat("0"), false, getString(R.string.action_duration_hours))
                container.addView(view)
                view
            }

            else -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    // check if it can happen that the fragment is never created that hold data?
                    // (you have to swipe past them anyways - but still)
                    val bolus = ActionECarbsPreCheck(
                        stringToInt(editCarbs?.editText?.text.toString()),
                        stringToInt(editStartTime?.editText?.text.toString()),
                        stringToInt(editDuration?.editText?.text.toString())
                    )
                    rxBus.send(EventWearToMobile(bolus))
                    showToast(this@ECarbActivity, R.string.action_ecarb_confirmation)
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