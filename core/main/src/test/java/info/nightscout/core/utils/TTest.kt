package info.nightscout.core.utils

import info.nightscout.shared.utils.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.math.abs

@Suppress("SpellCheckingInspection")
class TTest {

    @Test fun toUnits() {
        Assertions.assertEquals(1, T.msecs(1000).secs())
        Assertions.assertEquals(1, T.secs(60).mins())
        Assertions.assertEquals(1, T.mins(60).hours())
        Assertions.assertEquals(1, T.hours(24).days())
        Assertions.assertEquals(24, T.days(1).hours())
        Assertions.assertEquals(60000, T.mins(1).msecs())
    }

    @Test fun now() {
        Assertions.assertTrue(abs(T.now().msecs() - System.currentTimeMillis()) < 5000)
    }

    @Test fun additions() {
        val nowMsecs = System.currentTimeMillis()
        val now = T.msecs(nowMsecs)
        Assertions.assertEquals(now.plus(T.secs(5)).msecs(), nowMsecs + 5 * 1000)
        Assertions.assertEquals(now.plus(T.mins(5)).msecs(), nowMsecs + 5 * 60 * 1000)
        Assertions.assertEquals(now.plus(T.hours(5)).msecs(), nowMsecs + 5 * 60 * 60 * 1000)
        Assertions.assertEquals(now.plus(T.days(5)).msecs(), nowMsecs + 5 * 24 * 60 * 60 * 1000)
    }

    @Test fun subtractions() {
        val nowMsecs = System.currentTimeMillis()
        val now = T.msecs(nowMsecs)
        Assertions.assertEquals(now.minus(T.secs(5)).msecs(), nowMsecs - 5 * 1000)
        Assertions.assertEquals(now.minus(T.mins(5)).msecs(), nowMsecs - 5 * 60 * 1000)
        Assertions.assertEquals(now.minus(T.hours(5)).msecs(), nowMsecs - 5 * 60 * 60 * 1000)
        Assertions.assertEquals(now.minus(T.days(5)).msecs(), nowMsecs - 5 * 24 * 60 * 60 * 1000)
    }
}