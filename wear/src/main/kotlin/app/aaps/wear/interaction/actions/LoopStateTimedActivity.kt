package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.widgets.PagerAdapter
import java.text.DecimalFormat
import javax.inject.Inject

class LoopStateTimedActivity : ViewSelectorActivity() {
    @Inject lateinit var aapsLogger: AAPSLogger
    var editDuration: PlusMinusEditText? = null
    var eventData: EventData.LoopStatePreSelect? = null
    var isHours = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getString(DataLayerListenerServiceWear.KEY_ACTION)?.let { action ->
            aapsLogger.info(LTag.WEAR, "LoopStateTimedActivity.onCreate: action=$action")
            eventData = EventData.deserialize(action) as EventData.LoopStatePreSelect
        } ?: aapsLogger.error(LTag.WEAR, "LoopStateTimedActivity.onCreate extras 'actionString' required")
        setAdapter(MyPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyPagerAdapter : PagerAdapter() {

        override fun getPageCount(): Int = 2

        override fun instantiateItem(container: ViewGroup, position: Int): View = when (position) {
            0 -> {
                // Page 0: Input page
                val frameLayout = FrameLayout(applicationContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, frameLayout, true)
                val minValue = (eventData!!.durations.firstOrNull() ?: 60).let {
                    if (it >= 60) {
                        isHours = true
                        it / 60
                    } else it
                }
                val maxValue = (eventData!!.durations.lastOrNull() ?: 240).let {
                    if (isHours) it / 60 else it
                }

                val title = if (isHours)
                    getString(R.string.action_duration_h)
                else
                    getString(app.aaps.core.ui.R.string.duration_min_label)
                editDuration = PlusMinusEditText(
                    viewAdapter,
                    minValue.toDouble(), minValue.toDouble(),
                    maxValue.toDouble(),
                    when (minValue) {
                        30 -> listOf(30.0, 60.0, 90.0)
                        15 -> listOf(15.0, 30.0, 45.0)
                        else -> listOf(1.0, 2.0, 3.0)
                    },
                    DecimalFormat("0"), false, title)

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

                        rxBus.send(
                            EventWearToMobile(
                                EventData.LoopStateSelected(
                                    eventData!!.timeStamp,
                                    eventData!!.stateIndex,
                                    SafeParse.stringToDouble(editDuration?.editText?.text.toString()).toInt().let {
                                        if (isHours) it * 60 else it
                                    }
                                )
                            ))
                        showToast(this@LoopStateTimedActivity, R.string.action_loop_state_selected)
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
