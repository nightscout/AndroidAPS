package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.androidaps.plugins.pump.eopatch.AppConstant
import java.util.*
import java.util.function.BiFunction

abstract class SegmentsEntity<T : SegmentEntity<T>> {

    var list: ArrayList<T>

    val segmentCount: Int
        get() = list.size

    /**
     * shallow copied list
     */
    val copiedSegmentList: ArrayList<T>
        get() = ArrayList(list)

    /**
     * deep copied list
     */
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

    init {
        list = ArrayList()
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

        // 중복은 체크할 필요 없음, 세그먼트 추가할 때 중복체크함
//        var lastIndex = -1
        for (seg in list) {
            if (seg.isEmpty) {
                return false
            }

//            val startIndex = seg.startIndex
//            val endIndex = seg.endIndex
////            lastIndex += 1
//            if (lastIndex != startIndex) {
//                return false
//            }
//            lastIndex = endIndex
        }
        return true
    }

    private fun isTimeOverlapped(target: T): Boolean {
        for (seg in list) {
            if (seg.isOverlapped(target)) {
                return true
            }
        }
        return false
    }

    // private fun millsToLocalDateTime(millis: Long): LocalDateTime {
    //     val instant = Instant.ofEpochMilli(millis)
    //     return instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    // }

    // fun getCurrentSegmentRemainMinute(timeStamp: Long): Long {
    //     val localDateTime = millsToLocalDateTime(timeStamp)
    //     val minute = localDateTime.minute
    //
    //     getSegment(minute.toLong())?.let {
    //         val hourMinutes = TimeUnit.HOURS.toMinutes(localDateTime.hour.toLong())
    //         val totalMinutes = hourMinutes + minute
    //         val remain = it.endMinute - totalMinutes
    //
    //         return it.endMinute - totalMinutes
    //     }
    //
    //     return 0
    // }

    // fun getSegmentByTimeStamp(timeStamp: Long): T? {
    //     val minute = millsToLocalDateTime(timeStamp).minute.toLong()
    //     return getSegment(minute)
    // }

    fun getSegment(minute: Long): T? {
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

    fun addSegment(seg: T) {
        if (isTimeOverlapped(seg)) {
            throw Exception()
        }
        list.add(seg)
        Collections.sort(list) { t1, t2 -> java.lang.Long.compare(t1.startMinute, t2.startMinute) }
    }


    /**
     * 세그먼트가 한개인 경우 삭제 불가, 첫 번째 세그먼트는 삭제 불가이므로
     * 항상 mSegmentList.size() >= 2이고 idx는 0이 될 수 없음
     * @param segment
     */
    fun removeSegment(segment: T): Int {
        return _remove(segment, list)
    }

    /**
     * 기존 세그먼트 리스트에 덮으면서 추가함. 호환성을 위한 wrapper 함수.
     * @param segment
     */

    fun addSegmentWithTimeOverlapped(segment: T) {
        _union(segment, list)
    }

    fun addSegmentWithTimeOverlapped(segment: T, oldSegment: T?) {
        if(oldSegment != null) {        // 편집인 경우는 기존 세그먼트 제거
            list.remove(oldSegment)
        }
        _union(segment, list)
    }

    /**
     * 삭제
     * @param target
     */
    @Synchronized
    private fun _remove(target: T, list: ArrayList<T>): Int {
        val idx = list.indexOf(target)

        // idx는 최소 1
        if (idx > 0) {
//            list[idx - 1].endMinute = target.endMinute
            list.remove(target)
            _arrange(list)
            return idx
        } else {
            return -1
        }
    }

    /**
     * 기존 세그먼트 리스트에 덮으면서 추가함.
     * @param target
     */
    @Synchronized
    private fun _union(target: T, list: ArrayList<T>) {

        val toBeAdded = ArrayList<T>()

        val iterator = list.iterator()

        while (iterator.hasNext()) {
            val item = iterator.next()

            if (target.canCover(item)) {
                iterator.remove()
            } else if (target.isCoveredBy(item)) {
                toBeAdded.addAll(item.splitBy(target, false)!!)
                iterator.remove()
            } else if (target.isOverlapped(item)) {
                item.subtract(target, false)
            }
        }

        list.addAll(toBeAdded)
        list.add(target)

        _arrange(list)
    }

    /**
     * 동일 세그먼트 합치기
     */

    @Synchronized
    private fun _arrange(list: ArrayList<T>) {
        val size = list.size

        if (size < 2) {
            return
        }

        val resultList = ArrayList<T>()

        Collections.sort(list) { t1, t2 -> java.lang.Long.compare(t1.startMinute, t2.startMinute) }

        // ADM df_385. 연속된 세그먼트에 동일한 값 설정 가능하도록 합치는 작업 하지 않음 - EOMAPP은 합친다
        var left = list[0]

        for (i in 1 until size) {
            val right = list[i]
            if((left.endMinute == right.startMinute) && left.equalValue(right)) {
                left.endMinute = right.endMinute
            }else {
                resultList.add(left)
                left = right
            }
        }

        resultList.add(left)

        list.clear()
        list.addAll(resultList)
    }

    fun isFullSegment(): Boolean {
        var start = 0L
        var end = 1440L
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

    fun isChangeSegmentByNewSegment(newSegment: T, oldSegment: T?): Boolean {
        list.forEach loop@{
            if(it == oldSegment) {
                return@loop
            }
            if(newSegment.canCover(it)) {
                return true
            }else if(newSegment.isCoveredBy(it)) {
                return true
            }else if(newSegment.isOverlapped(it)) {
                return true
            }
        }
        return false
    }
}
