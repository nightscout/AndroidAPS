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
        assertThat(c[Calendar.HOUR_OF_DAY].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.MINUTE].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.SECOND].toLong()).isEqualTo(0L)
        assertThat(c[Calendar.MILLISECOND].toLong()).isEqualTo(0L)
        // Assure we get the same time from cache
        assertThat(midnight).isEqualTo(MidnightTime.calc(now))
    }

    @Test fun resetCache() {
        val now = System.currentTimeMillis()
        MidnightTime.calc(now)
        MidnightTime.resetCache()
        assertThat(MidnightTime.times.size().toLong()).isEqualTo(0L)
    }

    @Test fun log() {
        val now = System.currentTimeMillis()
        MidnightTime.calc(now)
        assertThat(MidnightTime.log()).startsWith("Hits:")
    }
}
