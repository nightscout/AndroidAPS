package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.plugins.libre3.Libre3WarmupState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** When the warm-up ring shows, and what it knows. */
class Libre3WarmupMapperTest {

    private fun state(phase: Libre3WarmupState.Phase, remainingMs: Long? = null) =
        Libre3WarmupState(phase = phase, remainingMs = remainingMs)

    @Test
    fun `a warming sensor shows the ring with how far it has come`() {
        val status = Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.WARMING, 900_000L))

        assertThat(status).isNotNull()
        assertThat(status!!.active).isTrue()
        assertThat(status.phase).isEqualTo(CgmWarmupStatus.Phase.WARMING)
        assertThat(status.remainingMs).isEqualTo(900_000L)
        assertThat(status.totalMs).isEqualTo(3_600_000L)
    }

    @Test
    fun `a sensor that is running shows nothing`() {
        assertThat(Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.READY))).isNull()
    }

    @Test
    fun `a sensor doing nothing shows nothing`() {
        assertThat(Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.IDLE))).isNull()
    }

    @Test
    fun `a final failure shows nothing on the ring, the user is told another way`() {
        assertThat(Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.FAILED))).isNull()
    }

    @Test
    fun `pairing, connecting and reconnecting each show their own phase`() {
        assertThat(Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.PAIRING))!!.phase)
            .isEqualTo(CgmWarmupStatus.Phase.PAIRING)
        assertThat(Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.CONNECTING))!!.phase)
            .isEqualTo(CgmWarmupStatus.Phase.CONNECTING)
        assertThat(Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.RECONNECTING))!!.phase)
            .isEqualTo(CgmWarmupStatus.Phase.RECONNECTING)
    }

    @Test
    fun `while only connecting, the ring does not pretend to know how long it will take`() {
        val status = Libre3WarmupMapper.toCgmWarmupStatus(state(Libre3WarmupState.Phase.CONNECTING))

        assertThat(status!!.totalMs).isNull()
    }
}
