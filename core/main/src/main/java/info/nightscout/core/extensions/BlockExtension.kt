package info.nightscout.core.extensions

import info.nightscout.database.entities.data.Block
import info.nightscout.database.entities.data.TargetBlock
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
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

fun blockFromJsonArray(jsonArray: JSONArray?, dateUtil: DateUtil): List<Block>? {
    val size = jsonArray?.length() ?: return null
    val ret = ArrayList<Block>(size)
    try {
        for (index in 0 until jsonArray.length() - 1) {
            val o = jsonArray.getJSONObject(index)
            val tas = dateUtil.toSeconds(o.getString("time"))
            val next = jsonArray.getJSONObject(index + 1)
            val nextTas = dateUtil.toSeconds(next.getString("time"))
            val value = o.getDouble("value")
            if (tas % 3600 != 0) return null
            if (nextTas % 3600 != 0) return null
            ret.add(index, Block((nextTas - tas) * 1000L, value))
        }
        val last: JSONObject = jsonArray.getJSONObject(jsonArray.length() - 1)
        val lastTas = dateUtil.toSeconds(last.getString("time"))
        val value = last.getDouble("value")
        ret.add(jsonArray.length() - 1, Block((T.hours(24).secs() - lastTas) * 1000L, value))
    } catch (e: Exception) {
        return null
    }
    return ret
}

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