package app.aaps.core.objects.extensions

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

private fun getShiftedTimeSecs(originalSeconds: Int, timeShiftHours: Int): Int {
    var shiftedSeconds = originalSeconds - timeShiftHours * 60 * 60
    shiftedSeconds = (shiftedSeconds + 24 * 60 * 60) % (24 * 60 * 60)
    return shiftedSeconds
}

fun List<Block>.shiftBlock(multiplier: Double, timeShiftHours: Int): List<Block> {
    val newList = arrayListOf<Block>()
    for (hour in 0..23) newList.add(Block(1000L * 60 * 60, blockValueBySeconds(hour * 3600, multiplier, timeShiftHours)))
    for (i in newList.indices.reversed()) {
        if (i > 0)
            if (newList[i].amount == newList[i - 1].amount) {
                newList[i - 1].duration += newList[i].duration
                newList.removeAt(i)
            }
    }
    return newList
}

fun List<TargetBlock>.shiftTargetBlock(timeShiftHours: Int): List<TargetBlock> {
    val newList = arrayListOf<TargetBlock>()
    for (hour in 0..23)
        newList.add(TargetBlock(1000L * 60 * 60, lowTargetBlockValueBySeconds(hour * 3600, timeShiftHours), highTargetBlockValueBySeconds(hour * 3600, timeShiftHours)))
    for (i in newList.indices.reversed()) {
        if (i > 0)
            if (newList[i].lowTarget == newList[i - 1].lowTarget && newList[i].highTarget == newList[i - 1].highTarget) {
                newList[i - 1].duration += newList[i].duration
                newList.removeAt(i)
            }
    }
    return newList
}

fun List<Block>.blockValueBySeconds(secondsFromMidnight: Int, multiplier: Double, timeShiftHours: Int): Double {
    var elapsed = 0L
    val shiftedSeconds = getShiftedTimeSecs(secondsFromMidnight, timeShiftHours)
    forEach {
        if (shiftedSeconds >= elapsed && shiftedSeconds < elapsed + T.msecs(it.duration).secs()) return it.amount * multiplier
        elapsed += T.msecs(it.duration).secs()
    }
    return last().amount * multiplier
}

fun List<TargetBlock>.targetBlockValueBySeconds(secondsFromMidnight: Int, timeShiftHours: Int): Double {
    var elapsed = 0L
    val shiftedSeconds = getShiftedTimeSecs(secondsFromMidnight, timeShiftHours)
    forEach {
        if (shiftedSeconds >= elapsed && shiftedSeconds < elapsed + T.msecs(it.duration).secs()) return (it.lowTarget + it.highTarget) / 2.0
        elapsed += T.msecs(it.duration).secs()
    }
    return (last().lowTarget + last().highTarget) / 2.0
}

fun List<TargetBlock>.lowTargetBlockValueBySeconds(secondsFromMidnight: Int, timeShiftHours: Int): Double {
    var elapsed = 0L
    val shiftedSeconds = getShiftedTimeSecs(secondsFromMidnight, timeShiftHours)
    forEach {
        if (shiftedSeconds >= elapsed && shiftedSeconds < elapsed + T.msecs(it.duration).secs()) return it.lowTarget
        elapsed += T.msecs(it.duration).secs()
    }
    return last().lowTarget
}

fun List<TargetBlock>.highTargetBlockValueBySeconds(secondsFromMidnight: Int, timeShiftHours: Int): Double {
    var elapsed = 0L
    val shiftedSeconds = getShiftedTimeSecs(secondsFromMidnight, timeShiftHours)
    forEach {
        if (shiftedSeconds >= elapsed && shiftedSeconds < elapsed + T.msecs(it.duration).secs()) return it.highTarget
        elapsed += T.msecs(it.duration).secs()
    }
    return last().highTarget
}

/**
 * Bridge from `org.json` to kotlinx at the module boundary.
 *
 * Goes via text because that is the only lossless thing both libraries agree on, and the cost is
 * paid once per schedule rather than per entry. Returns null rather than throwing so callers keep the
 * existing "unreadable means invalid profile" behaviour.
 */
private fun JSONArray?.toKotlinxOrNull(): JsonArray? =
    this?.let { runCatching { Json.parseToJsonElement(it.toString()) as? JsonArray }.getOrNull() }

/**
 * `time` as org.json's `getString` would give it: a quoted string comes back unquoted, and a bare
 * number comes back as its text. Null when absent or not a primitive.
 */
private fun JsonArray.timeAt(index: Int): String? =
    ((getOrNull(index) as? JsonObject)?.get("time") as? JsonPrimitive)?.content

/**
 * `value` as org.json's `getDouble` would give it - coercing a quoted number, which real Nightscout
 * and AAPS documents both contain. Null when absent or not numeric, which the callers turn into an
 * invalid profile.
 */
private fun JsonArray.valueAt(index: Int): Double? =
    ((getOrNull(index) as? JsonObject)?.get("value") as? JsonPrimitive)?.content?.toDoubleOrNull()

/**
 * Reads a profile schedule (`basal`, `sens`, `carbratio`) into blocks.
 *
 * The logic lives on kotlinx [JsonArray] so it can eventually move to commonMain; the `org.json`
 * entry point below is a thin adapter kept while the callers still hold `JSONArray`. This is the
 * inside-out step of the org.json migration - the parsing rules move first, the contracts follow.
 *
 * Behaviour is deliberately unchanged and is pinned by `ProfileJsonCharacterizationTest`:
 * - a value may be a real number OR a quoted string; both occur in the wild
 * - a schedule not aligned to whole hours is rejected
 * - anything unreadable yields **null** (an invalid profile), never an exception
 */
fun blockFromJson(jsonArray: JsonArray?, dateUtil: DateUtil): List<Block>? {
    if (jsonArray == null || jsonArray.isEmpty()) return null
    val ret = ArrayList<Block>(jsonArray.size)
    for (index in 0 until jsonArray.size - 1) {
        val tas = dateUtil.toSeconds(jsonArray.timeAt(index) ?: return null)
        val nextTas = dateUtil.toSeconds(jsonArray.timeAt(index + 1) ?: return null)
        val value = jsonArray.valueAt(index) ?: return null
        if (tas % 3600 != 0) return null
        if (nextTas % 3600 != 0) return null
        ret.add(index, Block((nextTas - tas) * 1000L, value))
    }
    val lastTas = dateUtil.toSeconds(jsonArray.timeAt(jsonArray.size - 1) ?: return null)
    val lastValue = jsonArray.valueAt(jsonArray.size - 1) ?: return null
    ret.add(jsonArray.size - 1, Block((T.hours(24).secs() - lastTas) * 1000L, lastValue))
    return ret
}

/** `org.json` entry point. Converts once at the boundary and delegates to [blockFromJson]. */
fun blockFromJsonArray(jsonArray: JSONArray?, dateUtil: DateUtil): List<Block>? =
    blockFromJson(jsonArray.toKotlinxOrNull(), dateUtil)

fun targetBlockFromJsonArray(jsonArray1: JSONArray?, jsonArray2: JSONArray?, dateUtil: DateUtil): List<TargetBlock>? {
    val size1 = jsonArray1?.length() ?: return null
    val size2 = jsonArray2?.length() ?: return null
    if (size1 != size2) return null
    val ret = ArrayList<TargetBlock>(size1)
    try {
        for (index in 0 until jsonArray1.length() - 1) {
            val o1: JSONObject = jsonArray1.getJSONObject(index)
            val tas1 = dateUtil.toSeconds(o1.getString("time"))
            val value1 = o1.getDouble("value")
            val next1 = jsonArray1.getJSONObject(index + 1)
            val nextTas1 = dateUtil.toSeconds(next1.getString("time"))
            val o2 = jsonArray2.getJSONObject(index)
            val tas2 = dateUtil.toSeconds(o2.getString("time"))
            val value2 = o2.getDouble("value")
            if (tas1 != tas2) return null
            if (tas1 % 3600 != 0) return null
            if (nextTas1 % 3600 != 0) return null
            ret.add(index, TargetBlock((nextTas1 - tas1) * 1000L, value1, value2))
        }
        val last1 = jsonArray1.getJSONObject(jsonArray1.length() - 1)
        val lastTas1 = dateUtil.toSeconds(last1.getString("time"))
        val value1 = last1.getDouble("value")
        val last2 = jsonArray2.getJSONObject(jsonArray2.length() - 1)
        val value2 = last2.getDouble("value")
        ret.add(jsonArray1.length() - 1, TargetBlock((T.hours(24).secs() - lastTas1) * 1000L, value1, value2))
    } catch (e: Exception) {
        return null
    }
    return ret
}