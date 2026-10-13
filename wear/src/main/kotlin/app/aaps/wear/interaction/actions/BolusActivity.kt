package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionBolusPreCheck
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.DoubleKey
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.widgets.PagerAdapter
import java.text.DecimalFormat
import kotlin.math.roundToInt

class BolusActivity : ViewSelectorActivity() {

    var editInsulin: PlusMinusEditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyPagerAdapter : PagerAdapter() {

        override fun getPageCount(): Int = 2

        val increment1 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement1) * 10).roundToInt() / 10.0
        val increment2 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement2) * 10).roundToInt() / 10.0
        val stepValues = listOf(0.1, increment1, increment2)

        override fun instantiateItem(container: ViewGroup, position: Int): View = when (position) {
            0    -> {
                // Page 0: Input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, true)
                val initValue = SafeParse.stringToDouble(editInsulin?.editText?.text.toString(), 0.0)
                val maxBolus = sp.getDouble(getString(R.string.key_treatments_safety_max_bolus), 3.0)
                val title = getString(R.string.action_insulin_units)
                editInsulin = PlusMinusEditText(viewAdapter, initValue, 0.0, maxBolus, stepValues, DecimalFormat("#0.0"), false, title)
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }

            else -> {
                // Page 1: Confirm page
                LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false).apply {
                    val confirmButton = findViewById<ImageView>(R.id.confirmbutton)
                    confirmButton.setOnClickListener { view ->
                        // Visual feedback: scale animation
                        view.animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .setDuration(150)
                            .start()

                        // Haptic feedback
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)

                        rxBus.send(EventWearToMobile(ActionBolusPreCheck(SafeParse.stringToDouble(editInsulin?.editText?.text.toString()), 0)))
                        showToast(this@BolusActivity, R.string.action_bolus_confirmation)
                        finishAffinity()
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        }
    }
}
