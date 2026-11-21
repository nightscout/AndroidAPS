package app.aaps.core.interfaces.source

import kotlin.test.Test
import kotlin.test.assertEquals

/** The sensor age limits from a known sensor lifetime: red at the end, orange a day or a month before it. */
class SensorLifetimeTest {

    @Test
    fun aSensorOfDaysWarnsADayBeforeItsEnd() {
        val libre = 14 * 24
        assertEquals(13 * 24, SensorLifetime.warningHours(libre))
        assertEquals(14 * 24, SensorLifetime.criticalHours(libre))
    }

    @Test
    fun aSensorOfMonthsWarnsAMonthBeforeItsEnd() {
        assertEquals(150 * 24, SensorLifetime.warningHours(180 * 24))
        assertEquals(335 * 24, SensorLifetime.warningHours(365 * 24))
        assertEquals(365 * 24, SensorLifetime.criticalHours(365 * 24))
    }

    @Test
    fun aThirtyDaySensorIsStillAShortOne() {
        assertEquals(29 * 24, SensorLifetime.warningHours(30 * 24))
    }
}
