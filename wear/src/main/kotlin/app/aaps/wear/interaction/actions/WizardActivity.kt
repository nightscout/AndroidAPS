package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionWizardPreCheck
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.IntKey
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.widgets.PagerAdapter
import java.text.DecimalFormat

class WizardActivity : ViewSelectorActivity() {

    private var editCarbs: PlusMinusEditText? = null
    private var editPercentage: PlusMinusEditText? = null
    private var hasPercentage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasPercentage = sp.getBoolean(R.string.key_wizard_percentage, false)
        setAdapter(MyPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyPagerAdapter : PagerAdapter() {

        override fun getPageCount(): Int = if (hasPercentage) 3 else 2

        private val increment1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble()
        private val increment2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble()
        private val stepValues = listOf(1.0, increment1, increment2)

        override fun instantiateItem(container: ViewGroup, position: Int): View = when (position) {
            0                  -> createCarbsInputPage()
            1 if hasPercentage -> createPercentageInputPage()
            else               -> createConfirmPage(container)
        }

        private fun createCarbsInputPage(): View {
            val frameLayout = FrameLayout(applicationContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, true)
            val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()
            val initValue = SafeParse.stringToDouble(editCarbs?.editText?.text.toString(), 0.0)

            editCarbs = PlusMinusEditText(
                viewAdapter,
                initValue,
                0.0,
                maxCarbs,
                stepValues,
                DecimalFormat("0"),
                false,
                getString(R.string.action_carbs_gram)
            )

            return viewAdapter.root.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        private fun createPercentageInputPage(): View {
            val frameLayout = FrameLayout(applicationContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, false)
            val percentage = sp.getInt(getString(R.string.key_bolus_wizard_percentage), 100).toDouble()
            val initValue = SafeParse.stringToDouble(editPercentage?.editText?.text.toString(), percentage)

            editPercentage = PlusMinusEditText(
                viewAdapter,
                initValue,
                10.0,
                200.0,
                5.0,
                DecimalFormat("0"),
                false,
                getString(R.string.action_percentage)
            )

            return viewAdapter.root.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        private fun createConfirmPage(container: ViewGroup): View {
            return LayoutInflater.from(applicationContext)
                .inflate(R.layout.action_confirm_ok, container, false)
                .apply {
                    findViewById<ImageView>(R.id.confirmbutton).setOnClickListener { view ->
                        // Visual feedback
                        view.animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .setDuration(150)
                            .start()

                        // Haptic feedback
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)

                        // Send wizard action
                        val percentage = getPercentageValue()
                        val carbs = SafeParse.stringToInt(editCarbs?.editText?.text.toString())
                        rxBus.send(EventWearToMobile(ActionWizardPreCheck(carbs, percentage)))

                        showToast(this@WizardActivity, R.string.action_wizard_confirmation)
                        finishAffinity()
                    }

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
        }

        private fun getPercentageValue(): Int {
            return if (hasPercentage) {
                SafeParse.stringToInt(editPercentage?.editText?.text.toString())
            } else {
                sp.getInt(getString(R.string.key_bolus_wizard_percentage), 100)
            }
        }
    }
}