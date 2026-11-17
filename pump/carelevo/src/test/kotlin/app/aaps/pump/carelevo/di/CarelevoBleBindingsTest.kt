package app.aaps.pump.carelevo.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.carelevo.ble.CarelevoBleTransportImpl
import app.aaps.pump.carelevo.config.BleEnvConfig
import app.aaps.pump.carelevo.emulator.CarelevoEmulatorBleTransport
import app.aaps.pump.carelevo.emulator.CarelevoPumpEmulator
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Which Bluetooth the driver gets: the real one, or the in-tree emulator when its marker file is there. The marker
 * files of the alarm scenarios set the emulated patch's alarm flags. Bluetooth v2 changes what this provides, so the
 * choice is pinned here first.
 */
internal class CarelevoBleBindingsTest {

    private val realTransport: CarelevoBleTransportImpl = mock()
    private val aapsLogger: AAPSLogger = mock()

    private fun config(vararg enabled: ExternalOptions): Config = mock<Config>().also { config ->
        whenever(config.isEnabled(any())).thenAnswer { it.arguments[0] in enabled }
    }

    @Test
    fun `without the marker file the driver gets the real Bluetooth`() {
        val transport = CarelevoBleBindings.provideCarelevoBleTransport(config(), realTransport, aapsLogger, PumpEmulators())

        assertThat(transport).isSameInstanceAs(realTransport)
    }

    @Test
    fun `with the marker file the driver gets the emulator and no alarm`() {
        val transport = CarelevoBleBindings.provideCarelevoBleTransport(config(ExternalOptions.EMULATE_CARELEVO), realTransport, aapsLogger, PumpEmulators())

        val emulator = (transport as CarelevoEmulatorBleTransport).emulator
        assertThat(emulator.state.criticalAlarmFlags.lowBattery).isFalse()
        assertThat(emulator.state.criticalAlarmFlags.occlusionDetected).isFalse()
        assertThat(emulator.state.advisoryAlarmFlags.lowBattery).isFalse()
        assertThat(emulator.state.advisoryAlarmFlags.outOfRangeTemperature).isFalse()
    }

    /** Critical and advisory are separate: the same condition is a warning on one and an alert on the other. */
    @Test
    fun `the alarm marker files set the alarm flags of the emulated patch`() {
        val config = config(
            ExternalOptions.EMULATE_CARELEVO,
            ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY,
            ExternalOptions.EMULATE_CARELEVO_OCCLUSION,
            ExternalOptions.EMULATE_CARELEVO_LOW_BATTERY_ALERT,
            ExternalOptions.EMULATE_CARELEVO_INVALID_TEMPERATURE
        )

        val transport = CarelevoBleBindings.provideCarelevoBleTransport(config, realTransport, aapsLogger, PumpEmulators())

        val state = (transport as CarelevoEmulatorBleTransport).emulator.state
        assertThat(state.criticalAlarmFlags.lowBattery).isTrue()
        assertThat(state.criticalAlarmFlags.occlusionDetected).isTrue()
        assertThat(state.advisoryAlarmFlags.lowBattery).isTrue()
        assertThat(state.advisoryAlarmFlags.outOfRangeTemperature).isTrue()
    }

    /** A test that restarts the app keeps the patch; only the radio is new. */
    @Test
    fun `a second graph on the same emulators gets the same patch and a new radio`() {
        val emulators = PumpEmulators()
        val config = config(ExternalOptions.EMULATE_CARELEVO)

        val first = CarelevoBleBindings.provideCarelevoBleTransport(config, realTransport, aapsLogger, emulators) as CarelevoEmulatorBleTransport
        val second = CarelevoBleBindings.provideCarelevoBleTransport(config, realTransport, aapsLogger, emulators) as CarelevoEmulatorBleTransport

        assertThat(second).isNotSameInstanceAs(first)
        assertThat(second.emulator).isSameInstanceAs(first.emulator)
        assertThat(emulators.getOrCreate(CarelevoPumpEmulator.KEY) { error("not created") }).isSameInstanceAs(first.emulator)
    }

    @Test
    fun `the characteristics are the write and the notify characteristic of the patch`() {
        assertThat(CarelevoBleBindings.provideRxCharacteristicUuid()).isEqualTo(UUID.fromString(BleEnvConfig.BLE_RX_CHAR_UUID))
        assertThat(CarelevoBleBindings.provideTxCharacteristicUuid()).isEqualTo(UUID.fromString(BleEnvConfig.BLE_TX_CHAR_UUID))
    }
}
