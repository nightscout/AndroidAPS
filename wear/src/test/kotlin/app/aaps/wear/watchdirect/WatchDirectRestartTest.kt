package app.aaps.wear.watchdirect

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WatchDirectRestartTest {

    private fun ask(
        hasSensor: Boolean = true,
        hasKey: Boolean = true,
        paused: Boolean = false,
        permitted: Boolean = true,
        bluetoothOn: Boolean = true,
        connected: Boolean = false,
    ) = WatchDirectRestart.shouldAsk(hasSensor, hasKey, paused, permitted, bluetoothOn, connected)

    @Test
    fun `a paired link that is down is asked again`() {
        assertThat(ask()).isTrue()
    }

    @Test
    fun `a link without a key is not asked so no pairing starts by itself`() {
        // A pairing can end in a refusal, and four refusals in a row lock the sensor for the phone too.
        assertThat(ask(hasKey = false)).isFalse()
    }

    @Test
    fun `a link that was turned off by the phone stays off`() {
        assertThat(ask(paused = true)).isFalse()
    }

    @Test
    fun `a connected link is left alone`() {
        assertThat(ask(connected = true)).isFalse()
    }

    @Test
    fun `nothing is asked without a sensor or permission or Bluetooth`() {
        assertThat(ask(hasSensor = false)).isFalse()
        assertThat(ask(permitted = false)).isFalse()
        assertThat(ask(bluetoothOn = false)).isFalse()
    }

    @Test
    fun `the link is asked after two sensor cycles and not after one missed reading`() {
        val cycle = 5L * 60L * 1000L
        assertThat(WatchDirectRestart.DOWN_BEFORE_ASKING_MS).isGreaterThan(2 * cycle)
        assertThat(WatchDirectRestart.DOWN_BEFORE_ASKING_MS).isLessThan(3 * cycle)
    }
}
