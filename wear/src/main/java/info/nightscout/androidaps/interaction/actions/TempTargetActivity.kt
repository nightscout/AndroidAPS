@file:Suppress("DEPRECATION")

package info.nightscout.androidaps.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import info.nightscout.androidaps.R
import info.nightscout.rx.events.EventWearToMobile
import info.nightscout.androidaps.interaction.utils.EditPlusMinusViewAdapter
import info.nightscout.androidaps.interaction.utils.PlusMinusEditText
import info.nightscout.androidaps.nondeprecated.GridPagerAdapterNonDeprecated
import info.nightscout.shared.SafeParse
import info.nightscout.rx.weardata.EventData.ActionTempTargetPreCheck
import java.text.DecimalFormat

class TempTargetActivity : ViewSelectorActivity() {

    var lowRange: PlusMinusEditText? = null
    var highRange: PlusMinusEditText? = null
    var time: PlusMinusEditText? = null
    var isMGDL = false
    var isSingleTarget = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyGridViewPagerAdapter())
        isMGDL = sp.getBoolean(R.string.key_units_mgdl, true)
        isSingleTarget = sp.getBoolean(R.string.key_single_target, true)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapterNonDeprecated() {

        override fun getColumnCount(arg0: Int): Int {
            return if (isSingleTarget) 3 else 4
        }

        override fun getRowCount(): Int {
            return 1
        }

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when {
            col == 0                    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                val initValue = SafeParse.stringToDouble(time?.editText?.text.toString(), 60.0)
                time = PlusMinusEditText(viewAdapter, initValue, 0.0, 24 * 60.0, 5.0, DecimalFormat("0"), false, getString(R.string.action_duration))
                container.addView(view)
                view.requestFocus()
                view
            }

            col == 1                    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                val title = if (isSingleTarget) getString(R.string.action_target) else getString(R.string.action_low)
                if (isMGDL) {
                    var initValue = SafeParse.stringToDouble(lowRange?.editText?.text.toString(), 100.0)
                    lowRange = PlusMinusEditText(viewAdapter, initValue, 72.0, 180.0, 1.0, DecimalFormat("0"), false, title)
                } else {
                    var initValue = SafeParse.stringToDouble(lowRange?.editText?.text.toString(), 5.5)
                    lowRange = PlusMinusEditText(viewAdapter, initValue, 4.0, 10.0, 0.1, DecimalFormat("#0.0"), false, title)
                }
                container.addView(view)
                view
            }

            col == 2 && !isSingleTarget -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, false)
                val view = viewAdapter.root
                if (isMGDL) {
                    var initValue = SafeParse.stringToDouble(highRange?.editText?.text.toString(), 100.0)
                    highRange = PlusMinusEditText(viewAdapter, initValue, 72.0, 180.0, 1.0, DecimalFormat("0"), false, getString(R.string.action_high))
                } else {
                    var initValue = SafeParse.stringToDouble(highRange?.editText?.text.toString(), 5.5)
                    highRange = PlusMinusEditText(viewAdapter, initValue, 4.0, 10.0, 0.1, DecimalFormat("#0.0"), false, getString(R.string.action_high))
                }
                container.addView(view)
                view
            }

            else                        -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    // check if it can happen that the fragment is never created that hold data?
                    // (you have to swipe past them anyways - but still)
                    val action = ActionTempTargetPreCheck(
                        ActionTempTargetPreCheck.TempTargetCommand.MANUAL,
                        isMGDL,
                        SafeParse.stringToInt(time?.editText?.text.toString()),
                        SafeParse.stringToDouble(lowRange?.editText?.text.toString()),
                        if (isSingleTarget) SafeParse.stringToDouble(lowRange?.editText?.text.toString()) else SafeParse.stringToDouble(highRange?.editText?.text.toString())
                    )
                    rxBus.send(EventWearToMobile(action))
                    showToast(this@TempTargetActivity, R.string.action_tempt_confirmation)
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
