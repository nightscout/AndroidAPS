package app.aaps.pump.danar.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.rfcomm.RfcommDevice
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.dana.keys.DanaIntNonKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test for [DanaRPairWizardViewModel]. The pure state-mutating setters are asserted directly; the
 * pairing state machine (`pair()` → CONNECTING, then a pump-status update → COMPLETE / ERROR) is driven
 * by emitting onto the `rxBus` streams the view-model subscribes to in `init`, exactly as the on-device
 * handshake would. `aapsSchedulers.main` is the trampoline so those emissions deliver synchronously, and
 * `pair()`'s `viewModelScope.launch { readStatus(...) }` stays deferred under a StandardTestDispatcher, so
 * every assertion is deterministic and synchronous.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DanaRPairWizardViewModelTest {

    private val aapsLogger: AAPSLogger = mock()
    private val rh: ResourceHelper = mock()
    private val preferences: Preferences = mock()
    private val commandQueue: CommandQueue = mock()
    private val pumpSync: PumpSync = mock()
    private val rxBus: RxBus = mock()
    private val aapsSchedulers: AapsSchedulers = mock()
    private val rfcommTransport: RfcommTransport = mock()
    private val danaPump: DanaPump = mock()

    // Real subjects so the two init collectors can be driven; the CONNECTING→COMPLETE/ERROR transition
    // is only reachable by emitting a pump-status event (see onPumpStatusUpdate).
    private val statusSubject: PublishSubject<EventDanaRNewStatus> = PublishSubject.create()
    private val initSubject: PublishSubject<EventInitializationChanged> = PublishSubject.create()

    private lateinit var sut: DanaRPairWizardViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        // init -> reset() reads these prefs synchronously (a mock String default is null -> NPE).
        whenever(preferences.get(DanaIntNonKey.Password)).thenReturn(0)
        whenever(preferences.get(DanaStringNonKey.RName)).thenReturn("")
        // init -> reset() -> refreshBondedDevices() reads the transport synchronously.
        whenever(rfcommTransport.getBondedDevices()).thenReturn(emptyList())
        // init subscribes to these rx streams via aapsSchedulers.main.
        whenever(rxBus.toObservable(EventDanaRNewStatus::class.java)).thenReturn(statusSubject)
        whenever(rxBus.toObservable(EventInitializationChanged::class.java)).thenReturn(initSubject)
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(rh.gs(anyInt())).thenReturn("device changed")

        sut = buildViewModel()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel() = DanaRPairWizardViewModel(
        aapsLogger, rh, preferences, danaPump, commandQueue, pumpSync, rxBus, aapsSchedulers, rfcommTransport
    )

    /** Selects a device + password and calls `pair()`, leaving the wizard on the CONNECTING step. */
    private fun moveToConnecting() {
        sut.onDeviceSelected(BondedDevice(name = DEVICE_NAME, address = DEVICE_ADDRESS))
        sut.updatePassword("1234")
        sut.pair()
    }

    // ---- initial state / setters ----------------------------------------------------------------

    @Test
    fun `default state starts on CONFIGURE step with empty password`() {
        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(PairWizardStep.CONFIGURE)
        assertThat(state.password).isEmpty()
        assertThat(state.selectedDevice).isNull()
        assertThat(state.isConnecting).isFalse()
        assertThat(state.passwordVerified).isNull()
    }

    @Test
    fun `updatePassword keeps only digits and caps at four characters`() {
        sut.updatePassword("12ab34567")
        assertThat(sut.uiState.value.password).isEqualTo("1234")
    }

    @Test
    fun `updatePassword with no digits yields empty string`() {
        sut.updatePassword("abcd")
        assertThat(sut.uiState.value.password).isEmpty()
    }

    @Test
    fun `onDeviceSelected stores the chosen device`() {
        val device = BondedDevice(name = "BEH12345AB", address = "00:11:22:33:44:55")
        sut.onDeviceSelected(device)
        assertThat(sut.uiState.value.selectedDevice).isEqualTo(device)
    }

    @Test
    fun `refreshBondedDevices keeps only devices matching the Dana name pattern`() {
        whenever(rfcommTransport.getBondedDevices()).thenReturn(
            listOf(
                RfcommDevice(name = "BEH12345AB", address = "AA:BB:CC:DD:EE:01"),
                RfcommDevice(name = "MyPhone", address = "AA:BB:CC:DD:EE:02")
            )
        )

        sut.refreshBondedDevices()

        val devices = sut.uiState.value.bondedDevices
        assertThat(devices).hasSize(1)
        assertThat(devices.first().name).isEqualTo("BEH12345AB")
    }

    @Test
    fun `goBack returns to CONFIGURE and clears connecting state`() {
        sut.goBack()

        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(PairWizardStep.CONFIGURE)
        assertThat(state.isConnecting).isFalse()
        assertThat(state.passwordVerified).isNull()
    }

    // ---- reset() branches -----------------------------------------------------------------------

    @Test
    fun `reset pre-fills the password and auto-selects the saved bonded device`() {
        whenever(preferences.get(DanaIntNonKey.Password)).thenReturn(1234)
        whenever(preferences.get(DanaStringNonKey.RName)).thenReturn(DEVICE_NAME)
        whenever(rfcommTransport.getBondedDevices()).thenReturn(
            listOf(RfcommDevice(name = DEVICE_NAME, address = DEVICE_ADDRESS))
        )

        val state = buildViewModel().uiState.value // init calls reset()

        assertThat(state.password).isEqualTo("1234")
        assertThat(state.selectedDevice?.name).isEqualTo(DEVICE_NAME)
    }

    // ---- pair() ---------------------------------------------------------------------------------

    @Test
    fun `pair with a device and password moves to CONNECTING, persists and triggers the connection`() {
        sut.onDeviceSelected(BondedDevice(name = DEVICE_NAME, address = DEVICE_ADDRESS))
        sut.updatePassword("1234")

        sut.pair()

        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(PairWizardStep.CONNECTING)
        assertThat(state.isConnecting).isTrue()
        verify(preferences).put(DanaStringNonKey.RName, DEVICE_NAME)
        verify(preferences).put(DanaIntNonKey.Password, 1234)
        verify(pumpSync).connectNewPump(true)
    }

    @Test
    fun `pair without a selected device stays on CONFIGURE`() {
        sut.updatePassword("1234")

        sut.pair()

        assertThat(sut.uiState.value.step).isEqualTo(PairWizardStep.CONFIGURE)
        verify(pumpSync, never()).connectNewPump(true)
    }

    @Test
    fun `pair with an empty password stays on CONFIGURE`() {
        sut.onDeviceSelected(BondedDevice(name = DEVICE_NAME, address = DEVICE_ADDRESS))

        sut.pair()

        assertThat(sut.uiState.value.step).isEqualTo(PairWizardStep.CONFIGURE)
        verify(pumpSync, never()).connectNewPump(true)
    }

    // ---- pairing state machine (onPumpStatusUpdate) ---------------------------------------------

    @Test
    fun `a password-OK status update while CONNECTING completes the wizard`() {
        moveToConnecting()
        whenever(danaPump.isPasswordOK).thenReturn(true)

        statusSubject.onNext(EventDanaRNewStatus())

        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(PairWizardStep.COMPLETE)
        assertThat(state.passwordVerified).isTrue()
        assertThat(state.isConnecting).isFalse()
    }

    @Test
    fun `a wrong-password status update while CONNECTING shows the error step`() {
        moveToConnecting()
        whenever(danaPump.isPasswordOK).thenReturn(false)

        statusSubject.onNext(EventDanaRNewStatus())

        val state = sut.uiState.value
        assertThat(state.step).isEqualTo(PairWizardStep.ERROR)
        assertThat(state.passwordVerified).isFalse()
    }

    @Test
    fun `an initialization-changed event also completes a CONNECTING wizard`() {
        moveToConnecting()
        whenever(danaPump.isPasswordOK).thenReturn(true)

        initSubject.onNext(EventInitializationChanged())

        assertThat(sut.uiState.value.step).isEqualTo(PairWizardStep.COMPLETE)
    }

    @Test
    fun `a status update while still on CONFIGURE is ignored`() {
        whenever(danaPump.isPasswordOK).thenReturn(true)

        statusSubject.onNext(EventDanaRNewStatus())

        assertThat(sut.uiState.value.step).isEqualTo(PairWizardStep.CONFIGURE)
    }

    private companion object {

        private const val DEVICE_NAME = "DAN12345AB"
        private const val DEVICE_ADDRESS = "00:11:22:33:44:55"
    }
}
