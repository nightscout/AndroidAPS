package app.aaps.pump.common.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.pump.ScanProblem
import app.aaps.core.ui.compose.pump.WizardScan
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class RileyLinkPairWizardViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var context: Context

    private val rileyLinkUtil: RileyLinkUtil = mock()
    private val adapterState = MutableStateFlow(AdapterState.ON)
    private val bleCentral: BleCentral = mock<BleCentral>().also { whenever(it.adapterState).thenReturn(adapterState) }
    private val dispatcher = StandardTestDispatcher()

    private lateinit var sut: RileyLinkPairWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // The scan's time limit runs in viewModelScope
        Dispatchers.setMain(dispatcher)
        sut = RileyLinkPairWizardViewModel(aapsLogger, preferences, activePlugin, rileyLinkUtil, context, bleCentral)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** With Bluetooth off nothing can be found: the step says so at once, and the RileyLink in use stays connected. */
    @Test
    fun `with Bluetooth off the scan ends at once and says why`() {
        adapterState.value = AdapterState.OFF

        sut.startScan()

        assertThat(sut.scanState.value.scanning).isFalse()
        assertThat(sut.scanState.value.problem).isEqualTo(ScanProblem.BLUETOOTH_OFF)
        verify(rileyLinkUtil, never()).sendBroadcastMessage(anyOrNull())
    }

    /** The scan ends by itself, so the step can say what it found and offer "Scan again". */
    @Test
    fun `the scan stops by itself after its time`() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_DENIED)
        sut.startScan()
        assertThat(sut.scanState.value.scanning).isTrue()

        dispatcher.scheduler.advanceTimeBy(WizardScan.DEFAULT_DURATION.inWholeMilliseconds + 1)

        assertThat(sut.scanState.value.scanning).isFalse()
    }

    @Test
    fun `default uiState starts on the BLE scan step`() {
        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(RileyLinkPairStep.BLE_SCAN)
        assertThat(state.devices).isEmpty()
        assertThat(state.selectedDevice).isNull()
    }

    @Test
    fun `selectDevice completes the wizard and records the device`() {
        // context.getSystemService(BLUETOOTH_SERVICE) is null on a mock (BLE branch skipped);
        // activePumpInternal is null so the `as? RileyLinkPumpDevice` branch is skipped too.
        val device = ScannedDevice(name = "RileyLink", address = "AA:BB:CC:DD:EE:FF")
        sut.selectDevice(device)

        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(RileyLinkPairStep.COMPLETE)
        assertThat(state.selectedDevice).isEqualTo(device)
    }

    // Without BLUETOOTH_SCAN, startScan throws a SecurityException and the app ends
    @Test
    fun `startScan without the scan permission keeps the RileyLink connected`() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_DENIED)

        sut.startScan()

        verify(rileyLinkUtil, never()).sendBroadcastMessage(anyOrNull())
    }

    @Test
    fun `startScan with the scan permission disconnects the RileyLink so it can be found`() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_GRANTED)

        // The Android stubs of a JVM test return null from ScanFilter.Builder, so the call ends
        // there with a NullPointerException. The broadcast is sent before that.
        runCatching { sut.startScan() }

        verify(rileyLinkUtil).sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnect)
    }
}
