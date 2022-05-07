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
import info.nightscout.shared.weardata.EventData.ActionTempTargetPreCheck
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

    private inner class MyGridViewPagerAdapter : GridPagerAdapter() {

        override fun getColumnCount(arg0: Int): Int {
            return if (isSingleTarget) 3 else 4
        }

        override fun getRowCount(): Int {
            return 1
        }

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): Any {
            return if (col == 0) {
                val view = getInflatedPlusMinusView(container)
                time = if (time == null) {
                    PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, 60.0, 0.0, 24 * 60.0, 5.0, DecimalFormat("0"), false)
                } else {
                    val def = SafeParse.stringToDouble(time?.editText?.text.toString())
                    PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0.0, 24 * 60.0, 5.0, DecimalFormat("0"), false)
                }
                setLabelToPlusMinusView(view, getString(R.string.action_duration))
                container.addView(view)
                view.requestFocus()
                view
            } else if (col == 1) {
                val view = getInflatedPlusMinusView(container)
                if (isMGDL) {
                    var def = 100.0
                    if (lowRange != null) def = SafeParse.stringToDouble(lowRange?.editText?.text.toString())
                    lowRange = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 72.0, 180.0, 1.0, DecimalFormat("0"), false)
                } else {
                    var def = 5.5
                    if (lowRange != null) def = SafeParse.stringToDouble(lowRange?.editText?.text.toString())
                    lowRange = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 4.0, 10.0, 0.1, DecimalFormat("#0.0"), false)
                }
                if (isSingleTarget) setLabelToPlusMinusView(view, getString(R.string.action_target))
                else setLabelToPlusMinusView(view, getString(R.string.action_low))
                container.addView(view)
                view
            } else if (col == 2 && !isSingleTarget) {
                val view = getInflatedPlusMinusView(container)
                if (isMGDL) {
                    var def = 100.0
                    if (highRange != null) def = SafeParse.stringToDouble(highRange?.editText?.text.toString())
                    highRange = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 72.0, 180.0, 1.0, DecimalFormat("0"), false)
                } else {
                    var def = 5.5
                    if (highRange != null) def = SafeParse.stringToDouble(highRange?.editText?.text.toString())
                    highRange = PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 4.0, 10.0, 0.1, DecimalFormat("#0.0"), false)
                }
                setLabelToPlusMinusView(view, getString(R.string.action_high))
                container.addView(view)
                view
            } else {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    //check if it can happen that the fragment is never created that hold data?
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