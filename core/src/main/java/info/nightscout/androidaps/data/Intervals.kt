package info.nightscout.androidaps.data

import androidx.collection.LongSparseArray
import info.nightscout.androidaps.interfaces.Interval
import java.util.*

/**
 * Created by mike on 09.05.2017.
 */
// Zero duration means end of interval
abstract class Intervals<T : Interval> {

    var rawData: LongSparseArray<T> = LongSparseArray()// oldest at index 0

    @Synchronized fun reset(): Intervals<T> {
        rawData = LongSparseArray()
        return this
    }

    protected abstract fun merge()

    /**
     * The List must be sorted by `T.start()` in ascending order
     */
    @Synchronized fun add(list: List<T>) {
        for (interval in list) {
            rawData.put(interval.start(), interval)
        }
        merge()
    }

    @Synchronized fun add(interval: T) {
        rawData.put(interval.start(), interval)
        merge()
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

    @Synchronized protected fun binarySearch(value: Long): Int {
        var lo = 0
        var hi = rawData.size() - 1
        while (lo <= hi) {
            val mid = lo + hi ushr 1
            val midVal: Interval = rawData.valueAt(mid)
            when {
                midVal.before(value) -> lo = mid + 1
                midVal.after(value)  -> hi = mid - 1
                midVal.match(value)  -> return mid // value found
            }
        }
        return lo.inv() // value not present
    }

    abstract fun getValueByInterval(time: Long): T?

    @Synchronized fun size(): Int = rawData.size()

    @Synchronized operator fun get(index: Int): T? = rawData.valueAt(index)

    @Synchronized fun getReversed(index: Int): T = rawData.valueAt(size() - 1 - index)
}