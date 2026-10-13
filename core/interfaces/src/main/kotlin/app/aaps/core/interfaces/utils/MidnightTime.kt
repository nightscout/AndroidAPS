package app.aaps.core.interfaces.utils

import androidx.annotation.VisibleForTesting
import androidx.collection.LongSparseArray
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object MidnightTime {

    @VisibleForTesting
    val times = LongSparseArray<Long>()

    private const val THRESHOLD = 100000

    /**
     * Epoch time of last midnight
     *
     * @return epoch millis
     */
    fun calc(): Long =
        LocalDateTime.now().atZone(ZoneId.systemDefault())
            .with(LocalTime.of(0, 0, 0, 0))
            .toInstant().toEpochMilli()

    /**
     * Today's time with 'minutes' from midnight
     *
     * @param minutes minutes to add
     * @return epoch millis of today with hh:mm:00
     */
    fun calcMidnightPlusMinutes(minutes: Int): Long {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return LocalDateTime.now().atZone(ZoneId.systemDefault())
            .with(LocalTime.of(h, m, 0, 0))
            .toInstant().toEpochMilli()
    }

    /**
     * Epoch time of last midnight before 'time'
     *
     * @param time time of the day
     * @return epoch millis
     */
    fun calc(time: Long): Long {
        synchronized(times) {
            val m = times[time] ?: Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
                .with(LocalTime.of(0, 0, 0, 0))
                .toInstant().toEpochMilli()
            if (times.size() > THRESHOLD) resetCache()
            return m
        }
    }

    /**
     * Epoch time of last midnight 'days' back
     *
     * @param daysBack how many days back
     * @return epoch millis of midnight
     */
    fun calcDaysBack(daysBack: Long): Long =
        LocalDateTime.now().atZone(ZoneId.systemDefault())
            .with(LocalTime.of(0, 0, 0, 0))
            .minusDays(daysBack)
            .toInstant().toEpochMilli()

    /**
     * Epoch time of last midnight 'days' back from time
     *
     * @param time start time
     * @param daysBack how many days back
     * @return epoch millis of midnight
     */
    fun calcDaysBack(time: Long, daysBack: Long): Long =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            .with(LocalTime.of(0, 0, 0, 0))
            .minusDays(daysBack)
            .toInstant().toEpochMilli()

    @VisibleForTesting
    fun resetCache() {
        times.clear()
    }
}