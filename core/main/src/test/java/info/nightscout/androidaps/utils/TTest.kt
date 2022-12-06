package info.nightscout.androidaps.utils

import info.nightscout.shared.utils.T
import org.junit.Assert
import org.junit.jupiter.api.Test
import kotlin.math.abs

@Suppress("SpellCheckingInspection")
class TTest {

    @Test fun toUnits() {
        Assert.assertEquals(1, T.msecs(1000).secs())
        Assert.assertEquals(1, T.secs(60).mins())
        Assert.assertEquals(1, T.mins(60).hours())
        Assert.assertEquals(1, T.hours(24).days())
        Assert.assertEquals(24, T.days(1).hours())
        Assert.assertEquals(60000, T.mins(1).msecs())
    }

    @Test fun now() {
        Assert.assertTrue(abs(T.now().msecs() - System.currentTimeMillis()) < 5000)
    }

    @Test fun additions() {
        val nowMsecs = System.currentTimeMillis()
        val now = T.msecs(nowMsecs)
        Assert.assertEquals(now.plus(T.secs(5)).msecs(), nowMsecs + 5 * 1000)
        Assert.assertEquals(now.plus(T.mins(5)).msecs(), nowMsecs + 5 * 60 * 1000)
        Assert.assertEquals(now.plus(T.hours(5)).msecs(), nowMsecs + 5 * 60 * 60 * 1000)
        Assert.assertEquals(now.plus(T.days(5)).msecs(), nowMsecs + 5 * 24 * 60 * 60 * 1000)
    }

    @Test fun subtractions() {
        val nowMsecs = System.currentTimeMillis()
        val now = T.msecs(nowMsecs)
        Assert.assertEquals(now.minus(T.secs(5)).msecs(), nowMsecs - 5 * 1000)
        Assert.assertEquals(now.minus(T.mins(5)).msecs(), nowMsecs - 5 * 60 * 1000)
        Assert.assertEquals(now.minus(T.hours(5)).msecs(), nowMsecs - 5 * 60 * 60 * 1000)
        Assert.assertEquals(now.minus(T.days(5)).msecs(), nowMsecs - 5 * 24 * 60 * 60 * 1000)
    }
}