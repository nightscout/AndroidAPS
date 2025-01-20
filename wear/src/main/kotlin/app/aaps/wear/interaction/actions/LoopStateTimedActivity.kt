package app.aaps.wear.interaction.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.ActionBolusPreCheck
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.DoubleKey
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.interaction.utils.EditPlusMinusViewAdapter
import app.aaps.wear.interaction.utils.PlusMinusEditText
import app.aaps.wear.nondeprecated.GridPagerAdapterNonDeprecated
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

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
        setAdapter(MyGridViewPagerAdapter())
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private inner class MyGridViewPagerAdapter : GridPagerAdapterNonDeprecated() {

        override fun getColumnCount(arg0: Int): Int = 2
        override fun getRowCount(): Int = 1

        override fun instantiateItem(container: ViewGroup, row: Int, col: Int): View = when (col) {
            0    -> {
                val viewAdapter = EditPlusMinusViewAdapter.getViewAdapter(sp, applicationContext, container, true)
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
                    getString(R.string.action_duration)
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

                val view = viewAdapter.root
                container.addView(view)
                view.requestFocus()
                view
            }

            else -> {
                val view = LayoutInflater.from(applicationContext).inflate(R.layout.action_confirm_ok, container, false)
                val confirmButton = view.findViewById<ImageView>(R.id.confirmbutton)
                confirmButton.setOnClickListener {
                    rxBus.send(EventWearToMobile(
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
                container.addView(view)
                view
            }
        }

        override fun destroyItem(container: ViewGroup, row: Int, col: Int, view: Any) {
            container.removeView(view as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`
    }
}
