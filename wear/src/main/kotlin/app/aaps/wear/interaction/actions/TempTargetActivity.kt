package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionTempTargetPreCheck
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.widgets.PagerAdapter
import java.text.DecimalFormat

class TempTargetActivity : ViewSelectorActivity() {

    var lowRange: PlusMinusEditText? = null
    var highRange: PlusMinusEditText? = null
    var time: PlusMinusEditText? = null
    var isMGDL = false
    var isSingleTarget = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isMGDL = sp.getBoolean(R.string.key_units_mgdl, true)
        isSingleTarget = sp.getBoolean(R.string.key_single_target, true)
        setAdapter(MyPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyPagerAdapter : PagerAdapter() {

        override fun getPageCount(): Int = if (isSingleTarget) 3 else 4

        override fun instantiateItem(container: ViewGroup, position: Int): View = when (position) {
            0                    -> {
                // Page 0: Duration input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, false)
                val initValue = SafeParse.stringToDouble(time?.editText?.text.toString(), 60.0)
                time = PlusMinusEditText(viewAdapter, initValue, 0.0, 24 * 60.0, 5.0, DecimalFormat("0"), false, getString(R.string.action_duration_minutes))
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
            1                    -> {
                // Page 1: Low/Target input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, false)
                val unit = if (isMGDL) "mg/dl" else "mmol/l"
                val title = if (isSingleTarget) getString(R.string.action_target_unit, unit) else getString(R.string.action_low_unit, unit)
                if (isMGDL) {
                    val initValue = SafeParse.stringToDouble(lowRange?.editText?.text.toString(), 101.0)
                    lowRange = PlusMinusEditText(viewAdapter, initValue, 72.0, 180.0, 1.0, DecimalFormat("0"), false, title)
                } else {
                    val initValue = SafeParse.stringToDouble(lowRange?.editText?.text.toString(), 5.6)
                    lowRange = PlusMinusEditText(viewAdapter, initValue, 4.0, 10.0, 0.1, DecimalFormat("#0.0"), false, title)
                }
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
            2 if !isSingleTarget -> {
                // Page 2: High input page (only if not single target)
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, false)
                val unit = if (isMGDL) "mg/dl" else "mmol/l"
                if (isMGDL) {
                    val initValue = SafeParse.stringToDouble(highRange?.editText?.text.toString(), 101.0)
                    highRange = PlusMinusEditText(viewAdapter, initValue, 72.0, 180.0, 1.0, DecimalFormat("0"), false, getString(R.string.action_high_unit, unit))
                } else {
                    val initValue = SafeParse.stringToDouble(highRange?.editText?.text.toString(), 5.6)
                    highRange = PlusMinusEditText(viewAdapter, initValue, 4.0, 10.0, 0.1, DecimalFormat("#0.0"), false, getString(R.string.action_high_unit, unit))
                }
                viewAdapter.root.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
            else                 -> {
                // Page 3 (or 2 if single target): Confirm page
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
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        }
    }
}
