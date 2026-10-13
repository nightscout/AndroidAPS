package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionECarbsPreCheck
import app.aaps.core.interfaces.utils.SafeParse.stringToDouble
import app.aaps.core.interfaces.utils.SafeParse.stringToInt
import app.aaps.core.keys.IntKey
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.widgets.PagerAdapter
import java.text.DecimalFormat

class ECarbActivity : ViewSelectorActivity() {

    var editCarbs: PlusMinusEditText? = null
    var editStartTime: PlusMinusEditText? = null
    var editDuration: PlusMinusEditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAdapter(MyPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyPagerAdapter : PagerAdapter() {

        override fun getPageCount(): Int = 4

        val increment1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble()
        val increment2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble()
        val stepValues = listOf(1.0, increment1, increment2)

        override fun instantiateItem(container: ViewGroup, position: Int): View = when (position) {
            0    -> {
                // Page 0: Carbs input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, true)
                val initValue = stringToDouble(editCarbs?.editText?.text.toString(), 0.0)
                val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()
                editCarbs = PlusMinusEditText(viewAdapter, initValue, 0.0, maxCarbs, stepValues, DecimalFormat("0"), true, getString(R.string.action_carbs_gram))
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }

            1    -> {
                // Page 1: Start time input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, false)
                val initValue = stringToDouble(editStartTime?.editText?.text.toString(), 0.0)
                editStartTime = PlusMinusEditText(viewAdapter, initValue, -60.0, 300.0, 15.0, DecimalFormat("0"), false, getString(R.string.action_start_minutes))
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }

            2    -> {
                // Page 2: Duration input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, false)
                val initValue = stringToDouble(editDuration?.editText?.text.toString(), 0.0)
                editDuration = PlusMinusEditText(viewAdapter, initValue, 0.0, 8.0, 1.0, DecimalFormat("0"), false, getString(R.string.action_duration_hours))
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }

            else -> {
                // Page 3: Confirm page
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

                        val bolus = ActionECarbsPreCheck(
                            stringToInt(editCarbs?.editText?.text.toString()),
                            stringToInt(editStartTime?.editText?.text.toString()),
                            stringToInt(editDuration?.editText?.text.toString())
                        )
                        rxBus.send(EventWearToMobile(bolus))
                        showToast(this@ECarbActivity, R.string.action_ecarb_confirmation)
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