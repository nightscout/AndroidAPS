package info.nightscout.androidaps.plugins.treatments

import android.content.Context
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class CarbsGenerator @Inject constructor(
    private val resourceHelper: ResourceHelper,
    private val activePlugin: ActivePluginProvider,
    private val commandQueue: CommandQueueProvider,
    private val context: Context
) {

    fun generateCarbs(amount: Int, startTime: Long, duration: Int, notes: String) {
        var remainingCarbs = amount.toLong()
        val ticks = duration * 4 //duration guaranteed to be integer greater zero
        for (i in 0 until ticks) {
            val carbTime = startTime + i * 15 * 60 * 1000
            val smallCarbAmount = (1.0 * remainingCarbs / (ticks - i)).roundToInt() //on last iteration (ticks-i) is 1 -> smallCarbAmount == remainingCarbs
            remainingCarbs -= smallCarbAmount.toLong()
            if (smallCarbAmount > 0) createCarb(smallCarbAmount, carbTime, TherapyEvent.Type.MEAL_BOLUS, notes)
        }
    }

    fun createCarb(carbs: Int, time: Long, eventType: TherapyEvent.Type, notes: String) {
        val carbInfo = DetailedBolusInfo()
        carbInfo.timestamp = time
        carbInfo.eventType = eventType
        carbInfo.carbs = carbs.toDouble()
        carbInfo.context = context
        carbInfo.notes = notes
        if (activePlugin.activePump.pumpDescription.storesCarbInfo && carbInfo.timestamp <= DateUtil.now() && carbInfo.timestamp > DateUtil.now() - T.mins(2).msecs()) {
            commandQueue.bolus(carbInfo, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        ErrorHelperActivity.runAlarm(context, result.comment, resourceHelper.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                    }
                }
            })
        } else {
            // Don't send to pump if it is in the future or more than 5 minutes in the past
            // as pumps might return those as as "now" when reading the history.
            activePlugin.activeTreatments.addToHistoryTreatment(carbInfo, false)
        }
    }
}