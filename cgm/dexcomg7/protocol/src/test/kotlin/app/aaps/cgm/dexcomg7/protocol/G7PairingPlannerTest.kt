package app.aaps.cgm.dexcomg7.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Ported from Trio's G7PairingPlannerTests. */
class G7PairingPlannerTest {

    private val a = "AA:00:00:00:00:01"
    private val b = "AA:00:00:00:00:02"
    private val c = "AA:00:00:00:00:03"
    private val d = "AA:00:00:00:00:04"

    @Test
    fun firstCandidateBecomesCurrent() {
        val planner = G7PairingPlanner()
        assertThat(planner.currentCandidate).isNull()
        assertThat(planner.addCandidate(a, "DXCM01", false)).isTrue()
        assertThat(planner.currentCandidate?.id).isEqualTo(a)
        assertThat(planner.nextAttemptNumber).isEqualTo(1)
    }

    @Test
    fun duplicateIsIgnored() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "DXCM01", false)
        assertThat(planner.addCandidate(a, "DXCM01", false)).isFalse()
        assertThat(planner.candidates).hasSize(1)
    }

    @Test
    fun freeSensorsGoBeforeHeldOnes() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "held", true)
        planner.addCandidate(b, "free", false)
        assertThat(planner.currentCandidate?.name).isEqualTo("held")
        planner.addCandidate(c, "held2", true)
        planner.addCandidate(d, "free2", false)
        assertThat(planner.candidates.map { it.name }).containsExactly("held", "free", "free2", "held2").inOrder()
    }

    @Test
    fun heldCandidateIsDeferredNotDropped() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "free", false)
        planner.addCandidate(b, "held", true)
        assertThat(planner.abandonCurrentCandidate("rejected")).isEqualTo(G7PairingPlanner.Action.AdvanceToNext)
        assertThat(planner.currentCandidate?.name).isEqualTo("held")
    }

    @Test
    fun ordinaryFailuresRetryThenAdvance() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "A", false)
        planner.addCandidate(b, "B", false)
        for (attempt in 1 until G7PairingPlanner.ATTEMPTS_PER_CANDIDATE) {
            assertThat(planner.nextAttemptNumber).isEqualTo(attempt)
            assertThat(planner.recordFailure()).isEqualTo(G7PairingPlanner.Action.RetryCurrent)
        }
        assertThat(planner.recordFailure()).isEqualTo(G7PairingPlanner.Action.AdvanceToNext)
        assertThat(planner.currentCandidate?.id).isEqualTo(b)
        assertThat(planner.nextAttemptNumber).isEqualTo(1)
    }

    @Test
    fun rejectionAbandonsAtOnce() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "A", false)
        planner.addCandidate(b, "B", false)
        assertThat(planner.abandonCurrentCandidate("rejected")).isEqualTo(G7PairingPlanner.Action.AdvanceToNext)
        assertThat(planner.currentCandidate?.id).isEqualTo(b)
    }

    @Test
    fun givesUpAfterTheLastCandidateAndSaysWhy() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "A", false)
        val action = planner.abandonCurrentCandidate("wrong code") as G7PairingPlanner.Action.GiveUp
        assertThat(action.reason).isEqualTo(G7PairingPlanner.Reason.ALL_CANDIDATES_FAILED)
        assertThat(action.details).contains("A: wrong code")
        assertThat(planner.currentCandidate).isNull()
    }

    @Test
    fun givesUpWithNothingFound() {
        val action = G7PairingPlanner().recordFailure() as G7PairingPlanner.Action.GiveUp
        assertThat(action.reason).isEqualTo(G7PairingPlanner.Reason.NO_SENSOR_FOUND)
    }

    @Test
    fun slotUpdateReordersUntriedCandidates() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "current", false)
        planner.addCandidate(b, "held", true)
        planner.addCandidate(c, "free", false)
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "free", "held").inOrder()
        assertThat(planner.updateSlot(b, false)).isTrue()
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "free", "held").inOrder()
        assertThat(planner.updateSlot(c, true)).isTrue()
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "held", "free").inOrder()
        assertThat(planner.updateSlot(c, true)).isFalse()
    }

    @Test
    fun slotUpdateNeverMovesTheCurrentCandidate() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "current", false)
        planner.addCandidate(b, "other", false)
        planner.updateSlot(a, true)
        assertThat(planner.currentCandidate?.id).isEqualTo(a)
    }

    @Test
    fun strongerSignalGoesFirstWithinAGroup() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "current", false, -50)
        planner.addCandidate(b, "weak", false, -85)
        planner.addCandidate(c, "strong", false, -42)
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "strong", "weak").inOrder()
    }

    @Test
    fun signalDoesNotBeatHeldOrFree() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "current", false, -50)
        planner.addCandidate(b, "held-strong", true, -30)
        planner.addCandidate(c, "free-weak", false, -88)
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "free-weak", "held-strong").inOrder()
    }

    @Test
    fun unknownSignalKeepsDiscoveryOrder() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "current", false)
        planner.addCandidate(b, "first", false)
        planner.addCandidate(c, "second", false)
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "first", "second").inOrder()
    }

    @Test
    fun updateAppliesNewSignal() {
        val planner = G7PairingPlanner()
        planner.addCandidate(a, "current", false, -50)
        planner.addCandidate(b, "b", false, -80)
        planner.addCandidate(c, "c", false, -70)
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "c", "b").inOrder()
        assertThat(planner.updateSlot(b, null, -30)).isTrue()
        assertThat(planner.candidates.map { it.name }).containsExactly("current", "b", "c").inOrder()
        assertThat(planner.updateSlot(b, null, -30)).isFalse()
    }

    @Test
    fun typedCodeKeepsScanningAfterAllFailed() {
        val planner = G7PairingPlanner(keepScanningWhenExhausted = true)
        planner.addCandidate(a, "neighbour", false, -60)
        assertThat(planner.abandonCurrentCandidate("wrong sensor")).isEqualTo(G7PairingPlanner.Action.KeepScanning)
        assertThat(planner.currentCandidate).isNull()
        assertThat(planner.addCandidate(b, "real", false, -45)).isTrue()
        assertThat(planner.currentCandidate?.name).isEqualTo("real")
    }

    @Test
    fun scannedCodeGivesUpAfterAllFailed() {
        val planner = G7PairingPlanner(keepScanningWhenExhausted = false)
        planner.addCandidate(a, "only", false)
        assertThat(planner.abandonCurrentCandidate("wrong code")).isInstanceOf(G7PairingPlanner.Action.GiveUp::class.java)
    }
}
