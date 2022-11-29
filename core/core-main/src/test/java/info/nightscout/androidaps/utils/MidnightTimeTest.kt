package info.nightscout.androidaps.utils

import info.nightscout.interfaces.utils.MidnightTime
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.util.Calendar

class MidnightTimeTest {

    @Test fun calc() {
        // We get real midnight
        val now = System.currentTimeMillis()
        Assert.assertTrue(now >= MidnightTime.calc())
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calc()
        Assert.assertEquals(c[Calendar.HOUR_OF_DAY].toLong(), 0)
        Assert.assertEquals(c[Calendar.MINUTE].toLong(), 0)
        Assert.assertEquals(c[Calendar.SECOND].toLong(), 0)
        Assert.assertEquals(c[Calendar.MILLISECOND].toLong(), 0)
    }

    @Test fun calc_time() {
        // We get real midnight
        val now = System.currentTimeMillis()
        val midnight = MidnightTime.calc(now)
        Assert.assertTrue(now >= midnight)
        val c = Calendar.getInstance()
        c.timeInMillis = MidnightTime.calc(now)
        Assert.assertEquals(c[Calendar.HOUR_OF_DAY].toLong(), 0)
        Assert.assertEquals(c[Calendar.MINUTE].toLong(), 0)
        Assert.assertEquals(c[Calendar.SECOND].toLong(), 0)
        Assert.assertEquals(c[Calendar.MILLISECOND].toLong(), 0)
        // Assure we get the same time from cache
        Assert.assertEquals(midnight, MidnightTime.calc(now))
    }

    @Test fun resetCache() {
        val now = System.currentTimeMillis()
        MidnightTime.calc(now)
        MidnightTime.resetCache()
        Assert.assertEquals(0, MidnightTime.times.size().toLong())
    }

    @Test fun log() {
        val now = System.currentTimeMillis()
        MidnightTime.calc(now)
        Assert.assertTrue(MidnightTime.log().startsWith("Hits:"))
    }
}