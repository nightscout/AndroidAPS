package app.aaps.core.objects.runningMode

import app.aaps.core.data.model.RM
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PumpCommandGateTest : TestBase() {

    // --- Working modes: everything allowed ---

    @Test
    fun `closed loop allows all command kinds`() {
        PumpCommandGate.CommandKind.entries.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.CLOSED_LOOP, kind)).isEqualTo(PumpCommandGate.Decision.Allow)
        }
    }

    @Test
    fun `open loop allows all command kinds`() {
        PumpCommandGate.CommandKind.entries.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.OPEN_LOOP, kind)).isEqualTo(PumpCommandGate.Decision.Allow)
        }
    }

    @Test
    fun `closed loop lgs allows all command kinds`() {
        PumpCommandGate.CommandKind.entries.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.CLOSED_LOOP_LGS, kind)).isEqualTo(PumpCommandGate.Decision.Allow)
        }
    }

    @Test
    fun `disabled loop allows all command kinds`() {
        PumpCommandGate.CommandKind.entries.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.DISABLED_LOOP, kind)).isEqualTo(PumpCommandGate.Decision.Allow)
        }
    }

    @Test
    fun `resume mode allows all command kinds`() {
        PumpCommandGate.CommandKind.entries.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.RESUME, kind)).isEqualTo(PumpCommandGate.Decision.Allow)
        }
    }

    // --- DISCONNECTED_PUMP: only zero TBR + cancel ---

    @Test
    fun `disconnected pump allows zero TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.DISCONNECTED_PUMP, PumpCommandGate.CommandKind.TEMP_BASAL_ZERO))
            .isEqualTo(PumpCommandGate.Decision.Allow)
    }

    @Test
    fun `disconnected pump allows cancel TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.DISCONNECTED_PUMP, PumpCommandGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(PumpCommandGate.Decision.Allow)
    }

    @Test
    fun `disconnected pump rejects non-zero TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.DISCONNECTED_PUMP, PumpCommandGate.CommandKind.TEMP_BASAL_NONZERO))
            .isEqualTo(PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.PUMP_DISCONNECTED))
    }

    @Test
    fun `disconnected pump rejects bolus`() {
        assertThat(PumpCommandGate.check(RM.Mode.DISCONNECTED_PUMP, PumpCommandGate.CommandKind.BOLUS))
            .isEqualTo(PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.PUMP_DISCONNECTED))
    }

    @Test
    fun `disconnected pump rejects extended bolus`() {
        assertThat(PumpCommandGate.check(RM.Mode.DISCONNECTED_PUMP, PumpCommandGate.CommandKind.EXTENDED_BOLUS))
            .isEqualTo(PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.PUMP_DISCONNECTED))
    }

    // --- SUPER_BOLUS: zero TBR, cancel, bolus (the super bolus itself) allowed ---

    @Test
    fun `super bolus allows zero TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.SUPER_BOLUS, PumpCommandGate.CommandKind.TEMP_BASAL_ZERO))
            .isEqualTo(PumpCommandGate.Decision.Allow)
    }

    @Test
    fun `super bolus allows cancel TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.SUPER_BOLUS, PumpCommandGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(PumpCommandGate.Decision.Allow)
    }

    @Test
    fun `super bolus allows bolus`() {
        assertThat(PumpCommandGate.check(RM.Mode.SUPER_BOLUS, PumpCommandGate.CommandKind.BOLUS))
            .isEqualTo(PumpCommandGate.Decision.Allow)
    }

    @Test
    fun `super bolus rejects non-zero TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.SUPER_BOLUS, PumpCommandGate.CommandKind.TEMP_BASAL_NONZERO))
            .isEqualTo(PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.SUPER_BOLUS_ACTIVE))
    }

    @Test
    fun `super bolus rejects extended bolus`() {
        assertThat(PumpCommandGate.check(RM.Mode.SUPER_BOLUS, PumpCommandGate.CommandKind.EXTENDED_BOLUS))
            .isEqualTo(PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.SUPER_BOLUS_ACTIVE))
    }

    // --- SUSPENDED_BY_USER: temporary DISABLED_LOOP — all commands allowed ---

    @Test
    fun `suspended by user allows all command kinds`() {
        PumpCommandGate.CommandKind.entries.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.SUSPENDED_BY_USER, kind)).isEqualTo(PumpCommandGate.Decision.Allow)
        }
    }

    // --- SUSPENDED_BY_DST: only cancel allowed ---

    @Test
    fun `suspended by dst allows only cancel TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.SUSPENDED_BY_DST, PumpCommandGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(PumpCommandGate.Decision.Allow)
    }

    @Test
    fun `suspended by dst rejects every other command kind`() {
        val rejected = listOf(
            PumpCommandGate.CommandKind.TEMP_BASAL_ZERO,
            PumpCommandGate.CommandKind.TEMP_BASAL_NONZERO,
            PumpCommandGate.CommandKind.BOLUS,
            PumpCommandGate.CommandKind.EXTENDED_BOLUS
        )
        rejected.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.SUSPENDED_BY_DST, kind))
                .isEqualTo(PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.LOOP_SUSPENDED_DST))
        }
    }

    // --- SUSPENDED_BY_PUMP: only cancel allowed ---

    @Test
    fun `suspended by pump allows only cancel TBR`() {
        assertThat(PumpCommandGate.check(RM.Mode.SUSPENDED_BY_PUMP, PumpCommandGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(PumpCommandGate.Decision.Allow)
    }

    @Test
    fun `suspended by pump rejects every other command kind`() {
        val rejected = listOf(
            PumpCommandGate.CommandKind.TEMP_BASAL_ZERO,
            PumpCommandGate.CommandKind.TEMP_BASAL_NONZERO,
            PumpCommandGate.CommandKind.BOLUS,
            PumpCommandGate.CommandKind.EXTENDED_BOLUS
        )
        rejected.forEach { kind ->
            assertThat(PumpCommandGate.check(RM.Mode.SUSPENDED_BY_PUMP, kind))
                .isEqualTo(PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.PUMP_REPORTED_SUSPENDED))
        }
    }

    // --- Exhaustiveness guard ---

    @Test
    fun `every mode and command kind combination produces a Decision`() {
        RM.Mode.entries.forEach { mode ->
            PumpCommandGate.CommandKind.entries.forEach { kind ->
                val d = PumpCommandGate.check(mode, kind)
                assertThat(d).isAnyOf(
                    PumpCommandGate.Decision.Allow,
                    PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.PUMP_DISCONNECTED),
                    PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.LOOP_SUSPENDED_DST),
                    PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.PUMP_REPORTED_SUSPENDED),
                    PumpCommandGate.Decision.Reject(PumpCommandGate.Reason.SUPER_BOLUS_ACTIVE)
                )
            }
        }
    }
}
