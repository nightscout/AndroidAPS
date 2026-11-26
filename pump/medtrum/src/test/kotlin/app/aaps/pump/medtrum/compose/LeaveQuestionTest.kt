package app.aaps.pump.medtrum.compose

import app.aaps.pump.medtrum.code.PatchStep
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The question asked when the patch wizard is left. "Change Patch" with a running patch, then Cancel on
 * "Deactivate Patch", asked whether to cancel the activation.
 */
class LeaveQuestionTest {

    private val deactivation = listOf(PatchStep.START_DEACTIVATION, PatchStep.DEACTIVATE, PatchStep.FORCE_DEACTIVATION, PatchStep.DEACTIVATION_COMPLETE)

    @Test
    fun `no step of a deactivation asks about the activation`() {
        deactivation.forEach { step ->
            assertThat(leaveQuestionFor(step)).isNotEqualTo(LeaveQuestion.ACTIVATION)
        }
    }

    /** As in 3.4: its own text says that Cancel returns to the main screen, and nothing has been done yet. */
    @Test
    fun `the first deactivation screen is left at once`() {
        assertThat(leaveQuestionFor(PatchStep.START_DEACTIVATION)).isEqualTo(LeaveQuestion.NONE)
    }

    @Test
    fun `the screen after the deactivation is left at once`() {
        assertThat(leaveQuestionFor(PatchStep.DEACTIVATION_COMPLETE)).isEqualTo(LeaveQuestion.NONE)
    }

    /** "Failed to deactivate, press Discard to forget patch": Cancel leaves the patch as it is. */
    @Test
    fun `cancel on a failed deactivation asks about the deactivation`() {
        assertThat(leaveQuestionFor(PatchStep.DEACTIVATE)).isEqualTo(LeaveQuestion.DEACTIVATION)
    }

    @Test
    fun `every other step still asks about the activation`() {
        (PatchStep.entries - deactivation).forEach { step ->
            assertThat(leaveQuestionFor(step)).isEqualTo(LeaveQuestion.ACTIVATION)
        }
    }
}
