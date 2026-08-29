package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TrendArrow
import app.aaps.database.entities.GlucoseValue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

internal class TrendArrowExtensionTest {

    @Test
    fun roundTripFromDomain() {
        TrendArrow.entries.forEach {
            assertEquals(it, it.toDb().fromDb())
        }
    }

    @Test
    fun roundTripFromEntity() {
        GlucoseValue.TrendArrow.entries.forEach {
            assertEquals(it, it.fromDb().toDb())
        }
    }

    @Test
    fun representativeMappings() {
        assertEquals(GlucoseValue.TrendArrow.FLAT, TrendArrow.FLAT.toDb())
        assertEquals(GlucoseValue.TrendArrow.NONE, TrendArrow.NONE.toDb())
        assertEquals(GlucoseValue.TrendArrow.TRIPLE_UP, TrendArrow.TRIPLE_UP.toDb())
        assertEquals(GlucoseValue.TrendArrow.TRIPLE_DOWN, TrendArrow.TRIPLE_DOWN.toDb())

        assertEquals(TrendArrow.SINGLE_UP, GlucoseValue.TrendArrow.SINGLE_UP.fromDb())
        assertEquals(TrendArrow.DOUBLE_DOWN, GlucoseValue.TrendArrow.DOUBLE_DOWN.fromDb())
        assertEquals(TrendArrow.FORTY_FIVE_UP, GlucoseValue.TrendArrow.FORTY_FIVE_UP.fromDb())
    }
}
