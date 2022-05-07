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
import info.nightscout.shared.weardata.EventData.ActionWizardPreCheck
import java.text.DecimalFormat

class WizardActivity : ViewSelectorActivity() {

    var editCarbs: PlusMinusEditText? = null
    var editPercentage: PlusMinusEditText? = null
    var hasPercentage = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyGridViewPagerAdapter())
        hasPercentage = sp.getBoolean(R.string.key_wizard_percentage, false)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapter() {

        override fun getColumnCount(arg0: Int): Int = if (hasPercentage) 3 else 2
        override fun getRowCount(): Int = 1

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): Any {
            return if (col == 0) {
                val view = getInflatedPlusMinusView(container)
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48)
                editCarbs = if (editCarbs == null) {
                    PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, 0.0, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), false)
                } else {
                    val def = SafeParse.stringToDouble(editCarbs?.editText?.text.toString())
                    PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), false)
                }
                setLabelToPlusMinusView(view, getString(R.string.action_carbs))
                container.addView(view)
                view.requestFocus()
                view
            } else if (col == 1 && hasPercentage) {
                val view = getInflatedPlusMinusView(container)
                val percentage = sp.getInt(getString(R.string.key_bolus_wizard_percentage), 100)
                editPercentage = if (editPercentage == null) {
                    PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, percentage.toDouble(), 50.0, 150.0, 1.0, DecimalFormat("0"), false)
                } else {
                    val def = SafeParse.stringToDouble(editPercentage?.editText?.text.toString())
                    PlusMinusEditText(view, R.id.amountfield, R.id.plusbutton, R.id.minusbutton, def, 50.0, 150.0, 1.0, DecimalFormat("0"), false)
                }
                setLabelToPlusMinusView(view, getString(R.string.action_percentage))
                container.addView(view)
                view
            } else {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    val action = ActionWizardPreCheck(
                        SafeParse.stringToInt(editCarbs?.editText?.text.toString()),
                        SafeParse.stringToInt(editPercentage?.editText?.text.toString())
                    )
                    rxBus.send(EventWearToMobile(action))
                    showToast(this@WizardActivity, R.string.action_wizard_confirmation)
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