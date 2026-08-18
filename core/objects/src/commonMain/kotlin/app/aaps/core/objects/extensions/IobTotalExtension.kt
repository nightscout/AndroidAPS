package app.aaps.core.objects.extensions

import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

operator fun IobTotal.plus(other: IobTotal): IobTotal {
    iob += other.iob
    activity += other.activity
    bolussnooze += other.bolussnooze
    basaliob += other.basaliob
    netbasalinsulin += other.netbasalinsulin
    hightempinsulin += other.hightempinsulin
    netInsulin += other.netInsulin
    extendedBolusInsulin += other.extendedBolusInsulin
    return this
}

fun IobTotal.round(): IobTotal {
    iob = Round.roundTo(iob, 0.001)
    activity = Round.roundTo(activity, 0.0001)
    bolussnooze = Round.roundTo(bolussnooze, 0.0001)
    basaliob = Round.roundTo(basaliob, 0.001)
    netbasalinsulin = Round.roundTo(netbasalinsulin, 0.001)
    hightempinsulin = Round.roundTo(hightempinsulin, 0.001)
    netInsulin = Round.roundTo(netInsulin, 0.001)
    extendedBolusInsulin = Round.roundTo(extendedBolusInsulin, 0.001)
    return this
}

/**
 * Writes [value] only when it is a real number.
 *
 * kotlinx accepts NaN and Infinity and then emits the bare token `NaN`, which is not valid JSON and
 * which `org.json` refuses to re-render - it would turn an uploaded device status into `null`. The
 * `org.json` builder this replaced refused a non-finite value outright, so skipping the key keeps the
 * old "a bad number never reaches the document" rule while leaving the rest of it intact.
 */
private fun JsonObjectBuilder.putIfFinite(key: String, value: Double) {
    if (value.isFinite()) put(key, value)
}

fun IobTotal.jsonObject(dateUtil: DateUtil): JsonObject =
    buildJsonObject {
        putIfFinite("iob", iob)
        putIfFinite("basaliob", basaliob)
        putIfFinite("activity", activity)
        put("time", dateUtil.toISOString(time))
    }

fun IobTotal.determineBasalJsonObject(dateUtil: DateUtil): JsonObject =
    buildJsonObject {
        putIfFinite("iob", iob)
        putIfFinite("basaliob", basaliob)
        putIfFinite("bolussnooze", bolussnooze)
        putIfFinite("activity", activity)
        put("lastBolusTime", lastBolusTime)
        put("time", dateUtil.toISOString(time))
        /*

        This is requested by SMB determine_basal but by based on Scott's info
        it's MDT specific safety check only
        It's causing rounding issues in determine_basal

        JSONObject lastTemp = new JSONObject();
        lastTemp.put("date", lastTempDate);
        lastTemp.put("rate", lastTempRate);
        lastTemp.put("duration", lastTempDuration);
        json.put("lastTemp", lastTemp);
        */
        iobWithZeroTemp?.let { put("iobWithZeroTemp", it.determineBasalJsonObject(dateUtil)) }
    }

fun IobTotal.Companion.combine(bolusIOB: IobTotal, basalIob: IobTotal): IobTotal {
    val result = IobTotal(bolusIOB.time)
    result.iob = bolusIOB.iob + basalIob.basaliob
    result.activity = bolusIOB.activity + basalIob.activity
    result.bolussnooze = bolusIOB.bolussnooze
    result.basaliob = bolusIOB.basaliob + basalIob.basaliob
    result.netbasalinsulin = bolusIOB.netbasalinsulin + basalIob.netbasalinsulin
    result.hightempinsulin = basalIob.hightempinsulin + bolusIOB.hightempinsulin
    result.netInsulin = basalIob.netInsulin + bolusIOB.netInsulin
    result.extendedBolusInsulin = basalIob.extendedBolusInsulin + bolusIOB.extendedBolusInsulin
    result.lastBolusTime = bolusIOB.lastBolusTime
    result.iobWithZeroTemp = basalIob.iobWithZeroTemp
    return result
}
