package app.aaps.wear.watchfaces.utils

import android.support.wearable.complications.ComplicationData as WireComplicationData
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Truth table for [shouldDropSmallImage] - whether a complication's small image is dropped so the
 * monochromatic icon renders instead.
 *
 * This decision is the seam between "let the library render it" and "rewrite the payload first".
 * Each test names the device case it covers, so a change that breaks one says which behaviour it
 * regressed.
 */
internal class ComplicationPayloadPolicyTest {

    private fun decide(
        wireType: Int = WireComplicationData.TYPE_SHORT_TEXT,
        hasSmallImage: Boolean = true,
        hasIcon: Boolean = true,
        iconIsLoadable: Boolean = true,
        iconColorRequested: Boolean = false
    ) = shouldDropSmallImage(wireType, hasSmallImage, hasIcon, iconIsLoadable, iconColorRequested)

    // --- the ranged-value layout bug: both images render as nothing unless one is removed ---------

    @Test fun `ranged value drops the small image even when no colour was asked for`() {
        assertThat(decide(wireType = WireComplicationData.TYPE_RANGED_VALUE)).isTrue()
    }

    @Test fun `goal progress and weighted elements take the same route as ranged value`() {
        assertThat(decide(wireType = WireComplicationData.TYPE_GOAL_PROGRESS)).isTrue()
        assertThat(decide(wireType = WireComplicationData.TYPE_WEIGHTED_ELEMENTS)).isTrue()
    }

    // --- every other type renders the small image correctly, so it is kept unless asked otherwise -

    @Test fun `short text keeps the provider's own image when no icon colour was requested`() {
        // The exercise complication: white icon from the provider, untinted. Dropping it here would
        // make the icon vanish.
        assertThat(decide(iconColorRequested = false)).isFalse()
    }

    @Test fun `short text drops the small image once an icon colour is requested`() {
        // The heart rate complication: iconColor makes the red heart follow the CWF's colour.
        assertThat(decide(iconColorRequested = true)).isTrue()
    }

    @Test fun `long text behaves like short text, not like ranged value`() {
        assertThat(decide(wireType = WireComplicationData.TYPE_LONG_TEXT, iconColorRequested = false)).isFalse()
        assertThat(decide(wireType = WireComplicationData.TYPE_LONG_TEXT, iconColorRequested = true)).isTrue()
    }

    // --- guards that keep the rewrite from ever being a downgrade ---------------------------------

    @Test fun `nothing is dropped when there is no small image to drop`() {
        assertThat(decide(hasSmallImage = false, iconColorRequested = true)).isFalse()
        assertThat(decide(hasSmallImage = false, wireType = WireComplicationData.TYPE_RANGED_VALUE)).isFalse()
    }

    @Test fun `nothing is dropped when there is no icon to fall back on`() {
        assertThat(decide(hasIcon = false, iconColorRequested = true)).isFalse()
        assertThat(decide(hasIcon = false, wireType = WireComplicationData.TYPE_RANGED_VALUE)).isFalse()
    }

    @Test fun `a working image is never traded for an icon that cannot draw`() {
        // hasIcon only says the field is set. A provider carrying an unloadable icon must not cost the
        // complication the image it already had, or the slot ends up with no image at all.
        assertThat(decide(iconIsLoadable = false, iconColorRequested = true)).isFalse()
        assertThat(decide(iconIsLoadable = false, wireType = WireComplicationData.TYPE_RANGED_VALUE)).isFalse()
    }

    // --- types with no icon/small image pairing at all --------------------------------------------

    @Test fun `image only types are never rewritten`() {
        // SMALL_IMAGE and PHOTO_IMAGE are drawn by the watch face itself; their payload carries no
        // monochromatic icon, so the question never arises.
        assertThat(decide(wireType = WireComplicationData.TYPE_SMALL_IMAGE, hasIcon = false)).isFalse()
        assertThat(decide(wireType = WireComplicationData.TYPE_LARGE_IMAGE, hasIcon = false)).isFalse()
    }

    @Test fun `an icon-only complication keeps its icon`() {
        assertThat(decide(wireType = WireComplicationData.TYPE_ICON, hasSmallImage = false)).isFalse()
    }
}
