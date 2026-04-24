package app.aaps.core.objects.runningMode

import app.aaps.core.data.model.RM
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TbrGateTest : TestBase() {

    // --- Working modes: everything allowed ---

    @Test
    fun `closed loop allows all command kinds`() {
        TbrGate.CommandKind.entries.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.CLOSED_LOOP, kind)).isEqualTo(TbrGate.Decision.Allow)
        }
    }

    @Test
    fun `open loop allows all command kinds`() {
        TbrGate.CommandKind.entries.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.OPEN_LOOP, kind)).isEqualTo(TbrGate.Decision.Allow)
        }
    }

    @Test
    fun `closed loop lgs allows all command kinds`() {
        TbrGate.CommandKind.entries.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.CLOSED_LOOP_LGS, kind)).isEqualTo(TbrGate.Decision.Allow)
        }
    }

    @Test
    fun `disabled loop allows all command kinds`() {
        TbrGate.CommandKind.entries.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.DISABLED_LOOP, kind)).isEqualTo(TbrGate.Decision.Allow)
        }
    }

    @Test
    fun `resume mode allows all command kinds`() {
        TbrGate.CommandKind.entries.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.RESUME, kind)).isEqualTo(TbrGate.Decision.Allow)
        }
    }

    // --- DISCONNECTED_PUMP: only zero TBR + cancel ---

    @Test
    fun `disconnected pump allows zero TBR`() {
        assertThat(TbrGate.check(RM.Mode.DISCONNECTED_PUMP, TbrGate.CommandKind.TEMP_BASAL_ZERO))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `disconnected pump allows cancel TBR`() {
        assertThat(TbrGate.check(RM.Mode.DISCONNECTED_PUMP, TbrGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `disconnected pump rejects non-zero TBR`() {
        assertThat(TbrGate.check(RM.Mode.DISCONNECTED_PUMP, TbrGate.CommandKind.TEMP_BASAL_NONZERO))
            .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.PUMP_DISCONNECTED))
    }

    @Test
    fun `disconnected pump rejects bolus`() {
        assertThat(TbrGate.check(RM.Mode.DISCONNECTED_PUMP, TbrGate.CommandKind.BOLUS))
            .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.PUMP_DISCONNECTED))
    }

    @Test
    fun `disconnected pump rejects extended bolus`() {
        assertThat(TbrGate.check(RM.Mode.DISCONNECTED_PUMP, TbrGate.CommandKind.EXTENDED_BOLUS))
            .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.PUMP_DISCONNECTED))
    }

    // --- SUPER_BOLUS: zero TBR, cancel, bolus (the super bolus itself) allowed ---

    @Test
    fun `super bolus allows zero TBR`() {
        assertThat(TbrGate.check(RM.Mode.SUPER_BOLUS, TbrGate.CommandKind.TEMP_BASAL_ZERO))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `super bolus allows cancel TBR`() {
        assertThat(TbrGate.check(RM.Mode.SUPER_BOLUS, TbrGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `super bolus allows bolus`() {
        assertThat(TbrGate.check(RM.Mode.SUPER_BOLUS, TbrGate.CommandKind.BOLUS))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `super bolus rejects non-zero TBR`() {
        assertThat(TbrGate.check(RM.Mode.SUPER_BOLUS, TbrGate.CommandKind.TEMP_BASAL_NONZERO))
            .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.SUPER_BOLUS_ACTIVE))
    }

    @Test
    fun `super bolus rejects extended bolus`() {
        assertThat(TbrGate.check(RM.Mode.SUPER_BOLUS, TbrGate.CommandKind.EXTENDED_BOLUS))
            .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.SUPER_BOLUS_ACTIVE))
    }

    // --- SUSPENDED_BY_USER: only cancel allowed ---

    @Test
    fun `suspended by user allows only cancel TBR`() {
        assertThat(TbrGate.check(RM.Mode.SUSPENDED_BY_USER, TbrGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `suspended by user rejects every other command kind`() {
        val rejected = listOf(
            TbrGate.CommandKind.TEMP_BASAL_ZERO,
            TbrGate.CommandKind.TEMP_BASAL_NONZERO,
            TbrGate.CommandKind.BOLUS,
            TbrGate.CommandKind.EXTENDED_BOLUS
        )
        rejected.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.SUSPENDED_BY_USER, kind))
                .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.LOOP_SUSPENDED_USER))
        }
    }

    // --- SUSPENDED_BY_DST: only cancel allowed ---

    @Test
    fun `suspended by dst allows only cancel TBR`() {
        assertThat(TbrGate.check(RM.Mode.SUSPENDED_BY_DST, TbrGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `suspended by dst rejects every other command kind`() {
        val rejected = listOf(
            TbrGate.CommandKind.TEMP_BASAL_ZERO,
            TbrGate.CommandKind.TEMP_BASAL_NONZERO,
            TbrGate.CommandKind.BOLUS,
            TbrGate.CommandKind.EXTENDED_BOLUS
        )
        rejected.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.SUSPENDED_BY_DST, kind))
                .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.LOOP_SUSPENDED_DST))
        }
    }

    // --- SUSPENDED_BY_PUMP: only cancel allowed ---

    @Test
    fun `suspended by pump allows only cancel TBR`() {
        assertThat(TbrGate.check(RM.Mode.SUSPENDED_BY_PUMP, TbrGate.CommandKind.CANCEL_TEMP_BASAL))
            .isEqualTo(TbrGate.Decision.Allow)
    }

    @Test
    fun `suspended by pump rejects every other command kind`() {
        val rejected = listOf(
            TbrGate.CommandKind.TEMP_BASAL_ZERO,
            TbrGate.CommandKind.TEMP_BASAL_NONZERO,
            TbrGate.CommandKind.BOLUS,
            TbrGate.CommandKind.EXTENDED_BOLUS
        )
        rejected.forEach { kind ->
            assertThat(TbrGate.check(RM.Mode.SUSPENDED_BY_PUMP, kind))
                .isEqualTo(TbrGate.Decision.Reject(TbrGate.Reason.PUMP_REPORTED_SUSPENDED))
        }
    }

    // --- Exhaustiveness guard ---

    @Test
    fun `every mode and command kind combination produces a Decision`() {
        RM.Mode.entries.forEach { mode ->
            TbrGate.CommandKind.entries.forEach { kind ->
                val d = TbrGate.check(mode, kind)
                assertThat(d).isAnyOf(
                    TbrGate.Decision.Allow,
                    TbrGate.Decision.Reject(TbrGate.Reason.PUMP_DISCONNECTED),
                    TbrGate.Decision.Reject(TbrGate.Reason.LOOP_SUSPENDED_USER),
                    TbrGate.Decision.Reject(TbrGate.Reason.LOOP_SUSPENDED_DST),
                    TbrGate.Decision.Reject(TbrGate.Reason.PUMP_REPORTED_SUSPENDED),
                    TbrGate.Decision.Reject(TbrGate.Reason.SUPER_BOLUS_ACTIVE)
                )
            }
        }
    }
}
