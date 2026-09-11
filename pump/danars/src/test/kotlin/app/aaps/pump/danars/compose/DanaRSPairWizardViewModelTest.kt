package app.aaps.pump.danars.compose

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.services.BLEComm
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class DanaRSPairWizardViewModelTest {

    private val aapsLogger: AAPSLogger = mock()
    private val rh: ResourceHelper = mock()
    private val bleTransport: BleTransport = mock()
    private val scanner: BleScanner = mock()
    private val adapter: BleAdapter = mock()
    private val preferences: Preferences = mock()
    private val config: Config = mock()
    private val pumpSync: PumpSync = mock()
    private val commandQueue: CommandQueue = mock()
    private val bleComm: BLEComm = mock()
    private val danaRSPlugin: DanaRSPlugin = mock()

    // Real flows so the two init collectors can be driven; the pairing state machine is only
    // reachable by emitting onto pairingState (see onPairingStateChanged).
    private val pairingStateFlow = MutableStateFlow(PairingState(step = PairingStep.IDLE))
    private val scannedDevicesFlow = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 8)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var sut: DanaRSPairWizardViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(bleTransport.pairingState).thenReturn(pairingStateFlow)
        whenever(bleTransport.scanner).thenReturn(scanner)
        whenever(bleTransport.adapter).thenReturn(adapter)
        whenever(scanner.scannedDevices).thenReturn(scannedDevicesFlow)
        whenever(rh.gs(anyInt())).thenReturn("error")
        sut = DanaRSPairWizardViewModel(
            aapsLogger, rh, bleTransport, bleComm, danaRSPlugin, preferences, config, pumpSync, commandQueue
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** Runs the init collectors and any tasks scheduled at the current virtual time — but not the
     *  pairing timeout's delay(), so a step assertion isn't clobbered by a fired timeout. */
    private fun runCurrent() = testDispatcher.scheduler.runCurrent()

    private val device = ScannedDevice(name = "UHH00002TI", address = "00:11:22:33:44:55")

    // ---- initial state / setters (existing coverage) ------------------------------------------

    @Test
    fun `default uiState starts on the BLE scan step`() {
        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(WizardStep.BLE_SCAN)
        assertThat(state.devices).isEmpty()
        assertThat(state.password).isEmpty()
    }

    @Test
    fun `password and pin setters update the state`() {
        sut.updatePassword("1a2b")
        sut.updatePin1("0123456789ab")
        sut.updatePin2("00112233")

        val state = sut.uiState.value
        assertThat(state.password).isEqualTo("1a2b")
        assertThat(state.pin1).isEqualTo("0123456789ab")
        assertThat(state.pin2).isEqualTo("00112233")
    }

    @Test
    fun `submitPassword ignores a malformed password`() {
        sut.updatePassword("12") // too short
        sut.submitPassword()

        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.BLE_SCAN)
    }

    @Test
    fun `submitPassword with a valid 4 hex code advances to pairing progress`() {
        sut.updatePassword("1a2b")
        sut.submitPassword()

        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.PAIRING_PROGRESS)
    }

    @Test
    fun `reset returns the wizard to its initial state`() {
        sut.updatePassword("1a2b")
        sut.reset()

        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(WizardStep.BLE_SCAN)
        assertThat(state.password).isEmpty()
    }

    // ---- scan / select --------------------------------------------------------------------------

    @Test
    fun `startScan clears devices and starts the scanner`() {
        sut.startScan()

        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.BLE_SCAN)
        assertThat(sut.uiState.value.devices).isEmpty()
        verify(scanner).startScan()
    }

    @Test
    fun `selectDevice stops scanning and moves to pairing progress`() {
        sut.selectDevice(device)

        val state = sut.uiState.value
        assertThat(state.selectedDevice).isEqualTo(device)
        assertThat(state.step).isEqualTo(WizardStep.PAIRING_PROGRESS)
        verify(scanner).stopScan()
        verify(commandQueue).clear()
        verify(danaRSPlugin).mDeviceName = device.name
    }

    @Test
    fun `a scanned device with a matching serial-number name is added to the list`() {
        runCurrent() // start the scannedDevices collector
        scannedDevicesFlow.tryEmit(device)
        runCurrent()

        assertThat(sut.uiState.value.devices).containsExactly(device)
    }

    @Test
    fun `a scanned device whose name is not a Dana serial number is ignored`() {
        runCurrent()
        scannedDevicesFlow.tryEmit(ScannedDevice(name = "SomeOtherPump", address = "AA:BB:CC:DD:EE:FF"))
        runCurrent()

        assertThat(sut.uiState.value.devices).isEmpty()
    }

    // ---- PIN submission -------------------------------------------------------------------------

    @Test
    fun `submitPin ignores malformed PIN input`() {
        sut.updatePin1("short")   // not 12 hex chars
        sut.updatePin2("00112233")
        sut.submitPin()

        // Never advances, and never reaches the checksum/finishV3Pairing path.
        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.BLE_SCAN)
        verify(bleComm, never()).finishV3Pairing()
    }

    // ---- finish / cancel / retry ----------------------------------------------------------------

    @Test
    fun `finishWizard without a selected device does nothing`() {
        sut.finishWizard()

        verify(preferences, never()).put(eq(DanaStringNonKey.MacAddress), any())
        verify(danaRSPlugin, never()).changePump()
    }

    @Test
    fun `finishWizard persists the pump and triggers the connection flow`() {
        sut.selectDevice(device) // sets selectedDevice

        sut.finishWizard()

        verify(preferences).put(DanaStringNonKey.MacAddress, device.address)
        verify(preferences).put(DanaStringNonKey.RsName, device.name)
        verify(adapter).createBond(device.address)
        verify(pumpSync).connectNewPump(true)
        verify(danaRSPlugin).changePump()
    }

    @Test
    fun `cancel stops scanning and returns the pairing state to idle`() {
        sut.cancel()

        verify(scanner).stopScan()
        verify(bleTransport).updatePairingState(PairingState(step = PairingStep.IDLE))
    }

    @Test
    fun `retry with a selected device reconnects`() {
        sut.selectDevice(device)

        sut.retry()

        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.PAIRING_PROGRESS)
    }

    @Test
    fun `retry without a selected device restarts scanning`() {
        sut.retry()

        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.BLE_SCAN)
        verify(scanner, org.mockito.kotlin.atLeastOnce()).startScan()
    }

    // ---- pairing state machine (onPairingStateChanged) ------------------------------------------

    @Test
    fun `CONNECTING moves the wizard to pairing progress`() {
        drivePairing(PairingStep.CONNECTING)
        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.PAIRING_PROGRESS)
    }

    @Test
    fun `WAITING_FOR_PASSWORD shows the password step`() {
        drivePairing(PairingStep.WAITING_FOR_PASSWORD)
        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.ENTER_PASSWORD)
    }

    @Test
    fun `WAITING_FOR_PIN shows the PIN step`() {
        drivePairing(PairingStep.WAITING_FOR_PIN)
        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.ENTER_PIN)
    }

    @Test
    fun `CONNECTED completes the wizard`() {
        drivePairing(PairingStep.CONNECTED)
        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.COMPLETE)
    }

    @Test
    fun `ERROR surfaces the error step and message`() {
        drivePairing(PairingStep.ERROR, message = "boom")
        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.ERROR)
        assertThat(sut.uiState.value.errorMessage).isEqualTo("boom")
    }

    @Test
    fun `IDLE while pairing is treated as a failure`() {
        drivePairing(PairingStep.CONNECTING)        // now in PAIRING_PROGRESS
        drivePairing(PairingStep.IDLE)              // a drop mid-pairing
        assertThat(sut.uiState.value.step).isEqualTo(WizardStep.ERROR)
    }

    /** Starts the collectors, emits [step] onto the pairing flow, and drains the current tasks. */
    private fun drivePairing(step: PairingStep, message: String? = null) {
        runCurrent()
        pairingStateFlow.value = PairingState(step = step, errorMessage = message)
        runCurrent()
    }
}
