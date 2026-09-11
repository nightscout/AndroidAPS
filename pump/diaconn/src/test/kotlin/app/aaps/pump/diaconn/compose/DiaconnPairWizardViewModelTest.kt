package app.aaps.pump.diaconn.compose

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.events.EventDiaconnG8DeviceChange
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

/**
 * Unit test for the synchronous state logic of [DiaconnPairWizardViewModel]. The VM has no init{}
 * launch and no viewModelScope work, so the default [DiaconnPairWizardUiState] and the pure
 * [DiaconnPairWizardViewModel.selectDevice] transition can be asserted directly. BLE scanning is
 * skipped because the mocked [Context] returns a null BluetoothManager, so the scanner / adapter
 * branches are all null-safe no-ops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DiaconnPairWizardViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var context: Context

    private lateinit var sut: DiaconnPairWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        sut = DiaconnPairWizardViewModel(aapsLogger, preferences, rxBus, context)
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
    fun selectDevice_movesToComplete_persistsAddressAndName_andBroadcasts() {
        val device = ScannedDevice(name = "Diaconn G8", address = "AA:BB:CC:DD:EE:FF")

        sut.selectDevice(device)

        assertThat(sut.uiState.value.step).isEqualTo(DiaconnPairStep.COMPLETE)
        verify(preferences).put(DiaconnStringNonKey.Address, "AA:BB:CC:DD:EE:FF")
        verify(preferences).put(DiaconnStringNonKey.Name, "Diaconn G8")
        verify(rxBus).send(any<EventDiaconnG8DeviceChange>())
    }
}
