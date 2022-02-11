package info.nightscout.androidaps.plugins.pump.eopatch.vo

import java.util.ArrayList

abstract class SegmentEntity<T : SegmentEntity<T>> {

    var startMinute = 0L
    var endMinute = 0L

    internal val startIndex: Int
        get() = (startMinute / TIME_BASE).toInt()

    internal val endIndex: Int
        get() = (endMinute / TIME_BASE).toInt()

    fun getDuration(): Long {
        return endMinute - startMinute
    }
    /**
     * Empty Segment 여부 돌려주기
     */
    internal abstract val isEmpty: Boolean

    internal fun isMinuteIncluding(minute: Long): Boolean {
        return startMinute <= minute && minute < endMinute
    }

    internal fun isSame(target: SegmentEntity<*>): Boolean {
        return startMinute == target.startMinute && target.endMinute == endMinute
    }

    internal fun hasSame(target: SegmentEntity<*>): Boolean {
        return startMinute == target.startMinute || target.endMinute == endMinute
    }

    /**
     * 타겟을 완전히 포함?
     */
    internal fun canCover(target: SegmentEntity<*>): Boolean {
        return startMinute <= target.startMinute && target.endMinute <= endMinute
    }

    /**
     * 타겟에 완전히 덮임.
     */
    internal fun isCoveredBy(target: SegmentEntity<*>): Boolean {
        return target.canCover(this)
    }

    /**
     * 타겟과 걸침? 주의 canCover 또는 isCoveredBy 이 true 이면 이것도 true 임.
     * 즉 canCover 와 isCoveredBy 가 미리 확인된 이후에 호출 할 것.
     */
    internal fun isOverlapped(target: SegmentEntity<*>): Boolean {
        return startMinute < target.endMinute && target.startMinute < endMinute
    }

    /**
     * 타겟을 완전히 포함하고 있지 않고 걸침.
     */
    internal fun isPartiallyNotFullyIncluding(target: SegmentEntity<*>): Boolean {
        return isOverlapped(target) && !canCover(target) && !isCoveredBy(target)
    }

    /**
     * target segment 를 뺸다. target 은 한방향으로만 걸쳐야 함.
     * 즉 isPartiallyNotFullyIncluding(target) 이 false 여야 한다.
     * 양 끝단 중 한 쪽이 같은 경우가 발생하는데 그 경우는 splitBy 에서 한쪽만 생성되므로 OK!
     */
    internal fun subtract(target: SegmentEntity<*>, validCheck: Boolean) {

        if (validCheck) {
            if (!isPartiallyNotFullyIncluding(target)) {
                return
            }
        }

        if (target.startMinute <= startMinute) {
            startMinute = target.endMinute
        } else if (endMinute <= target.endMinute) {
            endMinute = target.startMinute
        }
    }

    /**
     * target segment 에 의해서 쪼개져서 생성되는 segment list 를 돌려준다.
     */
    internal fun splitBy(target: T, validCheck: Boolean): List<T>? {

        if (validCheck) {
            if (!canCover(target)) {
                return null
            }
        }

        val result = ArrayList<T>()

        if (startMinute < target.startMinute) {
            result.add(duplicate(startMinute, target.startMinute))
        }

        if (target.endMinute < endMinute) {
            result.add(duplicate(target.endMinute, endMinute))
        }

        return result
    }

    /**
     * JoinedSegment 를 포함하는가?
     */
    internal fun includes(segment: JoinedSegment): Boolean {
        return startMinute <= segment.startMinute && segment.endMinute <= endMinute
    }

    /**
     * 같은 값을 가지는 주어진 시간으로 새로운 세그먼트 생성.
     */
    internal abstract fun duplicate(startMinute: Long, endMinute: Long): T

    /**
     * copy constructor
     */
    internal abstract fun deep(): T

    /**
     * 값이 같은가?
     */
    internal abstract fun equalValue(segment: T): Boolean

    /**
     * 이 세그먼트의 값을 JoinedSegment 에 적용한다.
     */
    internal abstract fun apply(segment: JoinedSegment)

    companion object {

        internal val TIME_BASE = 30
    }
}
