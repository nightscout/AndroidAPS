package app.aaps.pump.omnipod.common.bledriver.pod.state

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.keys.DashStringNonPreferenceKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * The pod state in memory against the stored one: a driver that stops drops its copy, and a stored state that
 * something else wrote (a settings import) is loaded again.
 *
 * The preferences are one text in memory, so a store and a load go through the real Gson round trip.
 */
class PodStateReloadTest : TestBase() {

    @Mock lateinit var preferences: Preferences

    private var stored: String? = null
    private lateinit var sut: OmnipodDashPodStateManagerImpl

    @BeforeEach
    fun setUp() {
        stored = null
        whenever(preferences.put(eq(DashStringNonPreferenceKey.PodState), any())).thenAnswer {
            stored = it.getArgument(1)
            Unit
        }
        whenever(preferences.getIfExists(DashStringNonPreferenceKey.PodState)).thenAnswer { stored }
        sut = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences)
    }

    private fun writeFromOutside(oldAddress: String, newAddress: String) {
        stored = stored!!.replace(oldAddress, newAddress)
    }

    @Test
    fun `after a stop the state is read from storage again`() {
        sut.bluetoothAddress = "AA:AA"
        sut.onStop()
        // Somebody else writes the stored state while the driver is stopped.
        writeFromOutside("AA:AA", "BB:BB")

        assertThat(sut.bluetoothAddress).isEqualTo("BB:BB")
    }

    @Test
    fun `a stop keeps also what was not stored yet`() {
        sut.bluetoothAddress = "AA:AA"
        // A counter that is stored only with the next change.
        sut.successfulConnections = 3

        sut.onStop()

        assertThat(sut.successfulConnections).isEqualTo(3)
        assertThat(sut.bluetoothAddress).isEqualTo("AA:AA")
    }

    @Test
    fun `a stop does not write over a stored state from outside`() {
        sut.bluetoothAddress = "AA:AA"
        writeFromOutside("AA:AA", "BB:BB")

        sut.onStop()

        assertThat(sut.bluetoothAddress).isEqualTo("BB:BB")
    }

    @Test
    fun `its own writes are not taken for a write from outside`() {
        sut.bluetoothAddress = "AA:AA"
        sut.reset()
        sut.bluetoothAddress = "CC:CC"

        assertThat(sut.reloadIfChangedOutside()).isFalse()
        assertThat(sut.bluetoothAddress).isEqualTo("CC:CC")
    }

    @Test
    fun `a stored state written from outside is loaded again`() {
        sut.bluetoothAddress = "AA:AA"
        writeFromOutside("AA:AA", "BB:BB")

        assertThat(sut.reloadIfChangedOutside()).isTrue()
        assertThat(sut.bluetoothAddress).isEqualTo("BB:BB")
        // The new state is now the known one.
        assertThat(sut.reloadIfChangedOutside()).isFalse()
    }

    @Test
    fun `a state that was never read has nothing to load again`() {
        stored = "{\"bluetoothAddress\":\"BB:BB\"}"

        assertThat(sut.reloadIfChangedOutside()).isFalse()
        assertThat(sut.bluetoothAddress).isEqualTo("BB:BB")
    }
}
