package app.aaps.core.utils

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * [Percentile.percentile] does NOT sort its input - callers pass an already-sorted array. All test
 * inputs here are sorted on purpose (one case documents the "must be pre-sorted" contract).
 *
 * The expected values follow oref0's rank convention `index = size * p` (see Percentile.kt), which is
 * why, for example, the median of an even-size array is the upper-middle element and not the classic
 * average of the two middle values. Do not "correct" these to the (n - 1) * p variant.
 */
class PercentileTest {

    private val tol = 1e-9

    @Test
    fun emptyReturnsZero() {
        assertEquals(0.0, Percentile.percentile(doubleArrayOf(), 0.5), tol)
    }

    @Test
    fun clampsPercentileAtOrBelowZeroToFirst() {
        val arr = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(1.0, Percentile.percentile(arr, 0.0), tol)
        assertEquals(1.0, Percentile.percentile(arr, -1.0), tol)
    }

    @Test
    fun clampsPercentileAtOrAboveOneToLast() {
        val arr = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(4.0, Percentile.percentile(arr, 1.0), tol)
        assertEquals(4.0, Percentile.percentile(arr, 2.0), tol)
    }

    @Test
    fun singleElement() {
        assertEquals(5.0, Percentile.percentile(doubleArrayOf(5.0), 0.5), tol)
    }

    @Test
    fun medianOfEvenSizeReturnsUpperMiddle() {
        // index = 4*0.5 = 2.0, weight = 0 -> arr[2] (not the classic 2.5 average of the two middles)
        assertEquals(3.0, Percentile.percentile(doubleArrayOf(1.0, 2.0, 3.0, 4.0), 0.5), tol)
    }

    @Test
    fun medianOfOddSizeInterpolates() {
        // index = 3*0.5 = 1.5 -> 0.5*arr[1] + 0.5*arr[2] = 0.5*2 + 0.5*3
        assertEquals(2.5, Percentile.percentile(doubleArrayOf(1.0, 2.0, 3.0), 0.5), tol)
    }

    @Test
    fun interpolatesBetweenRanks() {
        // index = 2*0.25 = 0.5 -> 0.5*arr[0] + 0.5*arr[1]
        assertEquals(50.0, Percentile.percentile(doubleArrayOf(0.0, 100.0), 0.25), tol)
        // index = 2*0.1 = 0.2 -> 0.8*arr[0] + 0.2*arr[1]
        assertEquals(20.0, Percentile.percentile(doubleArrayOf(0.0, 100.0), 0.1), tol)
        // index = 5*0.5 = 2.5 -> 0.5*arr[2] + 0.5*arr[3]
        assertEquals(25.0, Percentile.percentile(doubleArrayOf(0.0, 10.0, 20.0, 30.0, 40.0), 0.5), tol)
    }

    @Test
    fun integerIndexReturnsThatRank() {
        // index = 4*0.25 = 1.0, weight 0 -> arr[1]
        assertEquals(20.0, Percentile.percentile(doubleArrayOf(10.0, 20.0, 30.0, 40.0), 0.25), tol)
    }

    @Test
    fun upperOutOfRangeReturnsLowerRank() {
        // index = 4*0.75 = 3.0, upper = 4 >= size -> arr[3]
        assertEquals(40.0, Percentile.percentile(doubleArrayOf(10.0, 20.0, 30.0, 40.0), 0.75), tol)
        // index = 2*0.5 = 1.0, upper = 2 >= size -> arr[1]
        assertEquals(20.0, Percentile.percentile(doubleArrayOf(10.0, 20.0), 0.5), tol)
    }

    @Test
    fun boxedAndPrimitiveOverloadsAgree() {
        val values = doubleArrayOf(1.0, 5.0, 9.0)
        listOf(0.0, 0.1, 0.25, 0.5, 0.75, 1.0).forEach { p ->
            val fromPrimitive = Percentile.percentile(values, p)
            val fromBoxed = Percentile.percentile(values.toTypedArray(), p)
            assertEquals(fromPrimitive, fromBoxed, tol)
        }
    }

    @Test
    fun operatesPositionally_inputMustBePreSorted() {
        // The function does not sort: an unsorted array is read by position, not value order.
        // index = 3*0.5 = 1.5 -> 0.5*arr[1] + 0.5*arr[2] = 0.5*1 + 0.5*2
        assertEquals(1.5, Percentile.percentile(doubleArrayOf(3.0, 1.0, 2.0), 0.5), tol)
    }
}
