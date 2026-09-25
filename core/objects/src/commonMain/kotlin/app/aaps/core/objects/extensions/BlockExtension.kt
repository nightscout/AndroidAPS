package app.aaps.core.objects.extensions

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

/** The shape [DateUtil.toSeconds] can actually read: ASCII digits, `HH:MM`. It matches with `find`. */
private val READABLE_TIME = Regex("""\d+:\d+""")

/**
 * Start-of-block seconds for entry [index].
 *
 * `timeAsSeconds` is the source of truth, and `time` is read only when it is missing or zero. That
 * matches how the profile editor always worked - it wrote `time` but read the rows back from
 * `timeAsSeconds`, never from `time`.
 *
 * Preferring the integer matters because `time` is the fragile field. Older builds formatted it with
 * the device locale's digits, so a profile saved under ar-SA (a locale AAPS ships) holds `"٠٦:٠٠"`,
 * and [DateUtil.toSeconds] matches ASCII `\d` only, answering 0 without complaining. The document is
 * re-rendered from whatever is parsed here, so a `time` we cannot read would collapse every block to
 * 00:00 and publish that to the sync channel and to Nightscout. The damage would be silent, because
 * all-zero starts still divide evenly by 3600 and so still parse.
 *
 * Treating **zero** as "ask `time`" is what keeps this from failing the same way in reverse: an
 * uploader that omits `timeAsSeconds` from a defaults-filled struct writes 0 for every entry, and
 * trusting that would flatten the schedule just as badly. A real midnight block is unaffected - its
 * `time` reads back as 0 anyway - and when neither field can be read, a present-but-zero
 * `timeAsSeconds` is still honoured.
 *
 * Null only when neither field is usable, which the callers turn into an invalid profile.
 */
private fun JsonArray.startSecondsAt(index: Int, dateUtil: DateUtil): Int? {
    val entry = getOrNull(index) as? JsonObject
    val seconds = (entry?.get("timeAsSeconds") as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
    if (seconds != null && seconds != 0) return seconds
    val time = (entry?.get("time") as? JsonPrimitive)?.content
    if (time != null && READABLE_TIME.containsMatchIn(time)) return dateUtil.toSeconds(time)
    // Absent -> null (invalid profile); present and zero -> a genuine midnight block.
    return seconds
}

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
        val tas = jsonArray.startSecondsAt(index, dateUtil) ?: return null
        val nextTas = jsonArray.startSecondsAt(index + 1, dateUtil) ?: return null
        val value = jsonArray.valueAt(index) ?: return null
        if (tas % 3600 != 0) return null
        if (nextTas % 3600 != 0) return null
        ret.add(index, Block((nextTas - tas) * 1000L, value))
    }
    val lastTas = jsonArray.startSecondsAt(jsonArray.size - 1, dateUtil) ?: return null
    val lastValue = jsonArray.valueAt(jsonArray.size - 1) ?: return null
    ret.add(jsonArray.size - 1, Block((T.hours(24).secs() - lastTas) * 1000L, lastValue))
    return ret
}

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
        val tas1 = jsonArray1.startSecondsAt(index, dateUtil) ?: return null
        val value1 = jsonArray1.valueAt(index) ?: return null
        val nextTas1 = jsonArray1.startSecondsAt(index + 1, dateUtil) ?: return null
        val tas2 = jsonArray2.startSecondsAt(index, dateUtil) ?: return null
        val value2 = jsonArray2.valueAt(index) ?: return null
        if (tas1 != tas2) return null
        if (tas1 % 3600 != 0) return null
        if (nextTas1 % 3600 != 0) return null
        ret.add(index, TargetBlock((nextTas1 - tas1) * 1000L, value1, value2))
    }
    val lastIndex = jsonArray1.size - 1
    val lastTas1 = jsonArray1.startSecondsAt(lastIndex, dateUtil) ?: return null
    val lastValue1 = jsonArray1.valueAt(lastIndex) ?: return null
    val lastValue2 = jsonArray2.valueAt(lastIndex) ?: return null
    ret.add(lastIndex, TargetBlock((T.hours(24).secs() - lastTas1) * 1000L, lastValue1, lastValue2))
    return ret
}

/**
 * `HH:00` for a whole-hour offset from midnight.
 *
 * Padded by hand rather than with `String.format`: `%02d` renders digits using the *locale's* zero
 * digit, so under a locale such as ar-EG it writes `٠١:٠٠`, which no reader can parse back. The
 * profile editor used to build times that way, so this also removes that trap.
 */
private fun hourLabel(secondsFromMidnight: Int): String {
    val hours = secondsFromMidnight / 3600
    return (if (hours < 10) "0$hours" else "$hours") + ":00"
}

/**
 * Render a schedule back to the `[{time, timeAsSeconds, value}, …]` array shape used by both
 * Nightscout and the stored profile document.
 *
 * Blocks carry durations, not start times, so each entry's start is the running sum of the blocks
 * before it. Inverse of [blockFromJson] for any schedule that one accepts.
 */
fun List<Block>.toJsonArray(): JsonArray {
    var startSeconds = 0
    return buildJsonArray {
        for (block in this@toJsonArray) {
            add(timeValueObject(startSeconds, block.amount))
            startSeconds += T.msecs(block.duration).secs().toInt()
        }
    }
}

/** [toJsonArray] for the low side of a target schedule. */
fun List<TargetBlock>.lowToJsonArray(): JsonArray = toJsonArray { it.lowTarget }

/** [toJsonArray] for the high side of a target schedule. */
fun List<TargetBlock>.highToJsonArray(): JsonArray = toJsonArray { it.highTarget }

private fun List<TargetBlock>.toJsonArray(select: (TargetBlock) -> Double): JsonArray {
    var startSeconds = 0
    return buildJsonArray {
        for (block in this@toJsonArray) {
            add(timeValueObject(startSeconds, select(block)))
            startSeconds += T.msecs(block.duration).secs().toInt()
        }
    }
}

private fun timeValueObject(startSeconds: Int, value: Double): JsonObject =
    buildJsonObject {
        put("time", hourLabel(startSeconds))
        put("timeAsSeconds", startSeconds)
        put("value", value)
    }

/** One block covering the whole day — the shape a freshly seeded or damaged schedule takes. */
fun singleBlock(value: Double): List<Block> = listOf(Block(T.hours(24).msecs(), value))

/** [singleBlock] for a target schedule. */
fun singleTargetBlock(low: Double, high: Double): List<TargetBlock> = listOf(TargetBlock(T.hours(24).msecs(), low, high))