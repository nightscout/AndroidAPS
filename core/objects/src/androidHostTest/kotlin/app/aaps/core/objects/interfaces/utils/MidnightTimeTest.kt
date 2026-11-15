package app.aaps.core.objects.interfaces.utils

import app.aaps.core.interfaces.utils.MidnightTime
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import java.util.Calendar

class MidnightTimeTest {

    @Test fun calc() {
        // We get real midnight
        val now = System.currentTimeMillis()
        assertThat(MidnightTime.last(TimeZone.currentSystemDefault())).isAtMost(now)
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.last(TimeZone.currentSystemDefault())
        assertThat(c[Calendar.HOUR_OF_DAY].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.MINUTE].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.SECOND].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.MILLISECOND].toLong()).isEqualTo(0L)
    }

    @Test fun calc_time() {
        // We get real midnight
        val now = System.currentTimeMillis()
        val midnight = MidnightTime.before(now, TimeZone.currentSystemDefault())
        assertThat(midnight).isAtMost(now)
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.before(now, TimeZone.currentSystemDefault())
        assertThat(c[Calendar.HOUR_OF_DAY]).isEqualTo(0)
        assertThat(c[Calendar.MINUTE]).isEqualTo(0)
        assertThat(c[Calendar.SECOND]).isEqualTo(0)
        assertThat(c[Calendar.MILLISECOND]).isEqualTo(0)
        // Assure we get the same time from cache
        assertThat(midnight).isEqualTo(MidnightTime.before(now, TimeZone.currentSystemDefault()))
    }

    @Test fun calcMidnightPlusMinutesTest() {
        // Use 301 minutes (5:01) to avoid DST spring-forward gap (typically 2:00-3:00)
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.plusMinutes(301, TimeZone.currentSystemDefault())
        assertThat(c[Calendar.HOUR_OF_DAY]).isEqualTo(5)
        assertThat(c[Calendar.MINUTE]).isEqualTo(1)
        assertThat(c[Calendar.SECOND]).isEqualTo(0)
        assertThat(c[Calendar.MILLISECOND]).isEqualTo(0)
    }

    @Test fun calcDaysBackTest() {
        // We get real midnight
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.before(now, TimeZone.currentSystemDefault())
        c.add(Calendar.DAY_OF_MONTH, -5)
        assertThat(c[Calendar.HOUR_OF_DAY]).isEqualTo(0)
        assertThat(c[Calendar.MINUTE]).isEqualTo(0)
        assertThat(c[Calendar.SECOND]).isEqualTo(0)
        assertThat(c[Calendar.MILLISECOND]).isEqualTo(0)
        assertThat(MidnightTime.daysBack(5, TimeZone.currentSystemDefault())).isEqualTo(c.timeInMillis)
    }

    /**
     * `calc` used to consult a cache that nothing ever wrote to, so it always recomputed. The cache
     * is gone; this pins that repeated calls still agree, which is all the cache could ever have
     * promised.
     */
    @Test fun repeatedCallsAgree() {
        val now = System.currentTimeMillis()

        assertThat(MidnightTime.before(now, TimeZone.currentSystemDefault())).isEqualTo(MidnightTime.before(now, TimeZone.currentSystemDefault()))
    }
}
