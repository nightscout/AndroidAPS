package app.aaps.pump.insight.compose

import android.content.Context
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.insight.descriptors.InsightState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Unit test for [InsightPairState], the Insight pair-wizard state holder (the insight analog of the
 * pump pair-wizard ViewModels). It is NOT a ViewModel: [InsightComposeContent] builds it via
 * `remember` and it takes an injected [CoroutineScope]. It has no init block, so construction is
 * trivial; [onStateChanged] launches on `Dispatchers.Main.immediate` to map the connection state onto
 * the wizard step. The tested branches (CONNECTING / AWAITING_CODE_CONFIRMATION / CONNECTED) call
 * `stopBLScan()` which returns immediately while `scanning == false`, so they never touch Bluetooth
 * (the NOT_PAIRED branch would call `startBLScan()` → the mock Context, so it is deliberately not
 * exercised here).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class InsightPairStateTest {

    @Mock private lateinit var context: Context
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var rxBus: RxBus

    private lateinit var sut: InsightPairState

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // onStateChanged() launches on Dispatchers.Main.immediate — run it eagerly so state flips are observable.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sut = InsightPairState(context, pumpSync, rxBus, CoroutineScope(UnconfinedTestDispatcher()))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState starts on the search step`() {
        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(InsightPairStep.SEARCH)
        assertThat(state.devices).isEmpty()
        assertThat(state.verificationCode).isEmpty()
    }

    @Test
    fun `connecting states move the wizard to the connecting step`() {
        sut.onStateChanged(InsightState.CONNECTING)
        assertThat(sut.uiState.value.step).isEqualTo(InsightPairStep.CONNECTING)

        // A SATL/bind sub-state also maps to CONNECTING.
        sut.onStateChanged(InsightState.APP_BIND_MESSAGE)
        assertThat(sut.uiState.value.step).isEqualTo(InsightPairStep.CONNECTING)
    }

    @Test
    fun `awaiting code confirmation moves to the code-compare step`() {
        sut.onStateChanged(InsightState.AWAITING_CODE_CONFIRMATION)

        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(InsightPairStep.CODE_COMPARE)
        // No bound service, so the verification code resolves to empty rather than crashing.
        assertThat(state.verificationCode).isEmpty()
    }

    @Test
    fun `connected and disconnected states complete the wizard`() {
        sut.onStateChanged(InsightState.CONNECTED)
        assertThat(sut.uiState.value.step).isEqualTo(InsightPairStep.COMPLETED)

        sut.onStateChanged(InsightState.DISCONNECTED)
        assertThat(sut.uiState.value.step).isEqualTo(InsightPairStep.COMPLETED)
    }

    @Test
    fun `code actions are no-ops without a bound service`() {
        // Before start()/service bind these must not throw (service is null).
        sut.onDeviceSelected(InsightPairDevice(address = "AA:BB:CC:DD:EE:FF", name = "Insight"))
        sut.onConfirmCode()
        sut.onRejectCode()

        assertThat(sut.uiState.value.devices).isEmpty()
    }
}
