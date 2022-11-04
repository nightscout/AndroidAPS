package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.androidaps.plugins.pump.eopatch.AppConstant
import java.util.*
import java.util.function.BiFunction

abstract class SegmentsEntity<T : SegmentEntity<T>> {
    var list: ArrayList<T> = ArrayList()

    val segmentCount: Int
        get() = list.size

    val copiedSegmentList: ArrayList<T>
        get() = ArrayList(list)

    val deepCopiedSegmentList: ArrayList<T>
        get() {
            val copied = ArrayList<T>()

            for (seg in list) {
                copied.add(seg.deep())
            }

            return copied
        }

    private val timeMinute: Long
        get() {
            val c = Calendar.getInstance()
            val hour = c.get(Calendar.HOUR_OF_DAY)
            val min = c.get(Calendar.MINUTE)

            val segmentIndex = (hour * 60 + min) / 30

            return (segmentIndex * 30).toLong()
        }

    fun hasSegments(): Boolean {
        return list.isNotEmpty()
    }

    fun eachSegmentItem(eachFunc: BiFunction<Int, T, Boolean>) {
        for (seg in list) {
            val startIndex = seg.startIndex
            val endIndex = seg.endIndex
            for (i in startIndex until endIndex) {
                val shouldContinue = eachFunc.apply(i, seg)
                if (!shouldContinue) {
                    break
                }
            }
        }
    }

    fun isValid(allowEmpty: Boolean): Boolean {
        if (!allowEmpty) {
            if (list.isEmpty()) {
                return false
            }
        }

        for (seg in list) {
            if (seg.isEmpty) {
                return false
            }
        }
        return true
    }

    private fun getSegment(minute: Long): T? {
        for (seg in list) {
            if (seg.isMinuteIncluding(minute)) {
                return seg
            }
        }

        return null
    }

    fun getCurrentSegment(): T? {
        return getSegment(timeMinute)
    }

    fun isFullSegment(): Boolean {
        var start = 0L
        val end = 1440L
        for(seg in list){
            if(seg.startMinute == start){
                start = seg.endMinute
            }else{
                return false
            }
        }
        return start == end
    }

    fun getEmptySegment(): Pair<Int, Int> {
        if(list.isNullOrEmpty()) {
            return Pair(0, AppConstant.SEGMENT_COUNT_MAX)
        }
        if(list[0].startIndex != 0) {
            return Pair(0, list[0].startIndex)
        }

        if(list.size == 1) {
            return Pair(list[0].endIndex, AppConstant.SEGMENT_COUNT_MAX)
        }

        for(i in 0 until list.size-1) {
            if(list[i].endIndex != list[i+1].startIndex) {
                return Pair(list[i].endIndex, list[i+1].startIndex)
            }
        }

        return Pair(list[list.size-1].endIndex, 48)
    }
}
