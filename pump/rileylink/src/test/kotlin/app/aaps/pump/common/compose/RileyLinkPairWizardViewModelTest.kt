package app.aaps.pump.common.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import com.google.common.truth.Truth.assertThat
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

internal class RileyLinkPairWizardViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var context: Context

    private val rileyLinkUtil: RileyLinkUtil = mock()

    private lateinit var sut: RileyLinkPairWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // No init block / no viewModelScope — construction is trivial.
        sut = RileyLinkPairWizardViewModel(aapsLogger, preferences, activePlugin, rileyLinkUtil, context)
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
