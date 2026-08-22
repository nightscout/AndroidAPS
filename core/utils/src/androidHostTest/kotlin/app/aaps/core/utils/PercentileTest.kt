package app.aaps.core.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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
        assertThat(Percentile.percentile(doubleArrayOf(), 0.5)).isWithin(tol).of(0.0)
    }

    @Test
    fun clampsPercentileAtOrBelowZeroToFirst() {
        val arr = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertThat(Percentile.percentile(arr, 0.0)).isWithin(tol).of(1.0)
        assertThat(Percentile.percentile(arr, -1.0)).isWithin(tol).of(1.0)
    }

    @Test
    fun clampsPercentileAtOrAboveOneToLast() {
        val arr = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertThat(Percentile.percentile(arr, 1.0)).isWithin(tol).of(4.0)
        assertThat(Percentile.percentile(arr, 2.0)).isWithin(tol).of(4.0)
    }

    @Test
    fun singleElement() {
        assertThat(Percentile.percentile(doubleArrayOf(5.0), 0.5)).isWithin(tol).of(5.0)
    }

    @Test
    fun medianOfEvenSizeReturnsUpperMiddle() {
        // index = 4*0.5 = 2.0, weight = 0 -> arr[2] (not the classic 2.5 average of the two middles)
        assertThat(Percentile.percentile(doubleArrayOf(1.0, 2.0, 3.0, 4.0), 0.5)).isWithin(tol).of(3.0)
    }

    @Test
    fun medianOfOddSizeInterpolates() {
        // index = 3*0.5 = 1.5 -> 0.5*arr[1] + 0.5*arr[2] = 0.5*2 + 0.5*3
        assertThat(Percentile.percentile(doubleArrayOf(1.0, 2.0, 3.0), 0.5)).isWithin(tol).of(2.5)
    }

    @Test
    fun interpolatesBetweenRanks() {
        // index = 2*0.25 = 0.5 -> 0.5*arr[0] + 0.5*arr[1]
        assertThat(Percentile.percentile(doubleArrayOf(0.0, 100.0), 0.25)).isWithin(tol).of(50.0)
        // index = 2*0.1 = 0.2 -> 0.8*arr[0] + 0.2*arr[1]
        assertThat(Percentile.percentile(doubleArrayOf(0.0, 100.0), 0.1)).isWithin(tol).of(20.0)
        // index = 5*0.5 = 2.5 -> 0.5*arr[2] + 0.5*arr[3]
        assertThat(Percentile.percentile(doubleArrayOf(0.0, 10.0, 20.0, 30.0, 40.0), 0.5)).isWithin(tol).of(25.0)
    }

    @Test
    fun integerIndexReturnsThatRank() {
        // index = 4*0.25 = 1.0, weight 0 -> arr[1]
        assertThat(Percentile.percentile(doubleArrayOf(10.0, 20.0, 30.0, 40.0), 0.25)).isWithin(tol).of(20.0)
    }

    @Test
    fun upperOutOfRangeReturnsLowerRank() {
        // index = 4*0.75 = 3.0, upper = 4 >= size -> arr[3]
        assertThat(Percentile.percentile(doubleArrayOf(10.0, 20.0, 30.0, 40.0), 0.75)).isWithin(tol).of(40.0)
        // index = 2*0.5 = 1.0, upper = 2 >= size -> arr[1]
        assertThat(Percentile.percentile(doubleArrayOf(10.0, 20.0), 0.5)).isWithin(tol).of(20.0)
    }

    @Test
    fun boxedAndPrimitiveOverloadsAgree() {
        val values = doubleArrayOf(1.0, 5.0, 9.0)
        listOf(0.0, 0.1, 0.25, 0.5, 0.75, 1.0).forEach { p ->
            val fromPrimitive = Percentile.percentile(values, p)
            val fromBoxed = Percentile.percentile(values.toTypedArray(), p)
            assertThat(fromBoxed).isWithin(tol).of(fromPrimitive)
        }
    }

    @Test
    fun operatesPositionally_inputMustBePreSorted() {
        // The function does not sort: an unsorted array is read by position, not value order.
        // index = 3*0.5 = 1.5 -> 0.5*arr[1] + 0.5*arr[2] = 0.5*1 + 0.5*2
        assertThat(Percentile.percentile(doubleArrayOf(3.0, 1.0, 2.0), 0.5)).isWithin(tol).of(1.5)
    }
}
