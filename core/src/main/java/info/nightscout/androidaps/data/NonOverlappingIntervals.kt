package info.nightscout.androidaps.data

import info.nightscout.androidaps.interfaces.Interval

class NonOverlappingIntervals<T : Interval> : Intervals<T> {

    constructor() : super()
    constructor(other: Intervals<T>) {
        rawData = other.rawData.clone()
    }

    @Synchronized public override fun merge() {
        for (index in 0 until rawData.size() - 1) {
            val i: T = rawData.valueAt(index)
            val startOfNewer = rawData.valueAt(index + 1).start()
            if (i.originalEnd() > startOfNewer) {
                i.cutEndTo(startOfNewer)
            }
        }
    }

    @Synchronized override fun getValueByInterval(time: Long): T? {
        val index = binarySearch(time)
        return if (index >= 0) rawData.valueAt(index) else null
    }
}