package app.aaps.wear.watchfaces.utils

import androidx.wear.watchface.complications.data.ComplicationType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ComplicationTypePriority.supportedTypes] - the ordered type list handed to each
 * complication slot.
 *
 * The main invariant is that every priority lists **every** type: the list is a preference order the
 * system walks until a provider matches, so dropping a type would make a slot unusable with providers
 * that offer only that type. The ordering assertions cover the case of a provider offering only
 * `ICON,SMALL_IMAGE,SHORT_TEXT,LONG_TEXT`, where `VALUE` order reaches `SHORT_TEXT` (text only, no
 * icon) while `ICON` order reaches the image first.
 *
 * Robolectric because `ComplicationType` touches wire-format constants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class ComplicationTypePriorityTest {

    @Test
    fun `every priority offers every type, so negotiation can never fail`() {
        val expected = ComplicationTypePriority.VALUE.supportedTypes().toSet()
        ComplicationTypePriority.entries.forEach { priority ->
            assertThat(priority.supportedTypes().toSet()).isEqualTo(expected)
            // No duplicates - a repeated type would be dead weight in the walk.
            assertThat(priority.supportedTypes()).hasSize(priority.supportedTypes().toSet().size)
        }
    }

    @Test
    fun `VALUE asks for a gauge before text, which is what keeps heart rate showing a number`() {
        val order = ComplicationTypePriority.VALUE.supportedTypes()

        assertThat(order.first()).isEqualTo(ComplicationType.RANGED_VALUE)
        assertThat(order.indexOf(ComplicationType.RANGED_VALUE)).isLessThan(order.indexOf(ComplicationType.SHORT_TEXT))
        assertThat(order.indexOf(ComplicationType.SHORT_TEXT)).isLessThan(order.indexOf(ComplicationType.MONOCHROMATIC_IMAGE))
    }

    @Test
    fun `ICON asks for an image before text, which is the only way to reach an icon-only provider`() {
        val order = ComplicationTypePriority.ICON.supportedTypes()

        assertThat(order.first()).isEqualTo(ComplicationType.MONOCHROMATIC_IMAGE)
        assertThat(order.indexOf(ComplicationType.MONOCHROMATIC_IMAGE)).isLessThan(order.indexOf(ComplicationType.SHORT_TEXT))
        assertThat(order.indexOf(ComplicationType.SMALL_IMAGE)).isLessThan(order.indexOf(ComplicationType.SHORT_TEXT))
    }

    @Test
    fun `TEXT asks for text before a gauge and before images`() {
        val order = ComplicationTypePriority.TEXT.supportedTypes()

        assertThat(order.first()).isEqualTo(ComplicationType.SHORT_TEXT)
        assertThat(order.indexOf(ComplicationType.SHORT_TEXT)).isLessThan(order.indexOf(ComplicationType.RANGED_VALUE))
        assertThat(order.indexOf(ComplicationType.LONG_TEXT)).isLessThan(order.indexOf(ComplicationType.MONOCHROMATIC_IMAGE))
    }

    @Test
    fun `RANGED_VALUE outranks GOAL_PROGRESS, whose visuals cannot be styled`() {
        // GOAL_PROGRESS renders on its own path, with a hardcoded red over-achievement arc, a dot
        // instead of a filled arc, and progress scaled against target * 1.1 - none of it stylable.
        val order = ComplicationTypePriority.VALUE.supportedTypes()
        if (order.contains(ComplicationType.GOAL_PROGRESS)) {
            assertThat(order.indexOf(ComplicationType.RANGED_VALUE)).isLessThan(order.indexOf(ComplicationType.GOAL_PROGRESS))
        }
    }

    @Test
    fun `for the exercise provider VALUE lands on text while ICON lands on an image`() {
        // What ExerciseOtherWorkoutComplicationProviderService declares, from its manifest.
        val offered = setOf(
            ComplicationType.MONOCHROMATIC_IMAGE, // "ICON"
            ComplicationType.SMALL_IMAGE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT
        )
        fun negotiated(priority: ComplicationTypePriority) = priority.supportedTypes().first { it in offered }

        assertThat(negotiated(ComplicationTypePriority.VALUE)).isEqualTo(ComplicationType.SHORT_TEXT)
        assertThat(negotiated(ComplicationTypePriority.ICON)).isEqualTo(ComplicationType.MONOCHROMATIC_IMAGE)
        assertThat(negotiated(ComplicationTypePriority.TEXT)).isEqualTo(ComplicationType.SHORT_TEXT)
    }
}
