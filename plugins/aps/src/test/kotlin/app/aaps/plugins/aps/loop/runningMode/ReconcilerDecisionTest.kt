package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.RM
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ReconcilerDecisionTest : TestBase() {

    private val working = listOf(
        RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP, RM.Mode.CLOSED_LOOP_LGS,
        RM.Mode.DISABLED_LOOP, RM.Mode.RESUME
    )
    private val zeroDelivery = listOf(RM.Mode.DISCONNECTED_PUMP, RM.Mode.SUPER_BOLUS)
    private val suspendedNoTbr = listOf(RM.Mode.SUSPENDED_BY_USER, RM.Mode.SUSPENDED_BY_DST)
    private val pumpReported = listOf(RM.Mode.SUSPENDED_BY_PUMP)

    // --- Bucket classification ---

    @Test
    fun `working modes map to Working bucket`() {
        working.forEach {
            assertThat(ReconcilerDecision.bucketOf(it)).isEqualTo(ReconcilerDecision.Bucket.Working)
        }
    }

    @Test
    fun `zero delivery modes map to ZeroDelivery bucket`() {
        zeroDelivery.forEach {
            assertThat(ReconcilerDecision.bucketOf(it)).isEqualTo(ReconcilerDecision.Bucket.ZeroDelivery)
        }
    }

    @Test
    fun `suspended no tbr modes map to SuspendedNoTbr bucket`() {
        suspendedNoTbr.forEach {
            assertThat(ReconcilerDecision.bucketOf(it)).isEqualTo(ReconcilerDecision.Bucket.SuspendedNoTbr)
        }
    }

    @Test
    fun `suspended by pump maps to PumpReported bucket`() {
        pumpReported.forEach {
            assertThat(ReconcilerDecision.bucketOf(it)).isEqualTo(ReconcilerDecision.Bucket.PumpReported)
        }
    }

    // --- Entry to zero-delivery ---

    @Test
    fun `working to disconnected pump issues zero TBR and cancels eb`() {
        working.forEach { prev ->
            assertThat(ReconcilerDecision.decide(prev, RM.Mode.DISCONNECTED_PUMP))
                .isEqualTo(ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true))
        }
    }

    @Test
    fun `working to super bolus issues zero TBR and cancels eb`() {
        working.forEach { prev ->
            assertThat(ReconcilerDecision.decide(prev, RM.Mode.SUPER_BOLUS))
                .isEqualTo(ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true))
        }
    }

    @Test
    fun `suspended no tbr to zero delivery issues zero TBR and cancels eb`() {
        suspendedNoTbr.forEach { prev ->
            zeroDelivery.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true))
            }
        }
    }

    @Test
    fun `zero delivery to zero delivery also issues zero TBR defensively`() {
        // Observer dedupes via pump state; decision here just states intent.
        zeroDelivery.forEach { prev ->
            zeroDelivery.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true))
            }
        }
    }

    // --- Entry to suspended-no-tbr ---

    @Test
    fun `working to suspended no tbr cancels TBR`() {
        working.forEach { prev ->
            suspendedNoTbr.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.CancelTbr)
            }
        }
    }

    @Test
    fun `zero delivery to suspended no tbr cancels TBR`() {
        zeroDelivery.forEach { prev ->
            suspendedNoTbr.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.CancelTbr)
            }
        }
    }

    @Test
    fun `suspended no tbr to suspended no tbr cancels TBR defensively`() {
        // Transition between two suspended-no-tbr modes: still ensure no active TBR.
        suspendedNoTbr.forEach { prev ->
            suspendedNoTbr.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.CancelTbr)
            }
        }
    }

    // --- Exit from zero-delivery ---

    @Test
    fun `zero delivery to working cancels TBR`() {
        zeroDelivery.forEach { prev ->
            working.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.CancelTbr)
            }
        }
    }

    // --- Exit from suspended-no-tbr to working: no-op ---

    @Test
    fun `suspended no tbr to working is no-op`() {
        // No TBR was set in suspended-no-tbr, so nothing to clean up.
        suspendedNoTbr.forEach { prev ->
            working.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.NoOp)
            }
        }
    }

    // --- Working to working: no-op ---

    @Test
    fun `working to working is no-op`() {
        working.forEach { prev ->
            working.forEach { next ->
                assertThat(ReconcilerDecision.decide(prev, next))
                    .isEqualTo(ReconcilerDecision.Action.NoOp)
            }
        }
    }

    // --- SUSPENDED_BY_PUMP: precheck owns this ---

    @Test
    fun `anything to suspended by pump is no-op`() {
        RM.Mode.entries.forEach { prev ->
            assertThat(ReconcilerDecision.decide(prev, RM.Mode.SUSPENDED_BY_PUMP))
                .isEqualTo(ReconcilerDecision.Action.NoOp)
        }
    }

    @Test
    fun `suspended by pump to anything is no-op`() {
        RM.Mode.entries.forEach { next ->
            assertThat(ReconcilerDecision.decide(RM.Mode.SUSPENDED_BY_PUMP, next))
                .isEqualTo(ReconcilerDecision.Action.NoOp)
        }
    }

    // --- Exhaustiveness guard ---

    @Test
    fun `every mode pair produces an action`() {
        // Regression guard: adding a new RM.Mode without updating bucketOf() would fail compilation
        // (when is exhaustive). This test catches the subtler case where a new mode gets bucketed
        // but decide() doesn't have the transition right — at minimum, assert every cell returns
        // a defined Action rather than throwing.
        RM.Mode.entries.forEach { prev ->
            RM.Mode.entries.forEach { next ->
                val a = ReconcilerDecision.decide(prev, next)
                assertThat(a).isAnyOf(
                    ReconcilerDecision.Action.NoOp,
                    ReconcilerDecision.Action.CancelTbr,
                    ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true),
                    ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = false)
                )
            }
        }
    }
}
