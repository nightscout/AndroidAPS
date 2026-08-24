package app.aaps.pump.carelevo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import app.aaps.core.interfaces.configuration.ExternalOptions
// Aliased: the simple name collides with Robolectric's @Config annotation used below.
import app.aaps.core.interfaces.configuration.Config as AapsConfig
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.BondingState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.NotificationState
import app.aaps.pump.carelevo.ble.data.PeripheralConnectionState
import app.aaps.pump.carelevo.ble.data.ServiceDiscoverState
import app.aaps.pump.carelevo.command.CarelevoActivationExecutor
import app.aaps.pump.carelevo.command.CmdUpdateBuzzer
import app.aaps.pump.carelevo.command.CmdUpdateExpiredThreshold
import app.aaps.pump.carelevo.command.CmdUpdateLowInsulinNotice
import app.aaps.pump.carelevo.command.CmdUpdateMaxBolus
import app.aaps.pump.carelevo.common.CarelevoAlarmNotifier
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.coordinator.CarelevoBasalProfileUpdateCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoBolusCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoConnectionCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoSettingsCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoTempBasalCoordinator
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoCancelTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import app.aaps.pump.carelevo.domain.usecase.basal.CarelevoStartTempBasalInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoCancelImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoFinishImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartExtendBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.bolus.CarelevoStartImmeBolusInfusionUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.joda.time.DateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Optional
import javax.inject.Provider

/**
 * Robolectric unit tests for the LIFECYCLE half of [CarelevoPumpPlugin] — everything `onStart()`
 * wires up and `onStop()` tears down, which the JVM/Mockito suites
 * ([CarelevoPumpPluginTest] and friends) cannot reach:
 *
 *  - `onStart`: the one-shot CAGE threshold defaults, `initPatchOnce` + best-effort profile apply,
 *    the reservoir/battery mirror, the Bluetooth adapter seed + broadcast receiver, and the alarm
 *    observer registration.
 *  - the four preference observers (max bolus / expiry threshold / low-insulin zero-skip / buzzer)
 *    and the deferred settings-sync recovery combiner.
 *  - `handleAlarms` — the critical-alarm escalation to `UiInteraction.runAlarm` when the Compose
 *    host is NOT mounted, plus the `globallyAlarmedIds` dedup/prune.
 *  - `onStop`: alarm-observer teardown, user-settings clear and preference-scope cancellation.
 *
 * Robolectric is required because `startAlarmObserving`/`onStop` touch [ProcessLifecycleOwner]
 * (a real `Looper` + `ArchTaskExecutor` main-thread check) and `PumpPluginBase.onStart` builds a
 * real `HandlerThread`. Same rationale and style as
 * [app.aaps.pump.carelevo.common.CarelevoAlarmNotifierTest].
 *
 * `Dispatchers.setMain(UnconfinedTestDispatcher())` backs the `withContext(Dispatchers.Main)` hops
 * in `startAlarmObserving`/`onStop`: without it the real Android main dispatcher would post to the
 * paused Robolectric looper while `runBlocking` holds that very thread, and deadlock.
 *
 * The Rx schedulers are trampolined so `initPatchOnce`, the patchInfo mirror and the settings-sync
 * combiner all deliver synchronously. The preference observers are the exception — the plugin
 * collects them on a hard-coded `Dispatchers.IO` scope, so those are verified with `timeout(...)`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class CarelevoPumpPluginLifecycleTest {

    // REAL application context: getSystemService(BLUETOOTH_SERVICE) and registerReceiver must work.
    private val context: Context = RuntimeEnvironment.getApplication()

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var rh: ResourceHelper
    private lateinit var preferences: Preferences
    private lateinit var commandQueue: CommandQueue
    private lateinit var aapsSchedulers: AapsSchedulers
    private lateinit var sp: SP
    private lateinit var spEditor: SP.Editor
    private lateinit var fabricPrivacy: FabricPrivacy
    private lateinit var profileFunction: ProfileFunction
    private lateinit var protectionCheck: ProtectionCheck
    private lateinit var blePreCheck: BlePreCheck
    private lateinit var iconsProvider: IconsProvider
    private lateinit var config: AapsConfig
    private lateinit var uiInteraction: UiInteraction
    private lateinit var carelevoPatch: CarelevoPatch
    private lateinit var carelevoAlarmNotifier: CarelevoAlarmNotifier
    private lateinit var bleSession: CarelevoBleSession
    private lateinit var activationExecutor: CarelevoActivationExecutor
    private lateinit var deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase

    private lateinit var plugin: CarelevoPumpPlugin

    // EffectiveProfile, not plain Profile: ProfileFunction.getProfile() returns EffectiveProfile? and
    // the same mock also backs carelevoPatch.profile (EffectiveProfile : Profile).
    private lateinit var testProfile: EffectiveProfile

    // Preference streams the plugin collects in registerPreferenceChangeObserver().
    private val maxBolusFlow = MutableStateFlow(3.0)
    private val expiryFlow = MutableStateFlow(116)
    private val lowInsulinFlow = MutableStateFlow(30)
    private val buzzerFlow = MutableStateFlow(false)

    // Patch streams the deferred settings-sync combiner reads.
    private lateinit var patchInfoSubject: BehaviorSubject<Optional<CarelevoPatchInfoDomainModel>>
    private lateinit var infusionInfoSubject: BehaviorSubject<Optional<CarelevoInfusionInfoDomainModel>>
    private lateinit var patchStateSubject: BehaviorSubject<Optional<PatchState>>
    private lateinit var userSettingSubject: BehaviorSubject<Optional<CarelevoUserSettingInfoDomainModel>>
    private lateinit var profileSubject: BehaviorSubject<Optional<Profile>>

    private var started = false

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // ProcessLifecycleOwner is a PROCESS singleton and survives between test methods, so pin it to
        // a known background state: addObserver replays events up to the current state, and a leftover
        // STARTED from another test would fire ON_START (-> refreshAlarms) the moment onStart registers.
        (ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        aapsLogger = mock()
        rh = mock()
        preferences = mock()
        commandQueue = mock()
        aapsSchedulers = mock()
        sp = mock()
        spEditor = mock()
        fabricPrivacy = mock()
        profileFunction = mock()
        protectionCheck = mock()
        blePreCheck = mock()
        iconsProvider = mock()
        config = mock()
        uiInteraction = mock()
        carelevoPatch = mock()
        carelevoAlarmNotifier = mock()
        bleSession = mock()
        activationExecutor = mock()
        deleteUserSettingInfoUseCase = mock()

        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.cpu).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.newThread).thenReturn(Schedulers.trampoline())
        whenever(rh.gs(any<Int>())).thenReturn("Mocked")

        testProfile = mock()
        whenever(testProfile.getBasal()).thenReturn(1.0)

        patchInfoSubject = BehaviorSubject.createDefault(Optional.of(samplePatchInfo()))
        infusionInfoSubject = BehaviorSubject.createDefault(Optional.of(CarelevoInfusionInfoDomainModel()))
        patchStateSubject = BehaviorSubject.createDefault(Optional.of(PatchState.ConnectedBooted))
        userSettingSubject = BehaviorSubject.createDefault(Optional.of(CarelevoUserSettingInfoDomainModel()))
        profileSubject = BehaviorSubject.createDefault(Optional.of<Profile>(testProfile))

        whenever(carelevoPatch.patchInfo).thenReturn(patchInfoSubject)
        whenever(carelevoPatch.infusionInfo).thenReturn(infusionInfoSubject)
        whenever(carelevoPatch.patchState).thenReturn(patchStateSubject)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingSubject)
        whenever(carelevoPatch.profile).thenReturn(profileSubject)
        whenever(carelevoPatch.initPatchOnce()).thenReturn(Completable.complete())

        // Deferred settings-sync + preference observers both enqueue through the queue.
        val enactResult: PumpEnactResult = mock()
        whenever { commandQueue.customCommand(any()) }.thenReturn(enactResult)

        // onStop -> settingsCoordinator.clearUserSettings
        whenever(deleteUserSettingInfoUseCase.execute()).thenReturn(Single.just(ResponseResult.Success(ResultSuccess)))

        whenever(preferences.observe(DoubleKey.SafetyMaxBolus)).thenReturn(maxBolusFlow)
        whenever(preferences.observe(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)).thenReturn(expiryFlow)
        whenever(preferences.observe(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS)).thenReturn(lowInsulinFlow)
        whenever(preferences.observe(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)).thenReturn(buzzerFlow)
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(7.5)

        // sp.edit { … } returns Unit -> stub with doAnswer and run the block against a mock Editor.
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.getArgument<Any>(1) as SP.Editor.() -> Unit).invoke(spEditor)
            Unit
        }.whenever(sp).edit(any(), any())

        plugin = buildPlugin()
        plugin.bleSession = bleSession
    }

    @After
    fun tearDown() {
        // Cancel the IO preference scope, PumpPluginBase's delayed initial-readStatus job and the
        // ProcessLifecycleOwner observer, so nothing leaks into the next test in this class.
        if (started) runBlocking { plugin.onStop() }
        Dispatchers.resetMain()
    }

    private fun buildPlugin(): CarelevoPumpPlugin {
        val pumpEnactResultProvider = Provider<PumpEnactResult> { mock() }
        val setBasalProgramUseCase: CarelevoSetBasalProgramUseCase = mock()
        val startTempBasalInfusionUseCase: CarelevoStartTempBasalInfusionUseCase = mock()
        val cancelTempBasalInfusionUseCase: CarelevoCancelTempBasalInfusionUseCase = mock()
        val startImmeBolusInfusionUseCase: CarelevoStartImmeBolusInfusionUseCase = mock()
        val startExtendBolusInfusionUseCase: CarelevoStartExtendBolusInfusionUseCase = mock()
        val cancelImmeBolusInfusionUseCase: CarelevoCancelImmeBolusInfusionUseCase = mock()
        val cancelExtendBolusInfusionUseCase: CarelevoCancelExtendBolusInfusionUseCase = mock()
        val finishImmeBolusInfusionUseCase: CarelevoFinishImmeBolusInfusionUseCase = mock()
        val dateUtil: DateUtil = mock()
        val pumpSync: PumpSync = mock()
        val bolusProgressData: BolusProgressData = mock()

        return CarelevoPumpPlugin(
            aapsLogger = aapsLogger,
            rh = rh,
            preferences = preferences,
            commandQueue = commandQueue,
            aapsSchedulers = aapsSchedulers,
            sp = sp,
            fabricPrivacy = fabricPrivacy,
            profileFunction = profileFunction,
            context = context,
            protectionCheck = protectionCheck,
            blePreCheck = blePreCheck,
            iconsProvider = iconsProvider,
            config = config,
            uiInteraction = uiInteraction,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            carelevoAlarmNotifier = carelevoAlarmNotifier,
            basalProfileUpdateCoordinator = CarelevoBasalProfileUpdateCoordinator(
                aapsLogger = aapsLogger,
                rh = rh,
                pumpEnactResultProvider = pumpEnactResultProvider,
                carelevoPatch = carelevoPatch,
                bleSession = bleSession,
                setBasalProgramUseCase = setBasalProgramUseCase
            ),
            bolusCoordinator = CarelevoBolusCoordinator(
                aapsLogger = aapsLogger,
                rh = rh,
                dateUtil = dateUtil,
                bolusProgressData = bolusProgressData,
                pumpSync = pumpSync,
                aapsSchedulers = aapsSchedulers,
                pumpEnactResultProvider = pumpEnactResultProvider,
                carelevoPatch = carelevoPatch,
                bleSession = bleSession,
                startImmeBolusInfusionUseCase = startImmeBolusInfusionUseCase,
                finishImmeBolusInfusionUseCase = finishImmeBolusInfusionUseCase,
                cancelImmeBolusInfusionUseCase = cancelImmeBolusInfusionUseCase,
                startExtendBolusInfusionUseCase = startExtendBolusInfusionUseCase,
                cancelExtendBolusInfusionUseCase = cancelExtendBolusInfusionUseCase
            ),
            tempBasalCoordinator = CarelevoTempBasalCoordinator(
                aapsLogger = aapsLogger,
                dateUtil = dateUtil,
                pumpSync = pumpSync,
                pumpEnactResultProvider = pumpEnactResultProvider,
                carelevoPatch = carelevoPatch,
                bleSession = bleSession,
                startTempBasalInfusionUseCase = startTempBasalInfusionUseCase,
                cancelTempBasalInfusionUseCase = cancelTempBasalInfusionUseCase
            ),
            connectionCoordinator = CarelevoConnectionCoordinator(
                aapsLogger = aapsLogger,
                carelevoPatch = carelevoPatch,
                bleSession = bleSession
            ),
            settingsCoordinator = CarelevoSettingsCoordinator(
                aapsLogger = aapsLogger,
                aapsSchedulers = aapsSchedulers,
                deleteUserSettingInfoUseCase = deleteUserSettingInfoUseCase
            ),
            activationExecutor = activationExecutor
        )
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun samplePatchInfo(insulinRemain: Double = 60.0): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = "AA:BB:CC:DD:EE:FF",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now(),
            manufactureNumber = "CARELEVO-TEST-001",
            insulinRemain = insulinRemain,
            bolusActionSeq = 1,
            mode = 1
        )

    private fun alarm(cause: AlarmCause, id: String = "alarm-1"): CarelevoAlarmInfo =
        CarelevoAlarmInfo(
            alarmId = id,
            alarmType = cause.alarmType,
            cause = cause,
            value = null,
            createdAt = "2026-07-16T10:00:00",
            updatedAt = "2026-07-16T10:00:00",
            isAcknowledged = false
        )

    private fun start() {
        runBlocking { plugin.onStart() }
        started = true
    }

    /**
     * Capture the `onAlarmsUpdated` callback `startAlarmObserving` hands to the notifier — that lambda
     * IS the plugin's private `handleAlarms`, and invoking it here runs it synchronously on the test
     * thread.
     */
    private fun alarmHandler(): (List<CarelevoAlarmInfo>) -> Unit {
        val captor = argumentCaptor<(List<CarelevoAlarmInfo>) -> Unit>()
        verify(carelevoAlarmNotifier).startObserving(captor.capture())
        return captor.firstValue
    }

    /**
     * Block until the plugin's collector for [flow] is attached AND has consumed the initial value
     * that `drop(1)` swallows; only after that does a `value = …` change reach the `onEach` body.
     *
     * The plugin collects on its own `CoroutineScope(Dispatchers.IO)`, so a change published too
     * early would be conflated into the collector's first emission and dropped. `subscriptionCount`
     * flips at slot allocation, an instant before the initial value is read — the short settle
     * covers that window. Landing inside it can only ever fail a test (the `timeout(…)` verify never
     * fires), never pass one falsely.
     */
    private fun awaitCollector(flow: MutableStateFlow<*>) {
        val deadline = System.currentTimeMillis() + 2_000
        while (flow.subscriptionCount.value == 0 && System.currentTimeMillis() < deadline) Thread.sleep(2)
        assertThat(flow.subscriptionCount.value).isGreaterThan(0)
        Thread.sleep(100)
    }

    /**
     * Block until the plugin's collector for [flow] is gone. `scope.cancel()` returns immediately but
     * the collector unwinds (and frees its slot) on its own IO thread, so only once the count is back
     * to zero can a later change provably not reach a live collector.
     */
    private fun awaitCollectorGone(flow: MutableStateFlow<*>) {
        val deadline = System.currentTimeMillis() + 2_000
        while (flow.subscriptionCount.value > 0 && System.currentTimeMillis() < deadline) Thread.sleep(2)
        assertThat(flow.subscriptionCount.value).isEqualTo(0)
    }

    /** Every [CustomCommand] of type [T] the plugin has enqueued so far. */
    private inline fun <reified T : Any> capturedCommands(): List<T> {
        val captor = argumentCaptor<CustomCommand>()
        verifyBlocking(commandQueue, atLeastOnce()) { customCommand(captor.capture()) }
        return captor.allValues.filterIsInstance<T>()
    }

    // ---- onStart: CAGE defaults ----------------------------------------------------------------

    @Test
    fun `onStart applies the Carelevo CAGE warning and critical defaults once`() {
        whenever(sp.getBoolean(eq(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED.key), any())).thenReturn(false)

        start()

        verify(spEditor).putInt(IntKey.OverviewCageWarning.key, 96)
        verify(spEditor).putInt(IntKey.OverviewCageCritical.key, 168)
        // The latch itself must be written, or the defaults would stomp the user's edits every start.
        verify(spEditor).putBoolean(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED.key, true)
    }

    @Test
    fun `onStart does not re-apply the CAGE defaults once the latch is set`() {
        whenever(sp.getBoolean(eq(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED.key), any())).thenReturn(true)

        start()

        verify(sp, never()).edit(any(), any())
    }

    // ---- onStart: patch init + profile ---------------------------------------------------------

    @Test
    fun `onStart initializes the patch and applies the current profile`() {
        whenever { profileFunction.getProfile() }.thenReturn(testProfile)

        start()

        verify(carelevoPatch).initPatchOnce()
        verify(carelevoPatch).setProfile(testProfile)
    }

    @Test
    fun `onStart defers the profile to setNewBasalProfile when none is available yet`() {
        // profileFunction.getProfile() answers null on the un-stubbed mock: the profile store is not up
        // yet. onStart must still complete — the profile arrives later via setNewBasalProfile.
        start()

        verify(carelevoPatch).initPatchOnce()
        verify(carelevoPatch, never()).setProfile(any())
    }

    @Test
    fun `onStart survives a failing patch init and still registers the alarm observer`() {
        // initPatchOnce is onErrorComplete()d + timed out: a broken init must not abort onStart or the
        // alarm pipeline would never come up.
        whenever(carelevoPatch.initPatchOnce()).thenReturn(Completable.error(IllegalStateException("boom")))

        start()

        verify(carelevoAlarmNotifier).startObserving(any())
    }

    @Test
    fun `onStart mirrors the patch reservoir and battery level`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo(insulinRemain = 42.5)))

        start()

        assertThat(plugin.reservoirLevel.value.cU).isWithin(0.001).of(42.5)
        assertThat(plugin.batteryLevel.value).isEqualTo(0)
    }

    @Test
    fun `onStart reports a zero reservoir when no patch record exists`() {
        patchInfoSubject.onNext(Optional.empty())

        start()

        assertThat(plugin.reservoirLevel.value.cU).isWithin(0.001).of(0.0)
    }

    @Test
    fun `reservoir level keeps tracking the patch after onStart`() {
        start()

        patchInfoSubject.onNext(Optional.of(samplePatchInfo(insulinRemain = 12.25)))

        assertThat(plugin.reservoirLevel.value.cU).isWithin(0.001).of(12.25)
    }

    // ---- onStart: Bluetooth adapter seed + receiver ---------------------------------------------

    /**
     * Pin the adapter explicitly rather than leaning on a Robolectric default — the default is OFF,
     * and an implicit one silently decides which branch of `currentAdapterBleState()` a test covers.
     */
    private fun setAdapterEnabled(enabled: Boolean) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        Shadows.shadowOf(adapter).setEnabled(enabled)
    }

    @Test
    fun `onStart seeds the adapter state so patchState resolves before the first broadcast`() {
        setAdapterEnabled(true)

        start()

        val captor = argumentCaptor<BleState>()
        verify(carelevoPatch, atLeastOnce()).onBluetoothStateChanged(captor.capture())
        val seeded = captor.firstValue
        assertThat(seeded.isEnabled).isEqualTo(DeviceModuleState.DEVICE_STATE_ON)
        // Adapter-level ONLY: bond/discovery/connection/notification are per-session and never tracked.
        assertThat(seeded.isBonded).isEqualTo(BondingState.BOND_NONE)
        assertThat(seeded.isServiceDiscovered).isEqualTo(ServiceDiscoverState.DISCOVER_STATE_NONE)
        assertThat(seeded.isConnected).isEqualTo(PeripheralConnectionState.CONN_STATE_NONE)
        assertThat(seeded.isNotificationEnabled).isEqualTo(NotificationState.NOTIFICATION_NONE)
    }

    @Test
    fun `onStart seeds OFF when the adapter is disabled`() {
        setAdapterEnabled(false)

        start()

        val captor = argumentCaptor<BleState>()
        verify(carelevoPatch, atLeastOnce()).onBluetoothStateChanged(captor.capture())
        assertThat(captor.firstValue.isEnabled).isEqualTo(DeviceModuleState.DEVICE_STATE_OFF)
    }

    @Test
    fun `while emulating the adapter is reported ON even though it is disabled`() {
        // The emulated patch has no radio behind it, so every isBluetoothEnabled() gate in the
        // coordinators and view models would otherwise refuse before reaching the emulated transport.
        whenever(config.isEnabled(ExternalOptions.EMULATE_CARELEVO)).thenReturn(true)
        setAdapterEnabled(false)

        start()

        val captor = argumentCaptor<BleState>()
        verify(carelevoPatch, atLeastOnce()).onBluetoothStateChanged(captor.capture())
        assertThat(captor.firstValue.isEnabled).isEqualTo(DeviceModuleState.DEVICE_STATE_ON)
    }

    @Test
    fun `while emulating a host adapter OFF broadcast cannot kill the session`() {
        whenever(config.isEnabled(ExternalOptions.EMULATE_CARELEVO)).thenReturn(true)
        setAdapterEnabled(false)
        start()
        clearInvocations(carelevoPatch)

        sendAdapterState(BluetoothAdapter.STATE_OFF)

        // The receiver is never registered while emulating, so the broadcast reaches nothing.
        verify(carelevoPatch, never()).onBluetoothStateChanged(any())
    }

    @Test
    fun `an adapter OFF broadcast is forwarded to the patch`() {
        start()

        sendAdapterState(BluetoothAdapter.STATE_OFF)

        val captor = argumentCaptor<BleState>()
        verify(carelevoPatch, atLeastOnce()).onBluetoothStateChanged(captor.capture())
        assertThat(captor.lastValue.isEnabled).isEqualTo(DeviceModuleState.DEVICE_STATE_OFF)
    }

    @Test
    fun `an adapter TURNING_OFF broadcast is forwarded to the patch`() {
        start()

        sendAdapterState(BluetoothAdapter.STATE_TURNING_OFF)

        val captor = argumentCaptor<BleState>()
        verify(carelevoPatch, atLeastOnce()).onBluetoothStateChanged(captor.capture())
        assertThat(captor.lastValue.isEnabled).isEqualTo(DeviceModuleState.DEVICE_STATE_TUNING_OFF)
    }

    @Test
    fun `an out-of-range adapter state is ignored instead of crashing the receiver`() {
        // codeToDeviceResult() throws on anything outside {-1, 10..13}, so the receiver MUST filter
        // first — an unfiltered EXTRA_STATE would blow up inside a system broadcast.
        start()
        // Exactly one call so far: the startup seed.
        verify(carelevoPatch, times(1)).onBluetoothStateChanged(any())

        sendAdapterState(BluetoothAdapter.ERROR)

        verify(carelevoPatch, times(1)).onBluetoothStateChanged(any())
    }

    private fun sendAdapterState(state: Int) {
        context.sendBroadcast(
            Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE, state)
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    // ---- preference observers -------------------------------------------------------------------

    @Test
    fun `a max bolus change is pushed to the patch through the command queue`() {
        // Queued, not fire-and-forget: the pump idle-disconnects between commands, so only the queue's
        // connect-before-execute gets the write onto the patch.
        start()
        awaitCollector(maxBolusFlow)

        maxBolusFlow.value = 9.0

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        // The value comes from preferences.get(), not from the emitted value.
        assertThat(capturedCommands<CmdUpdateMaxBolus>().first().maxBolusDose).isWithin(0.001).of(7.5)
    }

    @Test
    fun `an expiry threshold change is pushed to the patch with the stored hours`() {
        whenever(sp.getInt(eq(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key), any())).thenReturn(120)
        start()
        awaitCollector(expiryFlow)

        expiryFlow.value = 120

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateExpiredThreshold>()) }
        assertThat(capturedCommands<CmdUpdateExpiredThreshold>().first().hours).isEqualTo(120)
    }

    @Test
    fun `a buzzer change is pushed to the patch with the stored flag`() {
        whenever(sp.getBoolean(eq(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER.key), any())).thenReturn(true)
        start()
        awaitCollector(buzzerFlow)

        buzzerFlow.value = true

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateBuzzer>()) }
        assertThat(capturedCommands<CmdUpdateBuzzer>().first().on).isTrue()
    }

    @Test
    fun `a zero low-insulin reminder is not enqueued but a real one is`() {
        // Zero = reminder off. Enqueuing it would wake the patch over BLE just to no-op.
        val key = CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key
        whenever(sp.getInt(eq(key), any())).thenReturn(0)
        start()
        awaitCollector(lowInsulinFlow)

        lowInsulinFlow.value = 0
        // Barrier: once the collector has read sp for the 0-value emission, its skip decision is made.
        // Only then re-stub + emit again, so the two emissions cannot be conflated into one.
        verify(sp, timeout(2_000)).getInt(eq(key), eq(0))

        whenever(sp.getInt(eq(key), any())).thenReturn(25)
        lowInsulinFlow.value = 25

        // Same collector, ordered: observing the second emission's command proves the first is done.
        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateLowInsulinNotice>()) }
        val pushed = capturedCommands<CmdUpdateLowInsulinNotice>()
        assertThat(pushed).hasSize(1)
        assertThat(pushed.first().hours).isEqualTo(25)
    }

    // ---- deferred settings-sync recovery --------------------------------------------------------

    @Test
    fun `a pending max-bolus sync is pushed once the patch is booted and idle`() {
        start()

        userSettingSubject.onNext(
            Optional.of(CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true))
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>().first().maxBolusDose).isWithin(0.001).of(12.0)
    }

    @Test
    fun `a pending max-bolus sync with no stored dose falls back to zero`() {
        start()

        userSettingSubject.onNext(
            Optional.of(CarelevoUserSettingInfoDomainModel(maxBolusDose = null, needMaxBolusDoseSyncPatch = true))
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>().first().maxBolusDose).isWithin(0.001).of(0.0)
    }

    @Test
    fun `a pending low-insulin sync is pushed once the patch is booted`() {
        start()

        userSettingSubject.onNext(
            Optional.of(CarelevoUserSettingInfoDomainModel(lowInsulinNoticeAmount = 22, needLowInsulinNoticeAmountSyncPatch = true))
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateLowInsulinNotice>()) }
        assertThat(capturedCommands<CmdUpdateLowInsulinNotice>().first().hours).isEqualTo(22)
    }

    @Test
    fun `nothing is pushed while the patch is not booted and it flushes once it boots`() {
        start()
        patchStateSubject.onNext(Optional.of(PatchState.NotConnectedBooted))

        val pending = CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true)
        userSettingSubject.onNext(Optional.of(pending))
        // Not booted -> the combiner yields the empty need, so no CmdUpdateMaxBolus can ever be produced.
        patchStateSubject.onNext(Optional.of(PatchState.ConnectedBooted))

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        // Exactly one: the not-booted pass contributed nothing.
        assertThat(capturedCommands<CmdUpdateMaxBolus>()).hasSize(1)
    }

    @Test
    fun `a pending max-bolus sync is held back while an immediate bolus is running`() {
        // Max bolus is a device safety cap — re-pushing it mid-bolus would force a spurious reconnect.
        start()
        infusionInfoSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoDomainModel(
                        infusionId = "imme-1",
                        address = "AA:BB:CC:DD:EE:FF",
                        mode = 3,
                        volume = 1.0,
                        infusionDurationSeconds = 30
                    )
                )
            )
        )

        userSettingSubject.onNext(
            Optional.of(CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true))
        )
        // Bolus finished -> the same pending flag must now flush.
        infusionInfoSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel()))

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>()).hasSize(1)
    }

    @Test
    fun `a pending max-bolus sync is held back while an extended bolus is running`() {
        // BOTH channels must be idle: an `||` here (the old bug) read as idle during a single-channel
        // bolus and triggered a mid-bolus reconnect.
        start()
        infusionInfoSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    extendBolusInfusionInfo = CarelevoExtendBolusInfusionInfoDomainModel(
                        infusionId = "ext-1",
                        address = "AA:BB:CC:DD:EE:FF",
                        mode = 4,
                        volume = 1.0,
                        infusionDurationMin = 30
                    )
                )
            )
        )

        userSettingSubject.onNext(
            Optional.of(CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true))
        )
        infusionInfoSubject.onNext(Optional.of(CarelevoInfusionInfoDomainModel()))

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>()).hasSize(1)
    }

    @Test
    fun `a low-insulin sync is pushed even mid-bolus - only max bolus is gated`() {
        start()
        infusionInfoSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoDomainModel(
                        infusionId = "imme-1",
                        address = "AA:BB:CC:DD:EE:FF",
                        mode = 3,
                        volume = 1.0,
                        infusionDurationSeconds = 30
                    )
                )
            )
        )

        userSettingSubject.onNext(
            Optional.of(CarelevoUserSettingInfoDomainModel(lowInsulinNoticeAmount = 22, needLowInsulinNoticeAmountSyncPatch = true))
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateLowInsulinNotice>()) }
    }

    // ---- handleAlarms ---------------------------------------------------------------------------

    @Test
    fun `handleAlarms ignores an empty alarm set`() {
        start()

        alarmHandler().invoke(emptyList())

        verify(uiInteraction, never()).runAlarm(any(), any(), any())
        verify(carelevoAlarmNotifier).showTopNotification(eq(emptyList()))
    }

    @Test
    fun `handleAlarms shows a top notification for non-critical notices`() {
        start()

        alarmHandler().invoke(listOf(alarm(AlarmCause.ALARM_NOTICE_LGS_START)))

        verify(carelevoAlarmNotifier).showTopNotification(any())
        verify(uiInteraction, never()).runAlarm(any(), any(), any())
    }

    @Test
    fun `handleAlarms shows a top notification AND rings the full-screen alarm for a critical alarm`() {
        start()

        alarmHandler().invoke(listOf(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED)))

        verify(carelevoAlarmNotifier).showTopNotification(any())
        // Critical (WARNING/ALERT tier) alarms additionally escalate through the shared full-screen
        // alarm — see handleAlarms KDoc. Sound/full-screen wake-up alone does not clear the alarm;
        // the top-notification card's own action button does that (CarelevoAlarmNotifier).
        verify(uiInteraction).runAlarm(any(), any(), any())
    }

    @Test
    fun `handleAlarms rings the full-screen alarm for a critical ALERT tier too`() {
        start()

        alarmHandler().invoke(listOf(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)))

        verify(carelevoAlarmNotifier).showTopNotification(any())
        verify(uiInteraction).runAlarm(any(), any(), any())
    }

    @Test
    fun `handleAlarms includes both notice and critical alarms in the top notification path`() {
        start()

        alarmHandler().invoke(
            listOf(
                alarm(AlarmCause.ALARM_NOTICE_LGS_START, id = "notice"),
                alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "critical")
            )
        )

        verify(carelevoAlarmNotifier).showTopNotification(any())
        // Only the critical member rings the shared alarm — still exactly once per call.
        verify(uiInteraction).runAlarm(any(), any(), any())
    }

    @Test
    fun `handleAlarms re-posts the top notification but rings the full-screen alarm only once for a still-active id`() {
        start()
        val handle = alarmHandler()
        val alarms = listOf(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "a"))

        handle.invoke(alarms)
        handle.invoke(alarms)
        handle.invoke(alarms)

        // The card re-posts every time (that's how the user always sees current state)...
        verify(carelevoAlarmNotifier, times(3)).showTopNotification(eq(alarms))
        // ...but the same still-active id must not re-ring the shared alarm on every reconnect poll.
        verify(uiInteraction, times(1)).runAlarm(any(), any(), any())
    }

    @Test
    fun `handleAlarms rings the full-screen alarm again for each newly distinct critical id`() {
        start()
        val handle = alarmHandler()

        handle.invoke(listOf(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "a")))
        handle.invoke(
            listOf(
                alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "a"),
                alarm(AlarmCause.ALARM_WARNING_PATCH_ERROR, id = "b")
            )
        )

        verify(carelevoAlarmNotifier, times(2)).showTopNotification(any())
        // "a" only rings once (already seen); "b" is new on the second call → 2 rings total.
        verify(uiInteraction, times(2)).runAlarm(any(), any(), any())
    }

    @Test
    fun `handleAlarms rings the full-screen alarm again after an alarm id clears and recurs`() {
        start()
        val handle = alarmHandler()
        val alarms = listOf(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "a"))

        handle.invoke(alarms)
        handle.invoke(emptyList())
        handle.invoke(alarms)

        verify(carelevoAlarmNotifier, times(3)).showTopNotification(any())
        // Dropping out of the incoming set (resolved/acknowledged) clears the dedup id, so the same id
        // recurring later is treated as new again — rings twice (first appearance + recurrence).
        verify(uiInteraction, times(2)).runAlarm(any(), any(), any())
    }

    @Test
    fun `handleAlarms re-posts when the critical set shrinks but does not re-ring an already-seen id`() {
        start()
        val handle = alarmHandler()
        val a = alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED, id = "a")
        val b = alarm(AlarmCause.ALARM_WARNING_PATCH_ERROR, id = "b")

        handle.invoke(listOf(a, b))
        handle.invoke(listOf(a))

        verify(carelevoAlarmNotifier, times(2)).showTopNotification(any())
        // Both "a" and "b" are new on the first call, but only ONE ring per handleAlarms invocation
        // (the first fresh id) — matching EOPatch-style "announce one, the card lists the rest".
        // The second call has no fresh id ("a" already seen), so no additional ring.
        verify(uiInteraction, times(1)).runAlarm(any(), any(), any())
    }

    // ---- foreground refresh ---------------------------------------------------------------------

    @Test
    fun `a foreground transition refreshes the alarms`() {
        start()
        val registry = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

        // Force a real ON_START edge regardless of the state Robolectric left the process at.
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        verify(carelevoAlarmNotifier, atLeastOnce()).refreshAlarms()
    }

    // ---- onStop ---------------------------------------------------------------------------------

    @Test
    fun `onStop stops the alarm observer and clears the stored user settings`() {
        start()

        runBlocking { plugin.onStop() }
        started = false

        verify(carelevoAlarmNotifier).stopObserving()
        verify(deleteUserSettingInfoUseCase).execute()
    }

    @Test
    fun `onStop cancels the preference observers so later changes are not enqueued`() {
        start()
        awaitCollector(maxBolusFlow)

        runBlocking { plugin.onStop() }
        started = false
        awaitCollectorGone(maxBolusFlow)

        maxBolusFlow.value = 9.0

        verifyBlocking(commandQueue, never()) { customCommand(any<CmdUpdateMaxBolus>()) }
    }

    @Test
    fun `onStop detaches the foreground observer so it no longer refreshes alarms`() {
        start()
        runBlocking { plugin.onStop() }
        started = false
        val registry = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        verify(carelevoAlarmNotifier, never()).refreshAlarms()
    }

    @Test
    fun `onStop without a preceding onStart does not throw`() {
        // PluginBase launches onStart/onStop as separate unjoined coroutines, so a teardown with no
        // live generation (null scope / null observer) must be a clean no-op.
        runBlocking { plugin.onStop() }

        verify(carelevoAlarmNotifier).stopObserving()
    }

    @Test
    fun `onStop is idempotent`() {
        start()

        runBlocking { plugin.onStop() }
        runBlocking { plugin.onStop() }
        started = false

        verify(carelevoAlarmNotifier, times(2)).stopObserving()
    }
}
