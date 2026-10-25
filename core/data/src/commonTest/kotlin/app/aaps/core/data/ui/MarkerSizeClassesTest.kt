package app.aaps.core.data.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `kotlin.test`, not Truth or JUnit 5, because this `commonTest` also runs on Kotlin/Native.
 */
class MarkerSizeClassesTest {

    @Test
    fun `ofMax cuts the range from zero into thirds`() {
        // 0..6, cuts at 2 and 4: the smallest amount does not move the cuts
        val classes = MarkerSizeClasses.ofMax(listOf(3.0, 6.0))!!
        assertEquals(2.0, classes.smallBelow)
        assertEquals(4.0, classes.largeFrom)

        assertEquals(MarkerTier.SMALL, classes.of(1.9))
        assertEquals(MarkerTier.MEDIUM, classes.of(2.0))
        assertEquals(MarkerTier.MEDIUM, classes.of(3.0))
        assertEquals(MarkerTier.LARGE, classes.of(4.0))
        assertEquals(MarkerTier.LARGE, classes.of(6.0))
    }

    @Test
    fun `ofMax draws two near doses the same size`() {
        // 0.20 and 0.25 against a day whose largest is 0.25: both large, not smallest and largest
        val classes = MarkerSizeClasses.ofMax(listOf(0.20, 0.25))!!

        assertEquals(MarkerTier.LARGE, classes.of(0.20))
        assertEquals(MarkerTier.LARGE, classes.of(0.25))
    }

    @Test
    fun `the largest of the day is large also on its own`() {
        assertEquals(MarkerTier.LARGE, MarkerSizeClasses.ofMax(listOf(0.5))!!.of(0.5))
    }

    @Test
    fun `cuts from the phone are used as they are`() {
        val classes = MarkerSizeClasses(smallBelow = 3.0, largeFrom = 5.0)

        assertEquals(MarkerTier.SMALL, classes.of(0.0))
        assertEquals(MarkerTier.LARGE, classes.of(100.0))
    }

    @Test
    fun `nothing to measure against gives no classes`() {
        assertNull(MarkerSizeClasses.ofMax(emptyList()))
        assertNull(MarkerSizeClasses.ofMax(listOf(0.0)))
        assertNull(MarkerSizeClasses.ofMax(null))
        assertNull(MarkerSizeClasses.ofMax(-1.0))
    }
}
