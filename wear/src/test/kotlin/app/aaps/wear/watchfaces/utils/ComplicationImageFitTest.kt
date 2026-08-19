package app.aaps.wear.watchfaces.utils

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ComplicationImageFit.destination] - where an image of a given intrinsic size lands
 * inside a slot under each fit.
 *
 * Worth locking down because this arithmetic is the whole reason the watch face draws image
 * complications itself instead of leaving them to `ComplicationDrawable`, and because getting it
 * wrong is not obvious on a screenshot: the first implementation shipped a version that cropped *and*
 * distorted a wide image, and it took device comparison against a WFF watch face to notice.
 *
 * Robolectric only for [Rect], which is a stub in plain JVM unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class ComplicationImageFitTest {

    /** The real case that drove this feature: a 450x225 BG graph in a 300x115 px slot. */
    private val wideSlot = Rect(0, 0, 300, 115)

    private val squareSlot = Rect(0, 0, 100, 100)

    @Test
    fun `fit_center keeps the whole wide image, letterboxed, aspect preserved`() {
        val destination = ComplicationImageFit.FIT_CENTER.destination(450, 225, wideSlot)

        // Limiting axis is the height: 115/225 = 0.511, so 450 * 0.511 = 230.
        assertThat(destination.width()).isEqualTo(230)
        assertThat(destination.height()).isEqualTo(115)
        // Aspect ratio of the source survives.
        assertThat(destination.width().toFloat() / destination.height()).isWithin(0.01f).of(2f)
        // Centred in the slot.
        assertThat(destination.centerX()).isEqualTo(wideSlot.centerX())
        assertThat(destination.centerY()).isEqualTo(wideSlot.centerY())
    }

    @Test
    fun `center_crop covers the slot and overflows on the long axis only`() {
        val destination = ComplicationImageFit.CENTER_CROP.destination(450, 225, wideSlot)

        // Limiting axis is now the width: 300/450 = 0.667, so 225 * 0.667 = 150.
        assertThat(destination.width()).isEqualTo(300)
        assertThat(destination.height()).isEqualTo(150)
        assertThat(destination.width().toFloat() / destination.height()).isWithin(0.01f).of(2f)
        // Covers the slot in both directions - nothing of the slot is left unpainted.
        assertThat(destination.width()).isAtLeast(wideSlot.width())
        assertThat(destination.height()).isAtLeast(wideSlot.height())
    }

    @Test
    fun `fit_xy fills the slot exactly and is the only fit that drops the aspect ratio`() {
        val destination = ComplicationImageFit.FIT_XY.destination(450, 225, wideSlot)

        assertThat(destination).isEqualTo(wideSlot)
    }

    @Test
    fun `a square image in a square slot is identical under every fit`() {
        val fits = ComplicationImageFit.entries.map { it.destination(64, 64, squareSlot) }

        assertThat(fits.distinct()).hasSize(1)
        assertThat(fits.first()).isEqualTo(squareSlot)
    }

    @Test
    fun `fit_center never exceeds the slot and center_crop never falls short of it`() {
        // A tall image, the opposite orientation from the BG graph, so the limiting axis swaps.
        val fitCenter = ComplicationImageFit.FIT_CENTER.destination(100, 400, wideSlot)
        val centerCrop = ComplicationImageFit.CENTER_CROP.destination(100, 400, wideSlot)

        assertThat(fitCenter.width()).isAtMost(wideSlot.width())
        assertThat(fitCenter.height()).isAtMost(wideSlot.height())
        assertThat(centerCrop.width()).isAtLeast(wideSlot.width())
        assertThat(centerCrop.height()).isAtLeast(wideSlot.height())
    }

    @Test
    fun `an unmeasurable image falls back to the slot rather than collapsing`() {
        // intrinsicWidth/Height are -1 for a drawable with no intrinsic size; scaling by that would
        // produce a negative or empty rect.
        ComplicationImageFit.entries.forEach { fit ->
            assertThat(fit.destination(-1, -1, wideSlot)).isEqualTo(wideSlot)
            assertThat(fit.destination(0, 0, wideSlot)).isEqualTo(wideSlot)
        }
    }
}
