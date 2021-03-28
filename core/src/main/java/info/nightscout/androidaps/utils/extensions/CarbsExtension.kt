package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import kotlin.math.roundToInt

fun Carbs.expandCarbs(): List<Carbs> =
    mutableListOf<Carbs>().also { carbs ->
        if (this.duration == 0L) {
            carbs.add(this)
        } else {
            var remainingCarbs = this.amount
            val ticks = T.msecs(this.duration).mins() * 4 //duration guaranteed to be integer greater zero
            for (i in 0 until ticks) {
                val carbTime = this.timestamp + i * 15 * 60 * 1000
                val smallCarbAmount = (1.0 * remainingCarbs / (ticks - i)).roundToInt() //on last iteration (ticks-i) is 1 -> smallCarbAmount == remainingCarbs
                remainingCarbs -= smallCarbAmount.toLong()
                if (smallCarbAmount > 0)
                    carbs.add(Carbs(
                        timestamp = carbTime,
                        amount = smallCarbAmount.toDouble(),
                        duration = 0
                    ))
            }
        }
    }

fun Carbs.toJson(): JSONObject =
    JSONObject()
        .put("eventType", if (amount < 12) TherapyEvent.Type.CARBS_CORRECTION else TherapyEvent.Type.MEAL_BOLUS)
        .put("carbs", amount)
        .put("created_at", DateUtil.toISOString(timestamp))
        .put("date", timestamp).also {
            if (duration != 0L) it.put("duration", duration)
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }
