package app.aaps.pump.common.compose

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

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
}
