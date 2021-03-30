package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

fun Carbs.expandCarbs(): List<Carbs> =
    mutableListOf<Carbs>().also { carbs ->
        if (this.duration == 0L) {
            carbs.add(this)
        } else {
            var remainingCarbs = this.amount
            val ticks = T.msecs(this.duration).hours() * 4 //duration guaranteed to be integer greater zero
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
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

/*
        create fake object with nsID and isValid == false
 */
fun carbsFromNsIdForInvalidating(nsId: String): Carbs =
    carbsFromJson(
        JSONObject()
            .put("mills", 1)
            .put("carbs", -1.0)
            .put("_id", nsId)
            .put("isValid", false)
    )!!

fun carbsFromJson(jsonObject: JSONObject): Carbs? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val duration = JsonHelper.safeGetLong(jsonObject, "duration")
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "carbs") ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    return Carbs(
        timestamp = timestamp,
        duration = duration,
        amount = amount,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}

