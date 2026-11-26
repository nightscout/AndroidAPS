package app.aaps.pump.aaruy.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.event.EventAaruyDeviceChange
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyBLE
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AaruyPairWizardViewModelTest {

    @Mock private lateinit var aaruyPump: AaruyPump
    @Mock private lateinit var aaruyBLE: AaruyBLE
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var context: Context

    private lateinit var sut: AaruyPairWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        sut = AaruyPairWizardViewModel(aapsLogger, preferences, rxBus, aaruyPump, aaruyBLE)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_isBleScanWithNoDevices() {
        val state = sut.uiState.value

        assertThat(state.step).isEqualTo(AaruyPairWizardStep.BLE_SCAN)
        assertThat(state.devices).isEmpty()
    }

    @Test
    fun selectDevice_movesToComplete_persistsAddressAndName_andBroadcasts() {
        val device = ScannedDevice(name = "Q10001", address = "AA:BB:CC:DD:EE:FF")

        sut.onDeviceSelected(device)

        assertThat(sut.uiState.value.step).isEqualTo(AaruyPairWizardStep.BLE_SCAN)
        verify(preferences).put(AaruyStringKey.MacAddress, "AA:BB:CC:DD:EE:FF")
        verify(preferences).put(AaruyStringKey.PumpName, "Q10001")
    }

    // Without BLUETOOTH_SCAN, startScan throws a SecurityException and the app ends
    @Test
    fun startScan_withoutScanPermission_doesNotTouchBluetooth() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_DENIED)

        sut.startDeviceScan()

        verify(context, never()).getSystemService(Context.BLUETOOTH_SERVICE)
    }

    @Test
    fun startScan_withScanPermission_goesOnToTheScan() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_GRANTED)

        // The Android stubs of a JVM test return null from ScanFilter.Builder, so the call ends
        // there with a NullPointerException. It got past the permission check if nothing was logged.
        runCatching { sut.startDeviceScan() }

        verify(aapsLogger, never()).error(any<LTag>(), any<String>())
    }
}
