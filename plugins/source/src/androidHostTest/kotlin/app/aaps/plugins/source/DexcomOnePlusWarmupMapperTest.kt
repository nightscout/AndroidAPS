package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.source.compose.DexcomOnePlusWarmupCountdown
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DexcomOnePlusWarmupMapperTest {

    @Test
    fun `WARMING maps to active WARMING with fields passed through`() {
        val out = DexcomOnePlusWarmupMapper.toCgmWarmupStatus(
            OnePlusWarmupState(
                phase = OnePlusWarmupState.Phase.WARMING,
                remainingMs = 1_684_000L,
                endsAtEpochMs = 42L,
                message = "warming",
            ),
        )
        assertThat(out).isNotNull()
        assertThat(out!!.active).isTrue()
        assertThat(out.phase).isEqualTo(CgmWarmupStatus.Phase.WARMING)
        assertThat(out.remainingMs).isEqualTo(1_684_000L)
        assertThat(out.endsAtEpochMs).isEqualTo(42L)
        // Nominal total supplied only for WARMING → determinate warm-up ring.
        assertThat(out.totalMs).isEqualTo(DexcomOnePlusWarmupCountdown.LOCAL_FALLBACK_DURATION_MS)
    }

    @Test
    fun `CONNECTING RECONNECTING PAIRING map to active with same phase`() {
        val cases = mapOf(
            OnePlusWarmupState.Phase.CONNECTING to CgmWarmupStatus.Phase.CONNECTING,
            OnePlusWarmupState.Phase.RECONNECTING to CgmWarmupStatus.Phase.RECONNECTING,
            OnePlusWarmupState.Phase.PAIRING to CgmWarmupStatus.Phase.PAIRING,
        )
        cases.forEach { (inPhase, outPhase) ->
            val out = DexcomOnePlusWarmupMapper.toCgmWarmupStatus(OnePlusWarmupState(phase = inPhase))
            assertThat(out).isNotNull()
            assertThat(out!!.active).isTrue()
            assertThat(out.phase).isEqualTo(outPhase)
            // Pre-warm-up phases have no countdown total → indeterminate ring.
            assertThat(out.totalMs).isNull()
        }
    }

    @Test
    fun `a reconnect that still carries the warm-up deadline keeps a determinate ring`() {
        val out = DexcomOnePlusWarmupMapper.toCgmWarmupStatus(
            OnePlusWarmupState(
                phase = OnePlusWarmupState.Phase.RECONNECTING,
                endsAtEpochMs = 1_700_000_000_000L,
                message = "waiting_for_adv",
            ),
        )
        assertThat(out).isNotNull()
        assertThat(out!!.phase).isEqualTo(CgmWarmupStatus.Phase.RECONNECTING)
        assertThat(out.endsAtEpochMs).isEqualTo(1_700_000_000_000L)
        // Duty-cycle reconnect during warm-up must not blank the warm-up ring.
        assertThat(out.totalMs).isEqualTo(DexcomOnePlusWarmupCountdown.LOCAL_FALLBACK_DURATION_MS)
    }

    @Test
    fun `terminal and idle phases map to null`() {
        listOf(
            OnePlusWarmupState.Phase.READY,
            OnePlusWarmupState.Phase.IDLE,
            OnePlusWarmupState.Phase.FAILED,
        ).forEach { phase ->
            assertThat(DexcomOnePlusWarmupMapper.toCgmWarmupStatus(OnePlusWarmupState(phase = phase)))
                .isNull()
        }
    }

    @Test
    fun `null countdown fields are preserved on active status`() {
        val out = DexcomOnePlusWarmupMapper.toCgmWarmupStatus(
            OnePlusWarmupState(phase = OnePlusWarmupState.Phase.WARMING),
        )
        assertThat(out).isNotNull()
        assertThat(out!!.remainingMs).isNull()
        assertThat(out.endsAtEpochMs).isNull()
    }
}
