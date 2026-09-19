package app.aaps.pump.carelevo.presentation.viewmodel

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectPrepareEvent
import app.aaps.pump.carelevo.presentation.model.CarelevoOverviewEvent
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [CarelevoPatchConnectViewModel] — the activation wizard's scan/pair/discard step.
 *
 * **Plain JVM (JUnit5), not Robolectric.** The ViewModel touches no Android framework: it only drives
 * coroutines, RxJava and flows over mockable collaborators. `viewModelScope` needs a Main dispatcher,
 * which [Dispatchers.setMain] supplies. Every collaborator is mocked — the final Kotlin classes
 * ([CarelevoPatch], [CarelevoBleSession], the two use cases) mock fine under Mockito 5's inline
 * mock-maker, exactly as `CarelevoPumpPluginTestBase` in this module already does.
 *
 * **Dispatcher strategy** (the VM mixes two):
 *  - `triggerEvent`, and the queue branch of `startPatchDiscardProcess`, use a plain
 *    `viewModelScope.launch { }` → they inherit Main. Main is an [UnconfinedTestDispatcher], so those
 *    bodies run **eagerly and synchronously** inside the call and can be asserted straight after it.
 *  - `startScan` / `startConnect` launch with an explicit `Dispatchers.IO`, which overrides the test
 *    Main dispatcher and cannot be virtualised (the VM injects no dispatcher). Those run on the real
 *    IO pool, so their tests [awaitEvent] on the terminal event instead of advancing virtual time.
 *
 * `startPatchForceDiscard`'s Rx chain is pinned to [Schedulers.trampoline] via the mocked
 * [AapsSchedulers], so it too completes synchronously on the test thread.
 *
 * One-shot events are captured through a collector subscribed in [setUp] — [CarelevoPatchConnectViewModel.event]
 * is a consume-once `EventFlow`, so exactly one collector must own it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchConnectViewModelTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var sp: SP
    @Mock lateinit var bleSession: CarelevoBleSession
    @Mock lateinit var transport: CarelevoBleTransport
    @Mock lateinit var scanner: BleScanner
    @Mock lateinit var adapter: BleAdapter
    @Mock lateinit var connectNewPatchUseCase: CarelevoConnectNewPatchUseCase
    @Mock lateinit var patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase

    private lateinit var sut: CarelevoPatchConnectViewModel

    /** Thread-safe: events emitted from the IO-launched paths land on an IO thread, asserts read here. */
    private val events = CopyOnWriteArrayList<Event>()
    private lateinit var collectorScope: CoroutineScope

    private val device = ScannedDevice(name = "CareLevo-1234", address = "94:b2:16:1d:2f:6d", rssi = -40)

    private val pairingResult = CarelevoBleSession.PairingResult(
        address = "94:b2:16:1d:2f:6d",
        serialNumber = "CARELEVO-TEST-001",
        firmwareVersion = "T168",
        modelName = "6776514848"
    )

    private val userSettings = CarelevoUserSettingInfoDomainModel(
        lowInsulinNoticeAmount = 30,
        maxBasalSpeed = 15.0,
        maxBolusDose = 25.0
    )

    @BeforeEach
    fun setUp() {
        // viewModelScope resolves Dispatchers.Main.immediate at first use; Unconfined makes every
        // Main-launched body (triggerEvent, the discard queue branch) run in-line.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // startPatchForceDiscard's `.subscribe { }` has no onError arm, so an errored stream reaches
        // the global Rx handler as OnErrorNotImplementedException *after* doOnError already ran.
        // Swallow it so that (intended) branch does not trip the default uncaught-exception handler.
        RxJavaPlugins.setErrorHandler { }

        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(transport.scanner).thenReturn(scanner)
        whenever(transport.adapter).thenReturn(adapter)

        sut = CarelevoPatchConnectViewModel(
            aapsLogger = aapsLogger,
            aapsSchedulers = aapsSchedulers,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            sp = sp,
            bleSession = bleSession,
            transport = transport,
            connectNewPatchUseCase = connectNewPatchUseCase,
            patchForceDiscardUseCase = patchForceDiscardUseCase
        )

        // Subscribe before any action: the EventFlow replays 1 but each slot is consumed exactly once.
        collectorScope = CoroutineScope(Dispatchers.Unconfined)
        collectorScope.launch { sut.event.collect { events.add(it) } }
    }

    @AfterEach
    fun tearDown() {
        collectorScope.cancel()
        RxJavaPlugins.reset()
        Dispatchers.resetMain()
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Block until an IO-launched path emits its terminal event (see class KDoc: real IO pool). */
    private fun awaitEvent(timeoutMs: Long = 3_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && events.isEmpty()) Thread.sleep(5)
        assertThat(events).isNotEmpty()
    }

    private fun patchStateSubject(state: PatchState?): BehaviorSubject<Optional<PatchState>> =
        if (state == null) BehaviorSubject.create() else BehaviorSubject.createDefault(Optional.of(state))

    private fun userSettingSubject(model: CarelevoUserSettingInfoDomainModel?): BehaviorSubject<Optional<CarelevoUserSettingInfoDomainModel>> =
        if (model == null) BehaviorSubject.create() else BehaviorSubject.createDefault(Optional.of(model))

    /** `_selectedDevice` is private and only ever written by a successful scan; seed it directly. */
    private fun setSelectedDevice(value: ScannedDevice?) {
        val field = CarelevoPatchConnectViewModel::class.java.getDeclaredField("_selectedDevice")
        field.isAccessible = true
        field.set(sut, value)
    }

    /** Simulate "a scan is already in flight" without leaving a real 10 s scan coroutine running. */
    private fun setScanWorking(value: Boolean) {
        val field = CarelevoPatchConnectViewModel::class.java.getDeclaredField("_isScanWorking")
        field.isAccessible = true
        field.setBoolean(sut, value)
    }

    /** The VM's private Rx bag — used to prove `onCleared` actually releases in-flight work. */
    private fun compositeDisposable(): CompositeDisposable {
        val field = CarelevoPatchConnectViewModel::class.java.getDeclaredField("compositeDisposable")
        field.isAccessible = true
        return field.get(sut) as CompositeDisposable
    }

    /** `onCleared` is `protected` (inherited from [androidx.lifecycle.ViewModel]); the framework calls it. */
    private fun invokeOnCleared() {
        val method = CarelevoPatchConnectViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(sut)
    }

    /** Queue answer for `commandQueue.customCommand`; built before the stub that returns it. */
    private fun stubQueueResult(success: Boolean) {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        whenever { commandQueue.customCommand(any()) }.thenReturn(result)
    }

    private fun stubForceDiscardSuccess() {
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))
    }

    // ---- initial state ------------------------------------------------------------------------

    @Test
    fun `uiState starts Idle`() {
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `isScanWorking starts false`() {
        assertThat(sut.isScanWorking).isFalse()
    }

    // ---- startScan guards ---------------------------------------------------------------------

    @Test
    fun `startScan reports bluetooth off and never touches the scanner`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        sut.startScan()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
        verify(scanner, never()).startScan()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(sut.isScanWorking).isFalse()
    }

    @Test
    fun `startScan reports a scan already in progress and does not start a second one`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setScanWorking(true)

        sut.startScan()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanIsWorking)
        verify(scanner, never()).startScan()
        // Guard returns before setUiState(Loading).
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- startScan discovery ------------------------------------------------------------------

    @Test
    fun `startScan picks an advertised patch above the RSSI floor and offers the connect dialog`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Discovery collects for the whole DISCOVERY_COLLECTION_MS window (no early stop) then selects the
        // strongest-RSSI device seen. replay=1 buffers the advertisement so the collector cannot miss it.
        whenever(scanner.scannedDevices).thenReturn(MutableSharedFlow<ScannedDevice>(replay = 1).apply { tryEmit(device) })

        sut.startScan()
        // The window runs its full duration on the real IO pool, so wait past DISCOVERY_COLLECTION_MS.
        awaitEvent(timeoutMs = 15_000L)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowConnectDialog)
        // scanAddress = null puts the scanner in service-UUID discovery mode (no MAC filter).
        verify(transport).scanAddress = null
        verify(scanner).startScan()
        // The finally arm stops the scanner and releases the latch once the window closes.
        verify(scanner).stopScan()
        assertThat(sut.isScanWorking).isFalse()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startScan reports failure when the only advertisement is below the RSSI floor`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Too weak to be a nearby patch (below MIN_SCAN_RSSI = -45) → filtered out, so the window expires
        // with no selectable device and the scan reports failure.
        val weak = ScannedDevice(name = "CareLevo-far", address = "94:b2:16:1d:2f:99", rssi = -80)
        whenever(scanner.scannedDevices).thenReturn(MutableSharedFlow<ScannedDevice>(replay = 1).apply { tryEmit(weak) })

        sut.startScan()
        awaitEvent(timeoutMs = 15_000L)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        assertThat(events).doesNotContain(CarelevoConnectPrepareEvent.ShowConnectDialog)
        verify(scanner).stopScan()
        assertThat(sut.isScanWorking).isFalse()
    }

    @Test
    fun `startScan reports failure when the scan window expires with no advertisement`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Never emits → withTimeoutOrNull returns null only after the real 10 s SCAN_TIMEOUT_MS.
        // This is the one deliberately slow test: the VM hardcodes Dispatchers.IO, so the window
        // cannot be advanced with virtual time.
        whenever(scanner.scannedDevices).thenReturn(MutableSharedFlow<ScannedDevice>())

        sut.startScan()
        awaitEvent(timeoutMs = 15_000L)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        verify(scanner).startScan()
        // The finally arm still stops the scanner and releases the latch on the timeout path.
        verify(scanner).stopScan()
        assertThat(sut.isScanWorking).isFalse()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- startConnect guards ------------------------------------------------------------------

    @Test
    fun `startConnect reports bluetooth off before pairing`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
        setSelectedDevice(device)

        sut.startConnect(300)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
        verifyBlocking(bleSession, never()) { runPairing(any(), any()) }
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startConnect reports an empty selection when no patch was scanned`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(null)

        sut.startConnect(300)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty)
        verifyBlocking(bleSession, never()) { runPairing(any(), any()) }
    }

    @Test
    fun `startConnect reports missing user settings before pairing`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject(null))

        sut.startConnect(300)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo)
        verifyBlocking(bleSession, never()) { runPairing(any(), any()) }
        verify(adapter, never()).removeBond(any())
    }

    // ---- startConnect pairing -----------------------------------------------------------------

    @Test
    fun `startConnect clears the stale bond pairs the patch and persists it`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject(userSettings))
        whenever { bleSession.runPairing(any(), any()) }.thenReturn(pairingResult)
        whenever(connectNewPatchUseCase.persistNewPatch(any(), any(), any(), any(), any())).thenReturn(true)

        sut.startConnect(300)
        awaitEvent()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ConnectComplete)
        // Stale bond must be cleared before the pairing session dials.
        verify(adapter).removeBond(device.address)
        verifyBlocking(bleSession) { runPairing(any(), any()) }
        verify(connectNewPatchUseCase).persistNewPatch(any(), any(), any(), any(), any())
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startConnect builds the pairing spec from the wizard input the prefs and the user settings`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject(userSettings))
        whenever(sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 116)).thenReturn(72)
        whenever(sp.getBoolean(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER.key, false)).thenReturn(true)
        whenever { bleSession.runPairing(any(), any()) }.thenReturn(pairingResult)
        whenever(connectNewPatchUseCase.persistNewPatch(any(), any(), any(), any(), any())).thenReturn(true)

        sut.startConnect(300)
        awaitEvent()

        val addressCaptor = argumentCaptor<String>()
        val specCaptor = argumentCaptor<CarelevoBleSession.PairingSpec>()
        verifyBlocking(bleSession) { runPairing(addressCaptor.capture(), specCaptor.capture()) }

        // The session dials the MAC the scan reported ...
        assertThat(addressCaptor.firstValue).isEqualTo(device.address)
        // ... and the spec carries the wizard's fill volume plus the safety limits the patch itself
        // enforces (maxBasalSpeed/maxBolusDose) — these are written to the patch once, at activation,
        // so a wrong value here is not correctable later without a re-pair.
        assertThat(specCaptor.firstValue).isEqualTo(
            CarelevoBleSession.PairingSpec(
                volume = 300,
                remains = 30,
                expiry = 72,
                maxBasalSpeed = 15.0,
                maxBolusDose = 25.0,
                buzzUse = true
            )
        )
    }

    @Test
    fun `startConnect persists the MAC the pairing session read from the patch not the scanned address`() {
        // The 0x3B MAC read is authoritative: the advertised address and the patch's own MAC need not
        // match, and every later session dials the persisted one.
        val sessionReported = pairingResult.copy(address = "aa:bb:cc:dd:ee:ff")
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject(userSettings))
        whenever(sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 116)).thenReturn(72)
        whenever { bleSession.runPairing(any(), any()) }.thenReturn(sessionReported)
        whenever(connectNewPatchUseCase.persistNewPatch(any(), any(), any(), any(), any())).thenReturn(true)

        sut.startConnect(250)
        awaitEvent()

        val addressCaptor = argumentCaptor<String>()
        val requestCaptor = argumentCaptor<CarelevoConnectNewPatchRequestModel>()
        verify(connectNewPatchUseCase).persistNewPatch(
            addressCaptor.capture(),
            eq(sessionReported.serialNumber),
            eq(sessionReported.firmwareVersion),
            eq(sessionReported.modelName),
            requestCaptor.capture()
        )
        assertThat(addressCaptor.firstValue).isEqualTo("aa:bb:cc:dd:ee:ff")
        assertThat(addressCaptor.firstValue).isNotEqualTo(device.address)
        // The same request that drove the pairing writes is the one persisted.
        assertThat(requestCaptor.firstValue.volume).isEqualTo(250)
        assertThat(requestCaptor.firstValue.expiry).isEqualTo(72)
        assertThat(requestCaptor.firstValue.maxBasalSpeed).isEqualTo(15.0)
        assertThat(requestCaptor.firstValue.maxVolume).isEqualTo(25.0)
    }

    @Test
    fun `startConnect lets a cancellation cancel the coroutine instead of reporting a pairing failure`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject(userSettings))
        val pairingCalled = CountDownLatch(1)
        whenever { bleSession.runPairing(any(), any()) }.thenAnswer {
            pairingCalled.countDown()
            throw CancellationException("wizard step left")
        }

        sut.startConnect(300)

        // runCatching swallows CancellationException too, so the onFailure arm must rethrow it: leaving
        // it to fall through to ConnectFailed would pop a spurious "pairing failed" dialog when the user
        // simply navigated away mid-pair.
        assertThat(pairingCalled.await(3, TimeUnit.SECONDS)).isTrue()
        Thread.sleep(200) // let the fold arm run; it must stay silent
        assertThat(events).isEmpty()
        verify(connectNewPatchUseCase, never()).persistNewPatch(any(), any(), any(), any(), any())
    }

    @Test
    fun `startConnect reports failure when persisting the new patch fails`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject(userSettings))
        whenever { bleSession.runPairing(any(), any()) }.thenReturn(pairingResult)
        // check(false) inside runCatching → IllegalStateException → onFailure arm.
        whenever(connectNewPatchUseCase.persistNewPatch(any(), any(), any(), any(), any())).thenReturn(false)

        sut.startConnect(300)
        awaitEvent()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ConnectFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startConnect reports failure when the pairing session throws and never persists`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject(userSettings))
        whenever { bleSession.runPairing(any(), any()) }.thenAnswer { throw IllegalStateException("connect refused") }

        sut.startConnect(300)
        awaitEvent()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ConnectFailed)
        verify(connectNewPatchUseCase, never()).persistNewPatch(any(), any(), any(), any(), any())
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- startPatchDiscardProcess: nothing to discard ------------------------------------------

    @Test
    fun `startPatchDiscardProcess completes immediately when no patch state is known`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(null))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        verify(patchForceDiscardUseCase, never()).execute()
    }

    @Test
    fun `startPatchDiscardProcess completes immediately when the patch is not booting`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.NotConnectedNotBooting))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    // ---- startPatchDiscardProcess: queued discard ----------------------------------------------

    @Test
    fun `startPatchDiscardProcess routes a booted patch through the command queue`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.ConnectedBooted))
        stubQueueResult(success = true)

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
        // Queue succeeded → no DB-only fallback.
        verify(patchForceDiscardUseCase, never()).execute()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess routes a booted but disconnected patch through the command queue`() {
        // NotConnectedBooted is still a real patch on the body — only the link is down, so the discard
        // must go through the queue (which reconnects first), not straight to the DB-only fallback.
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.NotConnectedBooted))
        stubQueueResult(success = true)

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
        verify(patchForceDiscardUseCase, never()).execute()
    }

    @Test
    fun `startPatchDiscardProcess falls back to force-discard when the queue cannot reach the patch`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        stubForceDiscardSuccess()

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verify(patchForceDiscardUseCase).execute()
        verify(carelevoPatch).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess reports failure when force-discard returns an error`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(ResponseResult.Error(RuntimeException("db down"))))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess reports failure when force-discard returns a failure result`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        // Neither Success nor Error → the `else` arm of the response when.
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.just(ResponseResult.Failure("rejected")))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess reports failure when the force-discard stream errors`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        // Errored stream → the doOnError arm (not the subscribe consumer).
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.error(RuntimeException("boom")))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- onCleared ----------------------------------------------------------------------------

    @Test
    fun `onCleared disposes force-discard work still in flight`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        // Never terminates → the subscription stays parked in the VM's CompositeDisposable, which is
        // exactly the state onCleared exists to clean up (the wizard step can be torn down mid-discard).
        whenever(patchForceDiscardUseCase.execute()).thenReturn(Single.never())

        sut.startPatchDiscardProcess()
        assertThat(compositeDisposable().size()).isEqualTo(1)

        invokeOnCleared()

        assertThat(compositeDisposable().size()).isEqualTo(0)
    }

    // ---- resetForEnterStep --------------------------------------------------------------------

    @Test
    fun `resetForEnterStep stops the scanner and clears the scan latch`() {
        setScanWorking(true)
        setSelectedDevice(device)

        sut.resetForEnterStep()

        verify(scanner).stopScan()
        assertThat(sut.isScanWorking).isFalse()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)

        // The selection was dropped: re-entering the step must re-scan before connect is allowed.
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        sut.startConnect(300)
        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty)
    }

    // ---- triggerEvent / generateEventType ------------------------------------------------------

    @Test
    fun `triggerEvent forwards an unmapped connect-prepare event as NoAction`() {
        // NoAction is the only subtype generateEventType does not list → the `else` arm.
        sut.triggerEvent(CarelevoConnectPrepareEvent.NoAction)

        assertThat(events).containsExactly(CarelevoConnectPrepareEvent.NoAction)
    }

    @Test
    fun `triggerEvent ignores events from another screen`() {
        sut.triggerEvent(CarelevoOverviewEvent.ShowPumpDiscardDialog)

        assertThat(events).isEmpty()
    }
}
