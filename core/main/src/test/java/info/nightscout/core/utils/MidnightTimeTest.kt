package info.nightscout.core.utils

import info.nightscout.interfaces.utils.MidnightTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Calendar

class MidnightTimeTest {

    @Test fun calc() {
        // We get real midnight
        val now = System.currentTimeMillis()
        Assertions.assertTrue(now >= MidnightTime.calc())
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calc()
        Assertions.assertEquals(c[Calendar.HOUR_OF_DAY].toLong(), 0)
        Assertions.assertEquals(c[Calendar.MINUTE].toLong(), 0)
        Assertions.assertEquals(c[Calendar.SECOND].toLong(), 0)
        Assertions.assertEquals(c[Calendar.MILLISECOND].toLong(), 0)
    }

    @Test fun calc_time() {
        // We get real midnight
        val now = System.currentTimeMillis()
        val midnight = MidnightTime.calc(now)
        Assertions.assertTrue(now >= midnight)
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calc(now)
        Assertions.assertEquals(c[Calendar.HOUR_OF_DAY].toLong(), 0)
        Assertions.assertEquals(c[Calendar.MINUTE].toLong(), 0)
        Assertions.assertEquals(c[Calendar.SECOND].toLong(), 0)
        Assertions.assertEquals(c[Calendar.MILLISECOND].toLong(), 0)
        // Assure we get the same time from cache
        Assertions.assertEquals(midnight, MidnightTime.calc(now))
    }

    @Test fun resetCache() {
        val now = System.currentTimeMillis()
        MidnightTime.calc(now)
        MidnightTime.resetCache()
        Assertions.assertEquals(0, MidnightTime.times.size().toLong())
    }

    @Test fun log() {
        val now = System.currentTimeMillis()
        MidnightTime.calc(now)
        Assertions.assertTrue(MidnightTime.log().startsWith("Hits:"))
    }
}