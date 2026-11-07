package app.aaps.wear.complications

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What a tap on the two always-on readouts does.
 *
 * Both tests are faults that reached a wrist, and they pull in opposite directions - which is why
 * the rule needs holding in place rather than remembering. Fixing either one alone brought the
 * other back, and it has already happened twice.
 */
class AmbientReadoutTapTest {

    @Test
    fun `opens the AAPS menu while the watch is awake`() {
        // The dead spot: a tap on the readouts did nothing, while a tap anywhere else on the face
        // opened AAPS. These slots sit over the picture and take the tap, so doing nothing is not
        // the same as letting it through.
        assertThat(readoutTapAction(dozing = false)).isEqualTo(ComplicationAction.MENU)
    }

    @Test
    fun `does nothing while the watch dozes, so the first tap wakes it`() {
        // The other direction: with an action in every mode, the first tap on a sleeping watch
        // opened AAPS instead of waking the screen - reported twice, with the tap highlight visible
        assertThat(readoutTapAction(dozing = true)).isEqualTo(ComplicationAction.NONE)
    }
}
