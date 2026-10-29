package app.aaps.cgm.dexcomg7.watch

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.G7WatchState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class G7WatchHandoverTest {

    private val on = EventData.G7WatchDirect(active = true, pairingCode = "1155", address = "AA")
    private val off = EventData.G7WatchDirect(active = false)

    private fun status(state: G7WatchState, address: String? = "AA") = EventData.G7WatchStatus(state, address)

    @Test
    fun aWatchOnTheSameSensorIsInStep() {
        assertThat(G7WatchHandover.matches(on, status(G7WatchState.WAITING))).isTrue()
        assertThat(G7WatchHandover.matches(on, status(G7WatchState.PAIRING))).isTrue()
        assertThat(G7WatchHandover.matches(on, status(G7WatchState.NEEDS_PERMISSION))).isTrue()
    }

    @Test
    fun aWatchThatMissedTheMessageOrHoldsAnOldSensorIsOutOfStep() {
        assertThat(G7WatchHandover.matches(on, status(G7WatchState.OFF, null))).isFalse()
        assertThat(G7WatchHandover.matches(on, status(G7WatchState.WAITING, "BB"))).isFalse()
        assertThat(G7WatchHandover.matches(off, status(G7WatchState.WAITING))).isFalse()
        assertThat(G7WatchHandover.matches(off, status(G7WatchState.OFF, null))).isTrue()
    }

    @Test
    fun aWatchThatCannotRunItIsLeftAlone() {
        assertThat(G7WatchHandover.matches(on, status(G7WatchState.NOT_SUPPORTED, null))).isTrue()
    }
}
