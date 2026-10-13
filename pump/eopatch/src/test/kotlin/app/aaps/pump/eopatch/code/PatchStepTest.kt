package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchStepTest {

    @Test
    fun `should have exactly 18 steps`() {
        assertThat(PatchStep.entries).hasSize(19)
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
            PatchStep.REMOVE_NEEDLE_CAP,
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
    fun `all steps should be distinct`() {
        val steps = PatchStep.entries
        val uniqueSteps = steps.toSet()

        assertThat(uniqueSteps.size).isEqualTo(steps.size)
    }

    @Test
    fun `should support valueOf`() {
        assertThat(PatchStep.valueOf("SAFE_DEACTIVATION")).isEqualTo(PatchStep.SAFE_DEACTIVATION)
        assertThat(PatchStep.valueOf("MANUALLY_TURNING_OFF_ALARM")).isEqualTo(PatchStep.MANUALLY_TURNING_OFF_ALARM)
        assertThat(PatchStep.valueOf("DISCARDED")).isEqualTo(PatchStep.DISCARDED)
        assertThat(PatchStep.valueOf("WAKE_UP")).isEqualTo(PatchStep.WAKE_UP)
        assertThat(PatchStep.valueOf("CHECK_CONNECTION")).isEqualTo(PatchStep.CHECK_CONNECTION)
        assertThat(PatchStep.valueOf("COMPLETE")).isEqualTo(PatchStep.COMPLETE)
    }

    @Test
    fun `ordinal values should be sequential`() {
        assertThat(PatchStep.SAFE_DEACTIVATION.ordinal).isEqualTo(0)
        assertThat(PatchStep.MANUALLY_TURNING_OFF_ALARM.ordinal).isEqualTo(1)
        assertThat(PatchStep.DISCARDED.ordinal).isEqualTo(2)
        assertThat(PatchStep.DISCARDED_FOR_CHANGE.ordinal).isEqualTo(3)
        assertThat(PatchStep.DISCARDED_FROM_ALARM.ordinal).isEqualTo(4)
        assertThat(PatchStep.WAKE_UP.ordinal).isEqualTo(5)
        assertThat(PatchStep.CONNECT_NEW.ordinal).isEqualTo(6)
        assertThat(PatchStep.REMOVE_NEEDLE_CAP.ordinal).isEqualTo(7)
        assertThat(PatchStep.REMOVE_PROTECTION_TAPE.ordinal).isEqualTo(8)
        assertThat(PatchStep.SAFETY_CHECK.ordinal).isEqualTo(9)
        assertThat(PatchStep.ROTATE_KNOB.ordinal).isEqualTo(10)
        assertThat(PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR.ordinal).isEqualTo(11)
        assertThat(PatchStep.BASAL_SCHEDULE.ordinal).isEqualTo(12)
        assertThat(PatchStep.SETTING_REMINDER_TIME.ordinal).isEqualTo(13)
        assertThat(PatchStep.CHECK_CONNECTION.ordinal).isEqualTo(14)
        assertThat(PatchStep.CANCEL.ordinal).isEqualTo(15)
        assertThat(PatchStep.COMPLETE.ordinal).isEqualTo(16)
        assertThat(PatchStep.BACK_TO_HOME.ordinal).isEqualTo(17)
        assertThat(PatchStep.FINISH.ordinal).isEqualTo(18)
    }

    @Test
    fun `only SAFE_DEACTIVATION should have isSafeDeactivation true`() {
        assertThat(PatchStep.SAFE_DEACTIVATION.isSafeDeactivation).isTrue()

        // All other steps should be false
        assertThat(PatchStep.MANUALLY_TURNING_OFF_ALARM.isSafeDeactivation).isFalse()
        assertThat(PatchStep.DISCARDED.isSafeDeactivation).isFalse()
        assertThat(PatchStep.DISCARDED_FOR_CHANGE.isSafeDeactivation).isFalse()
        assertThat(PatchStep.DISCARDED_FROM_ALARM.isSafeDeactivation).isFalse()
        assertThat(PatchStep.WAKE_UP.isSafeDeactivation).isFalse()
        assertThat(PatchStep.CONNECT_NEW.isSafeDeactivation).isFalse()
        assertThat(PatchStep.REMOVE_NEEDLE_CAP.isSafeDeactivation).isFalse()
        assertThat(PatchStep.REMOVE_PROTECTION_TAPE.isSafeDeactivation).isFalse()
        assertThat(PatchStep.SAFETY_CHECK.isSafeDeactivation).isFalse()
        assertThat(PatchStep.ROTATE_KNOB.isSafeDeactivation).isFalse()
        assertThat(PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR.isSafeDeactivation).isFalse()
        assertThat(PatchStep.BASAL_SCHEDULE.isSafeDeactivation).isFalse()
        assertThat(PatchStep.SETTING_REMINDER_TIME.isSafeDeactivation).isFalse()
        assertThat(PatchStep.CHECK_CONNECTION.isSafeDeactivation).isFalse()
        assertThat(PatchStep.CANCEL.isSafeDeactivation).isFalse()
        assertThat(PatchStep.COMPLETE.isSafeDeactivation).isFalse()
        assertThat(PatchStep.BACK_TO_HOME.isSafeDeactivation).isFalse()
        assertThat(PatchStep.FINISH.isSafeDeactivation).isFalse()
    }

    @Test
    fun `only CHECK_CONNECTION should have isCheckConnection true`() {
        assertThat(PatchStep.CHECK_CONNECTION.isCheckConnection).isTrue()

        // All other steps should be false
        assertThat(PatchStep.SAFE_DEACTIVATION.isCheckConnection).isFalse()
        assertThat(PatchStep.MANUALLY_TURNING_OFF_ALARM.isCheckConnection).isFalse()
        assertThat(PatchStep.DISCARDED.isCheckConnection).isFalse()
        assertThat(PatchStep.DISCARDED_FOR_CHANGE.isCheckConnection).isFalse()
        assertThat(PatchStep.DISCARDED_FROM_ALARM.isCheckConnection).isFalse()
        assertThat(PatchStep.WAKE_UP.isCheckConnection).isFalse()
        assertThat(PatchStep.CONNECT_NEW.isCheckConnection).isFalse()
        assertThat(PatchStep.REMOVE_NEEDLE_CAP.isCheckConnection).isFalse()
        assertThat(PatchStep.REMOVE_PROTECTION_TAPE.isCheckConnection).isFalse()
        assertThat(PatchStep.SAFETY_CHECK.isCheckConnection).isFalse()
        assertThat(PatchStep.ROTATE_KNOB.isCheckConnection).isFalse()
        assertThat(PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR.isCheckConnection).isFalse()
        assertThat(PatchStep.BASAL_SCHEDULE.isCheckConnection).isFalse()
        assertThat(PatchStep.SETTING_REMINDER_TIME.isCheckConnection).isFalse()
        assertThat(PatchStep.CANCEL.isCheckConnection).isFalse()
        assertThat(PatchStep.COMPLETE.isCheckConnection).isFalse()
        assertThat(PatchStep.BACK_TO_HOME.isCheckConnection).isFalse()
        assertThat(PatchStep.FINISH.isCheckConnection).isFalse()
    }

    @Test
    fun `deactivation steps should come first`() {
        assertThat(PatchStep.SAFE_DEACTIVATION.ordinal).isLessThan(PatchStep.WAKE_UP.ordinal)
        assertThat(PatchStep.DISCARDED.ordinal).isLessThan(PatchStep.WAKE_UP.ordinal)
    }

    @Test
    fun `activation steps should be in logical order`() {
        // Wake up before connect
        assertThat(PatchStep.WAKE_UP.ordinal).isLessThan(PatchStep.CONNECT_NEW.ordinal)

        // Connect before removing cap
        assertThat(PatchStep.CONNECT_NEW.ordinal).isLessThan(PatchStep.REMOVE_NEEDLE_CAP.ordinal)

        // Remove cap before removing tape
        assertThat(PatchStep.REMOVE_NEEDLE_CAP.ordinal).isLessThan(PatchStep.REMOVE_PROTECTION_TAPE.ordinal)

        // Remove tape before safety check
        assertThat(PatchStep.REMOVE_PROTECTION_TAPE.ordinal).isLessThan(PatchStep.SAFETY_CHECK.ordinal)

        // Safety check before rotating knob
        assertThat(PatchStep.SAFETY_CHECK.ordinal).isLessThan(PatchStep.ROTATE_KNOB.ordinal)

        // Rotate knob before basal schedule
        assertThat(PatchStep.ROTATE_KNOB.ordinal).isLessThan(PatchStep.BASAL_SCHEDULE.ordinal)

        // Basal schedule before complete
        assertThat(PatchStep.BASAL_SCHEDULE.ordinal).isLessThan(PatchStep.COMPLETE.ordinal)
    }

    @Test
    fun `FINISH should be last step`() {
        val lastStep = PatchStep.entries.last()
        assertThat(lastStep).isEqualTo(PatchStep.FINISH)
    }
}
