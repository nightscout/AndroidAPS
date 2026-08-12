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

/**
 * Expands to 24 one-hour blocks, then merges neighbours that carry the same value.
 *
 * Builds the merged list forward instead of mutating durations in place, so [Block] can stay
 * immutable. Same output as before: consecutive equal-valued hours collapse into one block whose
 * duration is their sum.
 */
fun List<Block>.shiftBlock(multiplier: Double, timeShiftHours: Int): List<Block> {
    val hourly = (0..23).map { blockValueBySeconds(it * 3600, multiplier, timeShiftHours) }
    val merged = ArrayList<Block>(hourly.size)
    for (amount in hourly) {
        val last = merged.lastOrNull()
        if (last != null && last.amount == amount) merged[merged.size - 1] = last.copy(duration = last.duration + HOUR_MS)
        else merged.add(Block(HOUR_MS, amount))
    }
    return merged
}

/** Same merge, for the paired low/high target schedule. */
fun List<TargetBlock>.shiftTargetBlock(timeShiftHours: Int): List<TargetBlock> {
    val hourly = (0..23).map {
        lowTargetBlockValueBySeconds(it * 3600, timeShiftHours) to highTargetBlockValueBySeconds(it * 3600, timeShiftHours)
    }
    val merged = ArrayList<TargetBlock>(hourly.size)
    for ((low, high) in hourly) {
        val last = merged.lastOrNull()
        if (last != null && last.lowTarget == low && last.highTarget == high)
            merged[merged.size - 1] = last.copy(duration = last.duration + HOUR_MS)
        else merged.add(TargetBlock(HOUR_MS, low, high))
    }
    return merged
}

private const val HOUR_MS = 1000L * 60 * 60

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

/**
 * Reads `target_low` and `target_high` together into range blocks.
 *
 * Same inside-out treatment as [blockFromJson]. Rules pinned by `ProfileJsonCharacterizationTest`:
 * the two arrays must be the same length, entry N of each must be for the same time, and every time
 * must fall on the hour.
 *
 * The last entry's times are deliberately NOT compared - the original loop only checked entries
 * 0..n-2 and read just the value of the final one. That is reproduced exactly rather than tightened,
 * so this conversion stays provably behaviour-preserving; see
 * `a differing time on the LAST entry is not caught`.
 */
fun targetBlockFromJson(jsonArray1: JsonArray?, jsonArray2: JsonArray?, dateUtil: DateUtil): List<TargetBlock>? {
    if (jsonArray1 == null || jsonArray2 == null) return null
    if (jsonArray1.isEmpty() || jsonArray1.size != jsonArray2.size) return null
    val ret = ArrayList<TargetBlock>(jsonArray1.size)
    for (index in 0 until jsonArray1.size - 1) {
        val tas1 = dateUtil.toSeconds(jsonArray1.timeAt(index) ?: return null)
        val value1 = jsonArray1.valueAt(index) ?: return null
        val nextTas1 = dateUtil.toSeconds(jsonArray1.timeAt(index + 1) ?: return null)
        val tas2 = dateUtil.toSeconds(jsonArray2.timeAt(index) ?: return null)
        val value2 = jsonArray2.valueAt(index) ?: return null
        if (tas1 != tas2) return null
        if (tas1 % 3600 != 0) return null
        if (nextTas1 % 3600 != 0) return null
        ret.add(index, TargetBlock((nextTas1 - tas1) * 1000L, value1, value2))
    }
    val lastIndex = jsonArray1.size - 1
    val lastTas1 = dateUtil.toSeconds(jsonArray1.timeAt(lastIndex) ?: return null)
    val lastValue1 = jsonArray1.valueAt(lastIndex) ?: return null
    val lastValue2 = jsonArray2.valueAt(lastIndex) ?: return null
    ret.add(lastIndex, TargetBlock((T.hours(24).secs() - lastTas1) * 1000L, lastValue1, lastValue2))
    return ret
}

/** `org.json` entry point. Converts once at the boundary and delegates to [targetBlockFromJson]. */
fun targetBlockFromJsonArray(jsonArray1: JSONArray?, jsonArray2: JSONArray?, dateUtil: DateUtil): List<TargetBlock>? =
    targetBlockFromJson(jsonArray1.toKotlinxOrNull(), jsonArray2.toKotlinxOrNull(), dateUtil)