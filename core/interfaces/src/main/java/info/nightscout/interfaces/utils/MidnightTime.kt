package info.nightscout.interfaces.utils

import android.util.LongSparseArray
import java.util.Calendar

object MidnightTime {

    val times = LongSparseArray<Long>()

    private var hits: Long = 0
    private var misses: Long = 0
    private const val THRESHOLD = 100000

    fun calc(): Long {
        val c = Calendar.getInstance()
        c[Calendar.HOUR_OF_DAY] = 0
        c[Calendar.MINUTE] = 0
        c[Calendar.SECOND] = 0
        c[Calendar.MILLISECOND] = 0
        return c.timeInMillis
    }

    fun calcPlusMinutes(minutes: Int): Long {
        val h = minutes / 60
        val m = minutes % 60
        val c = Calendar.getInstance()
        c[Calendar.HOUR_OF_DAY] = h
        c[Calendar.MINUTE] = m
        c[Calendar.SECOND] = 0
        c[Calendar.MILLISECOND] = 0
        return c.timeInMillis
    }

    fun calc(time: Long): Long {
        var m: Long?
        synchronized(times) {
            m = times[time]
            if (m != null) {
                ++hits
                return m!!
            }
            val c = Calendar.getInstance()
            c.timeInMillis = time
            c[Calendar.HOUR_OF_DAY] = 0
            c[Calendar.MINUTE] = 0
            c[Calendar.SECOND] = 0
            c[Calendar.MILLISECOND] = 0
            m = c.timeInMillis
            times.append(time, m)
            ++misses
            if (times.size() > THRESHOLD) resetCache()
        }
        return m!!
    }

    fun resetCache() {
        hits = 0
        misses = 0
        times.clear()
    }

    fun log(): String =
        "Hits: " + hits + " misses: " + misses + " stored: " + times.size()
}