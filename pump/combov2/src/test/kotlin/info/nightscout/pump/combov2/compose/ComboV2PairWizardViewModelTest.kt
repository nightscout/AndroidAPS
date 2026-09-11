package info.nightscout.pump.combov2.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import com.google.common.truth.Truth.assertThat
import info.nightscout.comboctl.base.PAIRING_PIN_SIZE
import info.nightscout.comboctl.base.ProgressReport
import info.nightscout.pump.combov2.ComboV2Plugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ComboV2PairWizardViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper

    private val combov2Plugin: ComboV2Plugin = mock()

    private lateinit var sut: ComboV2PairWizardViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        // The uiState field is combine(...).stateIn(viewModelScope, WhileSubscribed, default): the 3 plugin flows
        // must be non-null to build the combine, but with no collector the combine never runs (uiState stays default).
        whenever(combov2Plugin.driverStateUIFlow).thenReturn(MutableStateFlow(ComboV2Plugin.DriverState.NotInitialized))
        whenever(combov2Plugin.pairingProgressUiFlow).thenReturn(emptyFlow<ProgressReport>())
        whenever(combov2Plugin.previousPairingAttemptFailedFlow).thenReturn(MutableStateFlow(false))
        sut = ComboV2PairWizardViewModel(aapsLogger, rh, combov2Plugin)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState reports the driver as not initialized`() {
        val state = sut.uiState.value
        assertThat(state.phase).isEqualTo(PairWizardPhase.DriverNotInitialized)
        assertThat(state.pinText).isEmpty()
    }

    @Test
    fun `submitPin rejects an incomplete PIN`() {
        assertThat(sut.submitPin()).isFalse()

        sut.onPinTextChange("123") // fewer than PAIRING_PIN_SIZE digits
        assertThat(sut.submitPin()).isFalse()
    }

    @Test
    fun `submitPin accepts a full-length PIN`() {
        sut.onPinTextChange("1".repeat(PAIRING_PIN_SIZE))

        assertThat(sut.submitPin()).isTrue()
    }

    @Test
    fun `onPinTextChange filters non-digits and caps at the PIN size`() {
        // 4 digits interleaved with letters -> still below PAIRING_PIN_SIZE, so submit fails.
        sut.onPinTextChange("1a2b3c4d")
        assertThat(sut.submitPin()).isFalse()

        // Overflowing digits are capped to exactly PAIRING_PIN_SIZE, so submit succeeds.
        sut.onPinTextChange("9".repeat(PAIRING_PIN_SIZE + 5))
        assertThat(sut.submitPin()).isTrue()
    }
}
