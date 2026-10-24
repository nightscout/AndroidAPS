package app.aaps.pump.diaconn.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    // Without BLUETOOTH_SCAN, startScan throws a SecurityException and the app ends
    @Test
    fun startScan_withoutScanPermission_doesNotTouchBluetooth() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_DENIED)

        sut.startScan()

        verify(context, never()).getSystemService(Context.BLUETOOTH_SERVICE)
    }

    @Test
    fun startScan_withScanPermission_goesOnToTheScan() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_GRANTED)

        // The Android stubs of a JVM test return null from ScanFilter.Builder, so the call ends
        // there with a NullPointerException. It got past the permission check if nothing was logged.
        runCatching { sut.startScan() }

        verify(aapsLogger, never()).error(any<LTag>(), any<String>())
    }
}
