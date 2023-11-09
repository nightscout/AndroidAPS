package app.aaps.core.interfaces.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar

class MidnightTimeTest {

    @Test fun calc() {
        // We get real midnight
        val now = System.currentTimeMillis()
        assertThat(MidnightTime.calc()).isAtMost(now)
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calc()
        assertThat(c[Calendar.HOUR_OF_DAY].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.MINUTE].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.SECOND].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.MILLISECOND].toLong()).isEqualTo(0L)
    }

    @Test fun calc_time() {
        // We get real midnight
        val now = System.currentTimeMillis()
        val midnight = MidnightTime.calc(now)
        assertThat(midnight).isAtMost(now)
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calc(now)
        assertThat(c[Calendar.HOUR_OF_DAY]).isEqualTo(0)
        assertThat(c[Calendar.MINUTE]).isEqualTo(0)
        assertThat(c[Calendar.SECOND]).isEqualTo(0)
        assertThat(c[Calendar.MILLISECOND]).isEqualTo(0)
        // Assure we get the same time from cache
        assertThat(midnight).isEqualTo(MidnightTime.calc(now))
    }

    @Test fun calcMidnightPlusMinutesTest() {
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calcMidnightPlusMinutes(121)
        assertThat(c[Calendar.HOUR_OF_DAY]).isEqualTo(2)
        assertThat(c[Calendar.MINUTE]).isEqualTo(1)
        assertThat(c[Calendar.SECOND]).isEqualTo(0)
        assertThat(c[Calendar.MILLISECOND]).isEqualTo(0)
    }

    @Test fun calcDaysBackTest() {
        // We get real midnight
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calc(now)
        c.add(Calendar.DAY_OF_MONTH, -5)
        assertThat(c[Calendar.HOUR_OF_DAY]).isEqualTo(0)
        assertThat(c[Calendar.MINUTE]).isEqualTo(0)
        assertThat(c[Calendar.SECOND]).isEqualTo(0)
        assertThat(c[Calendar.MILLISECOND]).isEqualTo(0)
        assertThat(MidnightTime.calcDaysBack(5)).isEqualTo(c.timeInMillis)
    }

    @Test fun resetCache() {
        val now = System.currentTimeMillis()
        MidnightTime.calc(now)
        MidnightTime.resetCache()
        assertThat(MidnightTime.times.size().toLong()).isEqualTo(0L)
    }
}
