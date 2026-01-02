package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import app.aaps.wear.widgets.PagerAdapter
import java.text.DecimalFormat
import kotlin.math.roundToInt

class TreatmentActivity : ViewSelectorActivity() {

    var editCarbs: PlusMinusEditText? = null
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

        override fun getPageCount(): Int = 3

        val incrementInsulin1 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement1) * 10).roundToInt() / 10.0
        val incrementInsulin2 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement2) * 10).roundToInt() / 10.0
        val stepValuesInsulin = listOf(0.1, incrementInsulin1, incrementInsulin2)
        val incrementCarbs1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble()
        val incrementCarbs2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble()
        val stepValuesCarbs = listOf(1.0, incrementCarbs1, incrementCarbs2)

        override fun instantiateItem(container: ViewGroup, position: Int): View = when (position) {
            0    -> {
                // Page 0: Insulin input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, true)
                val initValue = stringToDouble(editInsulin?.editText?.text.toString(), 0.0)
                val maxBolus = sp.getDouble(getString(R.string.key_treatments_safety_max_bolus), 3.0)
                editInsulin = PlusMinusEditText(viewAdapter, initValue, 0.0, maxBolus, stepValuesInsulin, DecimalFormat("#0.0"), false, getString(R.string.action_insulin_units))
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }

            1    -> {
                // Page 1: Carbs input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, true)
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()
                val initValue = stringToDouble(editCarbs?.editText?.text.toString(), 0.0)
                editCarbs = PlusMinusEditText(viewAdapter, initValue, -maxCarbs, maxCarbs, stepValuesCarbs, DecimalFormat("0"), false, getString(R.string.action_carbs_gram))
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }

            else -> {
                // Page 2: Confirm page
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

                        val bolus = ActionBolusPreCheck(stringToDouble(editInsulin?.editText?.text.toString()), stringToInt(editCarbs?.editText?.text.toString()))
                        rxBus.send(EventWearToMobile(bolus))
                        showToast(this@TreatmentActivity, R.string.action_treatment_confirmation)
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
