package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleBonding
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleException
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.CarelevoGatt
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.latestValueFlow
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [CarelevoPatchConnectViewModel] — the activation wizard's scan/pair/discard step.
 *
 * **Plain JVM (JUnit5), not Robolectric.** The ViewModel touches no Android framework: it only drives
 * coroutines and flows over mockable collaborators. `viewModelScope` needs a Main dispatcher,
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
 * `startPatchForceDiscard` also launches in `viewModelScope` on Main, so it too completes inside the
 * call. Its 3 s timeout runs on the test dispatcher's virtual clock, which no test moves.
 *
 * One-shot events are captured through a collector subscribed in [setUp] — [CarelevoPatchConnectViewModel.event]
 * is a consume-once `EventFlow`, so exactly one collector must own it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchConnectViewModelTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var bleSession: CarelevoBleSession
    @Mock lateinit var central: BleCentral
    @Mock lateinit var bonding: BleBonding
    @Mock lateinit var connectNewPatchUseCase: CarelevoConnectNewPatchUseCase
    @Mock lateinit var patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase

    private lateinit var sut: CarelevoPatchConnectViewModel

    /** Thread-safe: events emitted from the IO-launched paths land on an IO thread, asserts read here. */
    private val events = CopyOnWriteArrayList<Event>()
    private lateinit var collectorScope: CoroutineScope

    private val device = advertisement("CareLevo-1234", "94:B2:16:1D:2F:6D", rssi = -40)

    /** The scan ran, and its collection ended: the scan stopped, as the old scanner's stopScan(). */
    @Volatile private var scanStarted = false
    @Volatile private var scanStopped = false

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

        whenever(central.bonding).thenReturn(bonding)

        sut = CarelevoPatchConnectViewModel(
            aapsLogger = aapsLogger,
            carelevoPatch = carelevoPatch,
            commandQueue = commandQueue,
            preferences = preferences,
            bleSession = bleSession,
            central = central,
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
        Dispatchers.resetMain()
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Block until an IO-launched path emits its terminal event (see class KDoc: real IO pool). */
    private fun awaitEvent(timeoutMs: Long = 3_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && events.isEmpty()) Thread.sleep(5)
        assertThat(events).isNotEmpty()
    }

    /** A stream with [state] as its last value, or with no value at all for null. */
    private fun patchStateFlow(state: PatchState?): MutableSharedFlow<PatchState?> =
        latestValueFlow<PatchState?>().apply { if (state != null) tryEmit(state) }

    /** A stream with [model] as its last value, or with no value at all for null. */
    private fun userSettingFlow(model: CarelevoUserSettingInfoDomainModel?): MutableSharedFlow<CarelevoUserSettingInfoDomainModel?> =
        latestValueFlow<CarelevoUserSettingInfoDomainModel?>().apply { if (model != null) tryEmit(model) }

    private fun advertisement(name: String?, address: String, rssi: Int) =
        Advertisement(DeviceId(address), name, rssi, listOf(CarelevoGatt.SERVICE), emptyMap(), emptyMap())

    /** A scan that reports [found] and then runs until its collection stops. */
    private fun scanFlow(vararg found: Advertisement): Flow<Advertisement> = flow {
        scanStarted = true
        try {
            found.forEach { emit(it) }
            awaitCancellation()
        } finally {
            scanStopped = true
        }
    }

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000L
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timeout waiting for: $what" }
            Thread.sleep(5)
        }
    }

    /** `_selectedDevice` is private and only ever written by a successful scan; seed it directly. */
    private fun setSelectedDevice(value: Advertisement?) {
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

    /** The VM's coroutines that still run — used to prove that clearing the VM actually stops in-flight work. */
    private fun activeWork(): Int = sut.viewModelScope.coroutineContext.job.children.count { it.isActive }

    /**
     * Clear the VM the way the framework does when its screen goes away: `viewModelScope` is cancelled,
     * then `onCleared` runs. Calling only `onCleared` would leave the `viewModelScope` work running.
     */
    private fun clearViewModel() {
        val store = ViewModelStore()
        ViewModelProvider(store, viewModelFactory { initializer { sut } })[CarelevoPatchConnectViewModel::class]
        store.clear()
    }

    /** Queue answer for `commandQueue.customCommand`; built before the stub that returns it. */
    private fun stubQueueResult(success: Boolean) {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        whenever { commandQueue.customCommand(any()) }.thenReturn(result)
    }

    private fun stubForceDiscardSuccess() {
        whenever { patchForceDiscardUseCase.execute() }.thenReturn(ResponseResult.Success(ResultSuccess))
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
        verify(central, never()).scan(any())
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
        assertThat(sut.isScanWorking).isFalse()
    }

    @Test
    fun `startScan reports a scan already in progress and does not start a second one`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setScanWorking(true)

        sut.startScan()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanIsWorking)
        verify(central, never()).scan(any())
        // Guard returns before setUiState(Loading).
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- startScan discovery ------------------------------------------------------------------

    @Test
    fun `startScan picks an advertised patch above the RSSI floor and offers the connect dialog`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Discovery collects for the whole DISCOVERY_COLLECTION_MS window (no early stop) then selects the
        // strongest-RSSI device seen.
        whenever(central.scan(any())).thenReturn(scanFlow(device))

        sut.startScan()
        // The window runs its full duration on the real IO pool, so wait past DISCOVERY_COLLECTION_MS.
        awaitEvent(timeoutMs = 15_000L)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowConnectDialog)
        // A discovery scan for the patch service (no address filter).
        verify(central).scan(ScanFilter(services = listOf(CarelevoGatt.SERVICE)))
        // The scan stops and the latch is released once the window closes.
        assertThat(scanStopped).isTrue()
        assertThat(sut.isScanWorking).isFalse()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startScan reports failure when the only advertisement is below the RSSI floor`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Too weak to be a nearby patch (below MIN_SCAN_RSSI = -45) → filtered out, so the window expires
        // with no selectable device and the scan reports failure.
        val weak = advertisement("CareLevo-far", "94:B2:16:1D:2F:99", rssi = -80)
        whenever(central.scan(any())).thenReturn(scanFlow(weak))

        sut.startScan()
        awaitEvent(timeoutMs = 15_000L)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        assertThat(events).doesNotContain(CarelevoConnectPrepareEvent.ShowConnectDialog)
        assertThat(scanStopped).isTrue()
        assertThat(sut.isScanWorking).isFalse()
    }

    @Test
    fun `startScan reports failure when the scan window expires with no advertisement`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        // Never emits → withTimeoutOrNull returns null only after the real 10 s SCAN_TIMEOUT_MS.
        // This is the one deliberately slow test: the VM hardcodes Dispatchers.IO, so the window
        // cannot be advanced with virtual time.
        whenever(central.scan(any())).thenReturn(scanFlow())

        sut.startScan()
        awaitEvent(timeoutMs = 15_000L)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        assertThat(scanStarted).isTrue()
        // The scan still stops and the latch is released on the timeout path.
        assertThat(scanStopped).isTrue()
        assertThat(sut.isScanWorking).isFalse()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startScan skips a patch that tells no name`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(central.scan(any())).thenReturn(scanFlow(advertisement(null, "94:B2:16:1D:2F:6D", rssi = -40)))

        sut.startScan()
        awaitEvent(timeoutMs = 15_000L)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
        assertThat(events).doesNotContain(CarelevoConnectPrepareEvent.ShowConnectDialog)
    }

    /** No permission, for example: the scan ends at once with the reason, and the wizard says it found nothing. */
    @Test
    fun `startScan reports failure at once when the scan cannot run`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(central.scan(any())).thenReturn(flow { throw BleException(BleFailure(BleFailure.Kind.NOT_AUTHORIZED)) })

        sut.startScan()
        awaitEvent()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
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
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow(null))

        sut.startConnect(300)

        assertThat(events).contains(CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo)
        verifyBlocking(bleSession, never()) { runPairing(any(), any()) }
        verifyBlocking(bonding, never()) { removeBond(any()) }
    }

    // ---- startConnect pairing -----------------------------------------------------------------

    @Test
    fun `startConnect clears the stale bond pairs the patch and persists it`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow(userSettings))
        whenever { bleSession.runPairing(any(), any()) }.thenReturn(pairingResult)
        whenever(connectNewPatchUseCase.persistNewPatch(any(), any(), any(), any(), any())).thenReturn(true)

        sut.startConnect(300)
        awaitEvent()

        assertThat(events).contains(CarelevoConnectPrepareEvent.ConnectComplete)
        // Stale bond must be cleared before the pairing session dials.
        verifyBlocking(bonding) { removeBond(device.device) }
        verifyBlocking(bleSession) { runPairing(any(), any()) }
        verify(connectNewPatchUseCase).persistNewPatch(any(), any(), any(), any(), any())
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startConnect builds the pairing spec from the wizard input the prefs and the user settings`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow(userSettings))
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)).thenReturn(72)
        whenever(preferences.get(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)).thenReturn(true)
        whenever { bleSession.runPairing(any(), any()) }.thenReturn(pairingResult)
        whenever(connectNewPatchUseCase.persistNewPatch(any(), any(), any(), any(), any())).thenReturn(true)

        sut.startConnect(300)
        awaitEvent()

        val addressCaptor = argumentCaptor<String>()
        val specCaptor = argumentCaptor<CarelevoBleSession.PairingSpec>()
        verifyBlocking(bleSession) { runPairing(addressCaptor.capture(), specCaptor.capture()) }

        // The session dials the device the scan found ...
        assertThat(addressCaptor.firstValue).isEqualTo(device.device.value)
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
    fun `startConnect persists the address the pairing session returns`() {
        // The session decides which address the patch is stored at (CarelevoBleSessionPairingTest); every
        // later session dials the persisted one.
        val sessionReported = pairingResult.copy(address = "aa:bb:cc:dd:ee:ff")
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        setSelectedDevice(device)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow(userSettings))
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)).thenReturn(72)
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
        assertThat(addressCaptor.firstValue).isNotEqualTo(device.device.value.lowercase())
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
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow(userSettings))
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
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow(userSettings))
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
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow(userSettings))
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
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(null))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
        verifyBlocking(patchForceDiscardUseCase, never()) { execute() }
    }

    @Test
    fun `startPatchDiscardProcess completes immediately when the patch is not booting`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.NotConnectedNotBooting))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    // ---- startPatchDiscardProcess: queued discard ----------------------------------------------

    @Test
    fun `startPatchDiscardProcess routes a booted patch through the command queue`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.ConnectedBooted))
        stubQueueResult(success = true)

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
        // Queue succeeded → no DB-only fallback.
        verifyBlocking(patchForceDiscardUseCase, never()) { execute() }
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess routes a booted but disconnected patch through the command queue`() {
        // NotConnectedBooted is still a real patch on the body — only the link is down, so the discard
        // must go through the queue (which reconnects first), not straight to the DB-only fallback.
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.NotConnectedBooted))
        stubQueueResult(success = true)

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(commandQueue) { customCommand(any<CmdDiscard>()) }
        verifyBlocking(patchForceDiscardUseCase, never()) { execute() }
    }

    @Test
    fun `startPatchDiscardProcess falls back to force-discard when the queue cannot reach the patch`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        stubForceDiscardSuccess()

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardComplete)
        verifyBlocking(patchForceDiscardUseCase) { execute() }
        verify(carelevoPatch).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess reports failure when force-discard returns an error`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        whenever { patchForceDiscardUseCase.execute() }.thenReturn(ResponseResult.Error(RuntimeException("db down")))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess reports failure when force-discard returns a failure result`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        // Neither Success nor Error → the `else` arm of the response when.
        whenever { patchForceDiscardUseCase.execute() }.thenReturn(ResponseResult.Failure("rejected"))

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardFailed)
        verify(carelevoPatch, never()).discardTeardown()
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    @Test
    fun `startPatchDiscardProcess reports failure when the force-discard stream errors`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        // A throwing use case → the catch arm (not the response `when`).
        whenever { patchForceDiscardUseCase.execute() }.thenAnswer { throw RuntimeException("boom") }

        sut.startPatchDiscardProcess()

        assertThat(events).contains(CarelevoConnectPrepareEvent.DiscardFailed)
        assertThat(sut.uiState.value).isEqualTo(UiState.Idle)
    }

    // ---- onCleared ----------------------------------------------------------------------------

    @Test
    fun `onCleared disposes force-discard work still in flight`() {
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow(PatchState.ConnectedBooted))
        stubQueueResult(success = false)
        // Never ends → the force-discard coroutine stays parked in the VM's viewModelScope, which is
        // exactly the state clearing the VM exists to clean up (the wizard step can be torn down mid-discard).
        whenever { patchForceDiscardUseCase.execute() }.doSuspendableAnswer { awaitCancellation() }

        sut.startPatchDiscardProcess()
        assertThat(activeWork()).isEqualTo(1)

        clearViewModel()

        assertThat(activeWork()).isEqualTo(0)
    }

    // ---- resetForEnterStep --------------------------------------------------------------------

    @Test
    fun `resetForEnterStep stops the scan and clears the scan latch`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(central.scan(any())).thenReturn(scanFlow())
        sut.startScan()
        awaitTrue("the scan started") { scanStarted }
        setSelectedDevice(device)

        sut.resetForEnterStep()

        awaitTrue("the scan stopped") { scanStopped }
        assertThat(sut.isScanWorking).isFalse()
        // The scan of the step left reports nothing.
        Thread.sleep(100)
        assertThat(events).doesNotContain(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
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
