package info.nightscout.androidaps.data

import androidx.collection.LongSparseArray
import info.nightscout.androidaps.interfaces.Interval
import java.util.*

// Zero duration means profile is valid until is changed
// When no interval match the latest record without duration is used
class ProfileIntervals<T : Interval> {

    private var rawData: LongSparseArray<T> // oldest at index 0

    constructor() {
        rawData = LongSparseArray()
    }

    constructor(other: ProfileIntervals<T>) {
        rawData = other.rawData.clone()
    }

    @Synchronized fun reset(): ProfileIntervals<T> {
        rawData = LongSparseArray()
        return this
    }

    @Synchronized fun add(newInterval: T) {
        if (newInterval.isValid) {
            rawData.put(newInterval.start(), newInterval)
            merge()
        }
    }

    @Synchronized fun add(list: List<T>) {
        for (interval in list) {
            if (interval.isValid) rawData.put(interval.start(), interval)
        }
        merge()
    }

    @Synchronized private fun merge() {
        for (index in 0 until rawData.size() - 1) {
            val i: T = rawData.valueAt(index)
            val startOfNewer = rawData.valueAt(index + 1)!!.start()
            if (i.originalEnd() > startOfNewer) {
                i.cutEndTo(startOfNewer)
            }
        }
    }

    @Synchronized fun getValueToTime(time: Long): Interval? {
        var index = binarySearch(time)
        if (index >= 0) return rawData.valueAt(index)
        // if we request data older than first record, use oldest with zero duration instead
        index = 0
        while (index < rawData.size()) {
            if (rawData.valueAt(index)!!.durationInMsec() == 0L) {
                //log.debug("Requested profile for time: " + DateUtil.dateAndTimeString(time) + ". Providing oldest record: " + rawData.valueAt(0).toString());
                return rawData.valueAt(index)
            }
            index++
        }
        return null
    }

    @get:Synchronized val list: List<T>
        get() {
            val list: MutableList<T> = ArrayList()
            for (i in 0 until rawData.size()) list.add(rawData.valueAt(i))
            return list
        }
    @get:Synchronized val reversedList: List<T>
        get() {
            val list: MutableList<T> = ArrayList()
            for (i in rawData.size() - 1 downTo 0) list.add(rawData.valueAt(i))
            return list
        }

    @Synchronized private fun binarySearch(value: Long): Int {
        if (rawData.size() == 0) return -1
        var lo = 0
        var hi = rawData.size() - 1
        while (lo <= hi) {
            val mid = lo + hi ushr 1
            val midVal: Interval = rawData.valueAt(mid)
            when {
                midVal.match(value)  -> return mid // value found
                midVal.before(value) -> lo = mid + 1
                midVal.after(value)  -> hi = mid - 1
            }
        }
        // not found, try nearest older with duration 0
        lo -= 1
        while (lo >= 0 && lo < rawData.size()) {
            if (rawData.valueAt(lo)!!.isEndingEvent) return lo
            lo--
        }
        return -1 // value not present
    }

    @Synchronized fun size(): Int {
        return rawData.size()
    }

    @Synchronized operator fun get(index: Int): T? {
        return rawData.valueAt(index)
    }

    @Synchronized fun getReversed(index: Int): T {
        return rawData.valueAt(size() - 1 - index)
    }

    override fun toString(): String {
        return rawData.toString()
    }
}