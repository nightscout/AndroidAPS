package app.aaps.pump.diaconn.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.events.EventDiaconnG8DeviceChange
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The pairing wizard of the Diaconn G8. It scans and bonds through the transport, so the emulated pump is found the
 * same way as a real one. The transport itself is tested by the shared transport contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DiaconnPairWizardViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var bleTransport: BleTransport
    @Mock private lateinit var adapter: BleAdapter
    @Mock private lateinit var scanner: BleScanner

    private val scanned = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 10)
    private lateinit var dispatcher: TestDispatcher
    private lateinit var sut: DiaconnPairWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        whenever(bleTransport.adapter).thenReturn(adapter)
        whenever(bleTransport.scanner).thenReturn(scanner)
        whenever(scanner.scannedDevices).thenReturn(scanned)
        sut = DiaconnPairWizardViewModel(aapsLogger, preferences, rxBus, bleTransport)
        dispatcher.scheduler.runCurrent()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_isBleScanWithNoDevices() {
        val state = sut.uiState.value

        assertThat(state.step).isEqualTo(DiaconnPairStep.BLE_SCAN)
        assertThat(state.devices).isEmpty()
    }

    @Test
    fun selectDevice_movesToComplete_persistsAddressAndName_bondsAndBroadcasts() {
        val device = ScannedDevice(name = "Diaconn G8", address = "AA:BB:CC:DD:EE:FF")

        sut.selectDevice(device)

        assertThat(sut.uiState.value.step).isEqualTo(DiaconnPairStep.COMPLETE)
        verify(preferences).put(DiaconnStringNonKey.Address, "AA:BB:CC:DD:EE:FF")
        verify(preferences).put(DiaconnStringNonKey.Name, "Diaconn G8")
        verify(adapter).createBond("AA:BB:CC:DD:EE:FF")
        verify(scanner).stopScan()
        verify(rxBus).send(any<EventDiaconnG8DeviceChange>())
    }

    @Test
    fun startScan_scansThroughTheTransport() {
        sut.startScan()

        verify(scanner).startScan()
    }

    // A pump seen twice in one scan is listed once.
    @Test
    fun aDeviceTheScanFindsIsListedOnce() {
        val device = ScannedDevice(name = "DIACONN-64004", address = "DB:B0:8B:D9:48:97")

        scanned.tryEmit(device)
        scanned.tryEmit(device)
        dispatcher.scheduler.runCurrent()

        assertThat(sut.uiState.value.devices).containsExactly(device)
    }
}
