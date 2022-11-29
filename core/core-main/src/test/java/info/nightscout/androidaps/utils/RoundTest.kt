package info.nightscout.androidaps.utils

import info.nightscout.interfaces.utils.Round
import org.junit.Assert
import org.junit.jupiter.api.Test

class RoundTest {

    @Test
    fun roundToTest() {
        Assert.assertEquals(0.55, Round.roundTo(0.54, 0.05), 0.00000000000000000001)
        Assert.assertEquals(-3.26, Round.roundTo(-3.2553715764602713, 0.01), 0.00000000000000000001)
        Assert.assertEquals(0.816, Round.roundTo(0.8156666666666667, 0.001), 0.00000000000000000001)
        Assert.assertEquals(0.235, Round.roundTo(0.235, 0.001), 0.00000000000000000001)
        Assert.assertEquals(0.3, Round.roundTo(0.3, 0.1), 0.00000000000000001)
        Assert.assertEquals(0.0017, Round.roundTo(0.0016960652144170627, 0.0001), 0.00000000000000000001)
        Assert.assertEquals(0.0078, Round.roundTo(0.007804436682291013, 0.0001), 0.00000000000000000001)
        Assert.assertEquals(0.6, Round.roundTo(0.6, 0.05), 0.00000000000000000001)
        Assert.assertEquals(1.0, Round.roundTo(1.49, 1.0), 0.00000000000000000001)
        Assert.assertEquals(0.0, Round.roundTo(0.0, 1.0), 0.00000000000000000001)
    }

    @Test
    fun floorToTest() {
        Assert.assertEquals(0.5, Round.floorTo(0.54, 0.05), 0.00000001)
        Assert.assertEquals(1.0, Round.floorTo(1.59, 1.0), 0.00000001)
        Assert.assertEquals(0.0, Round.floorTo(0.0, 1.0), 0.00000001)
    }

    @Test
    fun ceilToTest() {
        Assert.assertEquals(0.6, Round.ceilTo(0.54, 0.1), 0.00000001)
        Assert.assertEquals(2.0, Round.ceilTo(1.49999, 1.0), 0.00000001)
        Assert.assertEquals(0.0, Round.ceilTo(0.0, 1.0), 0.00000001)
    }

    @Test
    fun isSameTest() {
        Assert.assertTrue(Round.isSame(0.54, 0.54))
    }
}