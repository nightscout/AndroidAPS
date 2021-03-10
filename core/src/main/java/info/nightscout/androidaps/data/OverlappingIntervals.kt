package info.nightscout.androidaps.data

import info.nightscout.androidaps.interfaces.Interval

class OverlappingIntervals<T : Interval> : Intervals<T> {

    constructor() : super()
    constructor(other: Intervals<T>) {
        rawData = other.rawData.clone()
    }

    @Synchronized override fun merge() {
        var needToCut = false
        var cutTime: Long = 0
        for (index in rawData.size() - 1 downTo 0) { //begin with newest
            val cur: Interval = rawData.valueAt(index)
            if (cur.isEndingEvent) {
                needToCut = true
                cutTime = cur.start()
            } else {
                //event that is no EndingEvent might need to be stopped by an ending event
                if (needToCut && cur.end() > cutTime) {
                    cur.cutEndTo(cutTime)
                }
            }
        }
    }

    @Synchronized override fun getValueByInterval(time: Long): T? {
        for (index in rawData.size() - 1 downTo 0) { //begin with newest
            val cur = rawData.valueAt(index)
            if (cur!!.match(time)) {
                return cur
            }
        }
        return null
    }
}