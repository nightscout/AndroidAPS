package app.aaps.pump.eopatch.vo

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

    internal fun canCover(target: SegmentEntity<*>): Boolean {
        return startMinute <= target.startMinute && target.endMinute <= endMinute
    }

    internal fun isCoveredBy(target: SegmentEntity<*>): Boolean {
        return target.canCover(this)
    }

    internal fun isOverlapped(target: SegmentEntity<*>): Boolean {
        return startMinute < target.endMinute && target.startMinute < endMinute
    }

    internal fun isPartiallyNotFullyIncluding(target: SegmentEntity<*>): Boolean {
        return isOverlapped(target) && !canCover(target) && !isCoveredBy(target)
    }

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

    internal fun includes(segment: JoinedSegment): Boolean {
        return startMinute <= segment.startMinute && segment.endMinute <= endMinute
    }

    internal abstract fun duplicate(startMinute: Long, endMinute: Long): T
    internal abstract fun deep(): T
    internal abstract fun equalValue(segment: T): Boolean
    internal abstract fun apply(segment: JoinedSegment)

    companion object {

        const val TIME_BASE = 30
    }
}
