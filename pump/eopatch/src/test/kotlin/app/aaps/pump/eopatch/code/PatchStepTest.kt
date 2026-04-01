package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchStepTest {

    @Test
    fun `should have expected number of steps`() {
        assertThat(PatchStep.entries).hasSize(21)
    }

    @Test
    fun `should contain all expected steps`() {
        assertThat(PatchStep.entries).containsExactly(
            PatchStep.SAFE_DEACTIVATION,
            PatchStep.MANUALLY_TURNING_OFF_ALARM,
            PatchStep.DISCARDED,
            PatchStep.DISCARDED_FOR_CHANGE,
            PatchStep.DISCARDED_FROM_ALARM,
            PatchStep.WAKE_UP,
            PatchStep.CONNECT_NEW,
            PatchStep.SELECT_INSULIN,
            PatchStep.REMOVE_NEEDLE_CAP,
            PatchStep.SITE_LOCATION,
            PatchStep.REMOVE_PROTECTION_TAPE,
            PatchStep.SAFETY_CHECK,
            PatchStep.ROTATE_KNOB,
            PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR,
            PatchStep.BASAL_SCHEDULE,
            PatchStep.SETTING_REMINDER_TIME,
            PatchStep.CHECK_CONNECTION,
            PatchStep.CANCEL,
            PatchStep.COMPLETE,
            PatchStep.BACK_TO_HOME,
            PatchStep.FINISH
        ).inOrder()
    }

    @Test
    fun `only SAFE_DEACTIVATION should have isSafeDeactivation true`() {
        assertThat(PatchStep.SAFE_DEACTIVATION.isSafeDeactivation).isTrue()
        PatchStep.entries.filter { it != PatchStep.SAFE_DEACTIVATION }.forEach {
            assertThat(it.isSafeDeactivation).isFalse()
        }
    }

    @Test
    fun `only CHECK_CONNECTION should have isCheckConnection true`() {
        assertThat(PatchStep.CHECK_CONNECTION.isCheckConnection).isTrue()
        PatchStep.entries.filter { it != PatchStep.CHECK_CONNECTION }.forEach {
            assertThat(it.isCheckConnection).isFalse()
        }
    }

    @Test
    fun `activation steps should be in logical order`() {
        assertThat(PatchStep.WAKE_UP.ordinal).isLessThan(PatchStep.CONNECT_NEW.ordinal)
        assertThat(PatchStep.CONNECT_NEW.ordinal).isLessThan(PatchStep.SELECT_INSULIN.ordinal)
        assertThat(PatchStep.SELECT_INSULIN.ordinal).isLessThan(PatchStep.REMOVE_NEEDLE_CAP.ordinal)
        assertThat(PatchStep.REMOVE_NEEDLE_CAP.ordinal).isLessThan(PatchStep.SITE_LOCATION.ordinal)
        assertThat(PatchStep.SITE_LOCATION.ordinal).isLessThan(PatchStep.REMOVE_PROTECTION_TAPE.ordinal)
        assertThat(PatchStep.REMOVE_PROTECTION_TAPE.ordinal).isLessThan(PatchStep.SAFETY_CHECK.ordinal)
        assertThat(PatchStep.SAFETY_CHECK.ordinal).isLessThan(PatchStep.ROTATE_KNOB.ordinal)
        assertThat(PatchStep.ROTATE_KNOB.ordinal).isLessThan(PatchStep.BASAL_SCHEDULE.ordinal)
        assertThat(PatchStep.BASAL_SCHEDULE.ordinal).isLessThan(PatchStep.COMPLETE.ordinal)
    }

    @Test
    fun `FINISH should be last step`() {
        assertThat(PatchStep.entries.last()).isEqualTo(PatchStep.FINISH)
    }
}
