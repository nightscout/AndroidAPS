package app.aaps.pump.carelevo.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.carelevo.emulator.CarelevoEmulatorPeripheral
import app.aaps.pump.carelevo.emulator.CarelevoPumpEmulator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Which Bluetooth the driver gets: the phone's, or an emulated central with the in-tree emulator when its marker file
 * is there. The marker files of the alarm scenarios set the emulated patch's alarm flags.
 */
internal class CarelevoBleBindingsTest {

    private val phone: BleCentral = mock()
    private val aapsLogger: AAPSLogger = mock()

    private fun config(vararg enabled: ExternalOptions): Config = mock<Config>().also { config ->
        whenever(config.isEnabled(any())).thenAnswer { it.arguments[0] in enabled }
    }

    private fun peripheralOf(central: BleCentral): CarelevoEmulatorPeripheral =
        (central as EmulatedBleCentral).devices.single() as CarelevoEmulatorPeripheral

    @Test
    fun `without the marker file the driver gets the phone's Bluetooth`() {
        val central = CarelevoBleBindings.provideCarelevoCentral(config(), phone, aapsLogger, PumpEmulators())

        assertThat(central).isSameInstanceAs(phone)
    }

    @Test
    fun `with the marker file the driver gets the emulated patch and no alarm`() {
        val central = CarelevoBleBindings.provideCarelevoCentral(config(ExternalOptions.EMULATE_CARELEVO), phone, aapsLogger, PumpEmulators())

        val state = peripheralOf(central).emulator.state
        assertThat(state.criticalAlarmFlags.lowBattery).isFalse()
        assertThat(state.criticalAlarmFlags.occlusionDetected).isFalse()
        assertThat(state.advisoryAlarmFlags.lowBattery).isFalse()
        assertThat(state.advisoryAlarmFlags.outOfRangeTemperature).isFalse()
    }

    /** The emulated patch was always bonded: the pairing bonds only a patch that is not. */
    @Test
    fun `the emulated patch is bonded`() = runBlocking {
        val central = CarelevoBleBindings.provideCarelevoCentral(config(ExternalOptions.EMULATE_CARELEVO), phone, aapsLogger, PumpEmulators())

        assertThat(central.bonding!!.bondState(peripheralOf(central).device).first()).isEqualTo(BondState.BONDED)
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

        val central = CarelevoBleBindings.provideCarelevoCentral(config, phone, aapsLogger, PumpEmulators())

        val state = peripheralOf(central).emulator.state
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

        val first = CarelevoBleBindings.provideCarelevoCentral(config, phone, aapsLogger, emulators)
        val second = CarelevoBleBindings.provideCarelevoCentral(config, phone, aapsLogger, emulators)

        assertThat(second).isNotSameInstanceAs(first)
        assertThat(peripheralOf(second)).isNotSameInstanceAs(peripheralOf(first))
        assertThat(peripheralOf(second).emulator).isSameInstanceAs(peripheralOf(first).emulator)
        assertThat(emulators.getOrCreate(CarelevoPumpEmulator.KEY) { error("not created") }).isSameInstanceAs(peripheralOf(first).emulator)
    }
}
