package app.aaps.plugins.eversense.pairing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.FakeBleCentral
import app.aaps.plugins.eversense.connection.EversenseSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/** The pairing scan: which devices the wizard lists. */
class EversensePairingScanTest {

    private val central = FakeBleCentral()
    private val pairing = EversensePairingImpl({ error("the scan needs no driver") }, central, mock<Preferences>(), mock<AAPSLogger>())

    private fun advertisement(address: String, name: String?, services: List<BleUuid>) =
        Advertisement(DeviceId(address), name, rssi = -60, serviceUuids = services, manufacturerData = emptyMap(), serviceData = emptyMap())

    /** A test transmitter showed "D313640": the service decides, not the name. */
    @Test fun `a transmitter is found by its service whatever its name`() = runTest {
        central.advertisements += listOf(
            advertisement("00:00:00:00:00:01", "D313640", listOf(EversenseSession.SERVICE)),
            advertisement("00:00:00:00:00:02", "T0214389", listOf(EversenseSession.SERVICE)),
            advertisement("00:00:00:00:00:03", "365123", listOf(BleUuid.of("180d"), EversenseSession.SERVICE))
        )

        assertThat(pairing.scan().last().map { it.name }).containsExactly("D313640", "T0214389", "365123").inOrder()
    }

    @Test fun `a device without the Eversense service is not listed even with a transmitter name`() = runTest {
        central.advertisements += listOf(
            advertisement("00:00:00:00:00:01", "T0214389", listOf(BleUuid.of("180d"))),
            advertisement("00:00:00:00:00:02", "D313640", emptyList()),
            advertisement("00:00:00:00:00:03", "D313640", listOf(EversenseSession.SERVICE))
        )

        assertThat(pairing.scan().last().map { it.address }).containsExactly("00:00:00:00:00:03")
    }

    /** The list would have nothing to show for it */
    @Test fun `a transmitter with no name is not listed`() = runTest {
        central.advertisements += listOf(
            advertisement("00:00:00:00:00:01", null, listOf(EversenseSession.SERVICE)),
            advertisement("00:00:00:00:00:02", "D313640", listOf(EversenseSession.SERVICE))
        )

        assertThat(pairing.scan().last().map { it.name }).containsExactly("D313640")
    }
}
