package app.aaps.pump.carelevo

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotFlags
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotResponse
import app.aaps.pump.carelevo.ble.commands.ActiveAlarmSnapshotTier
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
import app.aaps.pump.carelevo.common.latestValueFlow
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.coordinator.CarelevoBasalProfileUpdateCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoBolusCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoConnectionCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoTempBasalCoordinator
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
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
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import app.aaps.core.interfaces.configuration.Config as AapsConfig

/**
 * Robolectric unit tests for the LIFECYCLE half of [CarelevoPumpPlugin] — everything `onStart()`
 * wires up and `onStop()` tears down, which the JVM/Mockito suites
 * ([CarelevoPumpPluginTest] and friends) cannot reach:
 *
 *  - `onStart`: the one-shot CAGE threshold defaults, `initPatchOnce` + best-effort profile apply,
 *    the reservoir/battery mirror, the Bluetooth adapter seed + follow of the central's adapter state,
 *    the foreground follow, and the alarm observer registration.
 *  - the four preference observers (max bolus / expiry threshold / low-insulin zero-skip / buzzer)
 *    and the deferred settings-sync recovery combiner.
 *  - `handleAlarms` — the critical-alarm escalation to `UiInteraction.runAlarm` when the Compose
 *    host is NOT mounted, plus the `globallyAlarmedIds` dedup/prune.
 *  - `onStop`: alarm-observer teardown and preference-scope cancellation.
 *
 * Robolectric is required because `PumpPluginBase.onStart` builds a real `HandlerThread`. The
 * Bluetooth adapter and the foreground state come from the test (`adapterState`, `appForeground`).
 *
 * `Dispatchers.setMain(UnconfinedTestDispatcher())` keeps any main-dispatcher work on the test thread:
 * the real Android main dispatcher would post to the paused Robolectric looper while `runBlocking`
 * holds that very thread, and deadlock.
 *
 * The plugin's scope runs on [Dispatchers.Unconfined] here (see `buildPlugin`), so `initPatchOnce`, the
 * patchInfo mirror, the settings-sync combiner and the preference observers all run on the test thread
 * before the call that feeds them returns. The preference observers are still verified with
 * `timeout(...)`: it passes at once now and still holds if the work runs on another thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class CarelevoPumpPluginLifecycleTest {

    private val decimalFormatter: DecimalFormatter = mock<DecimalFormatter>().also {
        whenever(it.to2Decimal(any<Double>())).thenAnswer { inv -> "%.2f".format(Locale.US, inv.getArgument<Double>(0)) }
    }

    private lateinit var aapsLogger: AAPSLogger
    private lateinit var rh: ResourceHelper
    private lateinit var preferences: Preferences
    private lateinit var commandQueue: CommandQueue
    private lateinit var fabricPrivacy: FabricPrivacy
    private lateinit var profileFunction: ProfileFunction
    private lateinit var protectionCheck: ProtectionCheck
    private lateinit var iconsProvider: IconsProvider
    private lateinit var config: AapsConfig
    private lateinit var uiInteraction: UiInteraction
    private lateinit var carelevoPatch: CarelevoPatch
    private lateinit var carelevoAlarmNotifier: CarelevoAlarmNotifier
    private lateinit var bleSession: CarelevoBleSession
    private lateinit var activationExecutor: CarelevoActivationExecutor

    private lateinit var plugin: CarelevoPumpPlugin

    // The phone's Bluetooth adapter as the driver's central reports it, and AAPS in front or not.
    private val adapterState = MutableStateFlow(AdapterState.ON)
    private val appForeground = FakeCarelevoAppForeground()

    // EffectiveProfile, not plain Profile: ProfileFunction.getProfile() returns EffectiveProfile? and
    // the same mock also backs carelevoPatch.profile (EffectiveProfile : Profile).
    private lateinit var testProfile: EffectiveProfile

    // Preference streams the plugin collects in registerPreferenceChangeObserver().
    private val maxBolusFlow = MutableStateFlow(3.0)
    private val expiryFlow = MutableStateFlow(116)
    private val lowInsulinFlow = MutableStateFlow(30)
    private val buzzerFlow = MutableStateFlow(false)

    // Patch streams the deferred settings-sync combiner reads.
    private lateinit var patchInfoFlow: MutableSharedFlow<CarelevoPatchInfoDomainModel?>
    private lateinit var infusionInfoFlow: MutableSharedFlow<CarelevoInfusionInfoDomainModel?>
    private lateinit var patchStateFlow: MutableSharedFlow<PatchState?>
    private lateinit var userSettingFlow: MutableSharedFlow<CarelevoUserSettingInfoDomainModel?>
    private lateinit var profileFlow: MutableSharedFlow<Profile?>

    private var started = false

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        aapsLogger = mock()
        rh = mock()
        preferences = mock()
        commandQueue = mock()
        fabricPrivacy = mock()
        profileFunction = mock()
        protectionCheck = mock()
        iconsProvider = mock()
        config = mock()
        uiInteraction = mock()
        carelevoPatch = mock()
        carelevoAlarmNotifier = mock()
        bleSession = mock()
        activationExecutor = mock()

        whenever(rh.gs(any<TextRef>())).thenReturn("Mocked")
        whenever(rh.gs(any<TextRef>(), anyVararg())).thenReturn("Mocked")
        // onStart follows the link for the alarm snapshot read; a test about the link stubs its own flow.
        whenever(bleSession.connected).thenReturn(MutableStateFlow(false))

        testProfile = mock()
        whenever(testProfile.getBasal()).thenReturn(1.0)

        patchInfoFlow = latestValueFlow<CarelevoPatchInfoDomainModel?>().apply { tryEmit(samplePatchInfo()) }
        infusionInfoFlow = latestValueFlow<CarelevoInfusionInfoDomainModel?>().apply { tryEmit(CarelevoInfusionInfoDomainModel()) }
        patchStateFlow = latestValueFlow<PatchState?>().apply { tryEmit(PatchState.ConnectedBooted) }
        // Stored settings that match the preferences stubbed below, so the settings sync has nothing to push.
        userSettingFlow = latestValueFlow<CarelevoUserSettingInfoDomainModel?>().apply { tryEmit(CarelevoUserSettingInfoDomainModel(maxBolusDose = 7.5)) }
        profileFlow = latestValueFlow<Profile?>().apply { tryEmit(testProfile) }

        whenever(carelevoPatch.patchInfo).thenReturn(patchInfoFlow)
        whenever(carelevoPatch.infusionInfo).thenReturn(infusionInfoFlow)
        whenever(carelevoPatch.patchState).thenReturn(patchStateFlow)
        whenever(carelevoPatch.userSettingInfo).thenReturn(userSettingFlow)
        whenever(carelevoPatch.profile).thenReturn(profileFlow)
        whenever(carelevoPatch.initPatchOnce()).thenReturn(CompletableDeferred(Unit))

        // Deferred settings-sync + preference observers both enqueue through the queue.
        val enactResult: PumpEnactResult = mock()
        whenever { commandQueue.customCommand(any()) }.thenReturn(enactResult)

        whenever(preferences.observe(DoubleKey.SafetyMaxBolus)).thenReturn(maxBolusFlow)
        whenever(preferences.observe(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)).thenReturn(expiryFlow)
        whenever(preferences.observe(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(lowInsulinFlow)
        whenever(preferences.observe(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)).thenReturn(buzzerFlow)
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(7.5)

        plugin = buildPlugin()
        plugin.bleSession = bleSession
    }

    @After
    fun tearDown() {
        // Cancel the plugin scope (preferences, adapter and foreground follows) and PumpPluginBase's
        // delayed initial-readStatus job, so nothing leaks into the next test in this class.
        if (started) runBlocking { plugin.onStop() }
        Dispatchers.resetMain()
    }

    private fun buildPlugin(
        basalProfileUpdateCoordinator: CarelevoBasalProfileUpdateCoordinator? = null,
        bolusCoordinator: CarelevoBolusCoordinator? = null,
        tempBasalCoordinator: CarelevoTempBasalCoordinator? = null,
        central: BleCentral = bleCentralWith(adapterState)
    ): CarelevoPumpPlugin {
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
            fabricPrivacy = fabricPrivacy,
            profileFunction = profileFunction,
            central = central,
            appForeground = appForeground,
            protectionCheck = protectionCheck,
            bleReadyCheck = FakeCarelevoBleReadyCheck(),
            iconsProvider = iconsProvider,
            config = config,
            uiInteraction = uiInteraction,
            pumpEnactResultProvider = pumpEnactResultProvider,
            carelevoPatch = carelevoPatch,
            carelevoAlarmNotifier = carelevoAlarmNotifier,
            basalProfileUpdateCoordinator = basalProfileUpdateCoordinator ?: CarelevoBasalProfileUpdateCoordinator(
                aapsLogger = aapsLogger,
                rh = rh,
                pumpEnactResultProvider = pumpEnactResultProvider,
                carelevoPatch = carelevoPatch,
                bleSession = bleSession,
                setBasalProgramUseCase = setBasalProgramUseCase
            ),
            bolusCoordinator = bolusCoordinator ?: CarelevoBolusCoordinator(
                aapsLogger = aapsLogger,
                rh = rh,
                dateUtil = dateUtil,
                bolusProgressData = bolusProgressData,
                pumpSync = pumpSync,
                pumpEnactResultProvider = pumpEnactResultProvider,
                carelevoPatch = carelevoPatch,
                bleSession = bleSession,
                startImmeBolusInfusionUseCase = startImmeBolusInfusionUseCase,
                finishImmeBolusInfusionUseCase = finishImmeBolusInfusionUseCase,
                cancelImmeBolusInfusionUseCase = cancelImmeBolusInfusionUseCase,
                startExtendBolusInfusionUseCase = startExtendBolusInfusionUseCase,
                cancelExtendBolusInfusionUseCase = cancelExtendBolusInfusionUseCase,
                decimalFormatter = decimalFormatter
            ),
            tempBasalCoordinator = tempBasalCoordinator ?: CarelevoTempBasalCoordinator(
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
            activationExecutor = activationExecutor,
            notificationManager = mock()
        ).also {
            // Set before onStart creates the scope: everything the plugin collects runs on the test thread.
            it.dispatcher = Dispatchers.Unconfined
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun samplePatchInfo(insulinRemain: Double = 60.0): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = "AA:BB:CC:DD:EE:FF",
            createdAt = Clock.System.now() - 1.hours,
            updatedAt = Clock.System.now(),
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
     * Here the plugin collects on an unconfined dispatcher, so the collector is attached before
     * `start()` returns and this wait ends at once. It stays as a stated precondition: if the
     * collector ran on another thread, a change published too early would be conflated into its
     * first emission and dropped. `subscriptionCount` flips at slot allocation, an instant before
     * the initial value is read — the short settle covers that window. Landing inside it can only
     * ever fail a test (the `timeout(…)` verify never fires), never pass one falsely.
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
        whenever(preferences.get(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED)).thenReturn(false)

        start()

        verify(preferences).put(IntKey.OverviewCageWarning, 96)
        verify(preferences).put(IntKey.OverviewCageCritical, 168)
        // The latch itself must be written, or the defaults would stomp the user's edits every start.
        verify(preferences).put(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED, true)
    }

    @Test
    fun `onStart does not re-apply the CAGE defaults once the latch is set`() {
        whenever(preferences.get(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED)).thenReturn(true)

        start()

        verify(preferences, never()).put(eq(IntKey.OverviewCageWarning), any<Int>())
        verify(preferences, never()).put(eq(IntKey.OverviewCageCritical), any<Int>())
        verify(preferences, never()).put(eq(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED), any<Boolean>())
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
        // A failed or timed-out initPatchOnce is caught: a broken init must not abort onStart or the
        // alarm pipeline would never come up.
        whenever(carelevoPatch.initPatchOnce()).thenReturn(CompletableDeferred<Unit>().apply { completeExceptionally(IllegalStateException("boom")) })

        start()

        verify(carelevoAlarmNotifier).startObserving(any())
    }

    @Test
    fun `onStart mirrors the patch reservoir and battery level`() {
        patchInfoFlow.tryEmit(samplePatchInfo(insulinRemain = 42.5))

        start()

        assertThat(plugin.reservoirLevel.value?.cU).isWithin(0.001).of(42.5)
        assertThat(plugin.batteryLevel.value).isEqualTo(0)
    }

    /** No patch, no reservoir to tell. A 0 went to Nightscout and to automation rules as an empty reservoir. */
    @Test
    fun `onStart reports no reservoir when no patch record exists`() {
        patchInfoFlow.tryEmit(null)

        start()

        assertThat(plugin.reservoirLevel.value).isNull()
    }

    @Test
    fun `reservoir level keeps tracking the patch after onStart`() {
        start()

        patchInfoFlow.tryEmit(samplePatchInfo(insulinRemain = 12.25))

        assertThat(plugin.reservoirLevel.value?.cU).isWithin(0.001).of(12.25)
    }

    // ---- onStart: Bluetooth adapter seed + follow of the central's adapter state ------------------

    /** The adapter-level states the plugin gave the patch, in order. */
    private fun forwardedAdapterStates(): List<DeviceModuleState> {
        val captor = argumentCaptor<BleState>()
        verify(carelevoPatch, atLeastOnce()).onBluetoothStateChanged(captor.capture())
        return captor.allValues.map { it.isEnabled }
    }

    @Test
    fun `onStart seeds the adapter state so patchState resolves before the first change`() {
        adapterState.value = AdapterState.ON

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
    fun `onStart seeds OFF when the adapter is off`() {
        adapterState.value = AdapterState.OFF

        start()

        assertThat(forwardedAdapterStates().first()).isEqualTo(DeviceModuleState.DEVICE_STATE_OFF)
    }

    @Test
    fun `the emulated central reports ON so the emulated patch is not stopped by the phone Bluetooth`() {
        // The emulated patch has no radio behind it, so every isBluetoothEnabled() gate in the
        // coordinators and view models would otherwise refuse before reaching the emulated transport.
        // The driver gets the emulated central while emulating, and that central is always on.
        plugin = buildPlugin(central = EmulatedBleCentral(aapsLogger))
        plugin.bleSession = bleSession

        start()

        // The seed and the first value of the follow: both ON, nothing else.
        assertThat(forwardedAdapterStates().toSet()).containsExactly(DeviceModuleState.DEVICE_STATE_ON)
    }

    @Test
    fun `an adapter OFF change is forwarded to the patch`() {
        start()

        adapterState.value = AdapterState.OFF

        assertThat(forwardedAdapterStates().last()).isEqualTo(DeviceModuleState.DEVICE_STATE_OFF)
    }

    @Test
    fun `an unknown adapter state is not reported as off`() {
        // iOS says UNKNOWN for a moment after the start. An OFF would raise the Bluetooth-off alarm.
        adapterState.value = AdapterState.UNKNOWN

        start()

        assertThat(forwardedAdapterStates()).doesNotContain(DeviceModuleState.DEVICE_STATE_OFF)
    }

    @Test
    fun `no Bluetooth permission and no Bluetooth at all count as off`() {
        start()

        adapterState.value = AdapterState.UNAUTHORIZED
        assertThat(forwardedAdapterStates().last()).isEqualTo(DeviceModuleState.DEVICE_STATE_OFF)

        adapterState.value = AdapterState.ON
        adapterState.value = AdapterState.UNSUPPORTED
        assertThat(forwardedAdapterStates().last()).isEqualTo(DeviceModuleState.DEVICE_STATE_OFF)
    }

    @Test
    fun `onStop ends the adapter follow`() {
        start()
        runBlocking { plugin.onStop() }
        started = false
        clearInvocations(carelevoPatch)

        adapterState.value = AdapterState.OFF

        verify(carelevoPatch, never()).onBluetoothStateChanged(any())
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
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)).thenReturn(120)
        start()
        awaitCollector(expiryFlow)

        expiryFlow.value = 120

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateExpiredThreshold>()) }
        assertThat(capturedCommands<CmdUpdateExpiredThreshold>().first().hours).isEqualTo(120)
    }

    @Test
    fun `a buzzer change is pushed to the patch with the stored flag`() {
        whenever(preferences.get(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)).thenReturn(true)
        start()
        awaitCollector(buzzerFlow)

        buzzerFlow.value = true

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateBuzzer>()) }
        assertThat(capturedCommands<CmdUpdateBuzzer>().first().on).isTrue()
    }

    @Test
    fun `a zero low-insulin reminder is not enqueued but a real one is`() {
        // Zero = reminder off. Enqueuing it would wake the patch over BLE just to no-op.
        val key = CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS
        whenever(preferences.get(key)).thenReturn(0)
        start()
        awaitCollector(lowInsulinFlow)
        // The start's settings sync reads the preference too; count only the observer's read below.
        clearInvocations(preferences)

        lowInsulinFlow.value = 0
        // Barrier: once the collector has read the preference for the 0-value emission, its skip
        // decision is made. Only then re-stub + emit again, so the two emissions cannot be conflated
        // into one.
        verify(preferences, timeout(2_000)).get(key)

        whenever(preferences.get(key)).thenReturn(25)
        lowInsulinFlow.value = 25

        // Same collector, ordered: observing the second emission's command proves the first is done.
        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateLowInsulinNotice>()) }
        val pushed = capturedCommands<CmdUpdateLowInsulinNotice>()
        assertThat(pushed).hasSize(1)
        assertThat(pushed.first().amountUnits).isEqualTo(25)
    }

    // ---- deferred settings-sync recovery --------------------------------------------------------

    @Test
    fun `a pending max-bolus sync is pushed once the patch is booted and idle`() {
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(12.0)
        start()

        userSettingFlow.tryEmit(
            CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true)
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>().first().maxBolusDose).isWithin(0.001).of(12.0)
    }

    @Test
    fun `a pending max-bolus sync with no stored dose sends the preference`() {
        // It sent 0 U before, which would make the patch refuse every bolus.
        start()

        userSettingFlow.tryEmit(
            CarelevoUserSettingInfoDomainModel(maxBolusDose = null, needMaxBolusDoseSyncPatch = true)
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>().first().maxBolusDose).isWithin(0.001).of(7.5)
    }

    @Test
    fun `a max bolus changed while the driver did not run is pushed once the patch is booted`() {
        // No flag: the change happened while another pump was active, or came with a settings import.
        start()

        userSettingFlow.tryEmit(CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0))

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>().single().maxBolusDose).isWithin(0.001).of(7.5)
    }

    @Test
    fun `nothing is pushed when the stored settings match the preferences`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(22)
        userSettingFlow.tryEmit(CarelevoUserSettingInfoDomainModel(maxBolusDose = 7.5, lowInsulinNoticeAmount = 22))
        start()

        patchStateFlow.tryEmit(PatchState.ConnectedBooted)

        // The plugin collects on an unconfined dispatcher, so a push would already have happened.
        verifyBlocking(commandQueue, never()) { customCommand(any()) }
    }

    @Test
    fun `a pending low-insulin sync is pushed once the patch is booted`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(22)
        start()

        userSettingFlow.tryEmit(
            CarelevoUserSettingInfoDomainModel(maxBolusDose = 7.5, lowInsulinNoticeAmount = 22, needLowInsulinNoticeAmountSyncPatch = true)
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateLowInsulinNotice>()) }
        assertThat(capturedCommands<CmdUpdateLowInsulinNotice>().first().amountUnits).isEqualTo(22)
    }

    @Test
    fun `a low-insulin preference that differs from the stored one is pushed`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(30)
        start()

        userSettingFlow.tryEmit(CarelevoUserSettingInfoDomainModel(maxBolusDose = 7.5, lowInsulinNoticeAmount = 22))

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateLowInsulinNotice>()) }
        assertThat(capturedCommands<CmdUpdateLowInsulinNotice>().single().amountUnits).isEqualTo(30)
    }

    @Test
    fun `a low-insulin reminder that is off is never pushed by the settings sync`() {
        // Zero = reminder off, which the preference observer never sends either.
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(0)
        start()

        // The max-bolus difference is the barrier: both settings are pushed from the same run, max bolus first.
        userSettingFlow.tryEmit(
            CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, lowInsulinNoticeAmount = 22, needLowInsulinNoticeAmountSyncPatch = true)
        )

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateLowInsulinNotice>()).isEmpty()
    }

    @Test
    fun `nothing is pushed while the patch is not booted and it flushes once it boots`() {
        start()
        patchStateFlow.tryEmit(PatchState.NotConnectedBooted)

        val pending = CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true)
        userSettingFlow.tryEmit(pending)
        // Not booted -> the combiner yields the empty need, so no CmdUpdateMaxBolus can ever be produced.
        patchStateFlow.tryEmit(PatchState.ConnectedBooted)

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        // Exactly one: the not-booted pass contributed nothing.
        assertThat(capturedCommands<CmdUpdateMaxBolus>()).hasSize(1)
    }

    @Test
    fun `a pending max-bolus sync is held back while an immediate bolus is running`() {
        // Max bolus is a device safety cap — re-pushing it mid-bolus would force a spurious reconnect.
        start()
        infusionInfoFlow.tryEmit(
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

        userSettingFlow.tryEmit(
            CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true)
        )
        // Bolus finished -> the same pending flag must now flush.
        infusionInfoFlow.tryEmit(CarelevoInfusionInfoDomainModel())

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>()).hasSize(1)
    }

    @Test
    fun `a pending max-bolus sync is held back while an extended bolus is running`() {
        // BOTH channels must be idle: an `||` here (the old bug) read as idle during a single-channel
        // bolus and triggered a mid-bolus reconnect.
        start()
        infusionInfoFlow.tryEmit(
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

        userSettingFlow.tryEmit(
            CarelevoUserSettingInfoDomainModel(maxBolusDose = 12.0, needMaxBolusDoseSyncPatch = true)
        )
        infusionInfoFlow.tryEmit(CarelevoInfusionInfoDomainModel())

        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CmdUpdateMaxBolus>()) }
        assertThat(capturedCommands<CmdUpdateMaxBolus>()).hasSize(1)
    }

    @Test
    fun `a low-insulin sync is pushed even mid-bolus - only max bolus is gated`() {
        whenever(preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)).thenReturn(22)
        start()
        infusionInfoFlow.tryEmit(
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

        userSettingFlow.tryEmit(
            CarelevoUserSettingInfoDomainModel(lowInsulinNoticeAmount = 22, needLowInsulinNoticeAmountSyncPatch = true)
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
        verify(uiInteraction).runAlarm(any(), any(), eq(AlarmSound.ERROR))
    }

    @Test
    fun `handleAlarms rings the full-screen alarm for a critical ALERT tier too`() {
        start()

        alarmHandler().invoke(listOf(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)))

        verify(carelevoAlarmNotifier).showTopNotification(any())
        verify(uiInteraction).runAlarm(any(), any(), eq(AlarmSound.ERROR))
    }

    @Test
    fun `the full-screen alarm names the button on the notification that acts on the alarm`() {
        whenever(rh.gs(CarelevoStrings.alarm_feat_title_alert_incomplete_patch_setting)).thenReturn("Patch Activation Incomplete")
        whenever(rh.gs(CarelevoStrings.common_btn_ok)).thenReturn("OK")
        whenever(rh.gs(eq(CarelevoStrings.carelevo_alarm_screen_text), anyVararg())).thenAnswer { it.arguments.drop(1).joinToString("|") }
        start()

        alarmHandler().invoke(listOf(alarm(AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE)))

        verify(uiInteraction).runAlarm(eq("Patch Activation Incomplete|OK"), any(), eq(AlarmSound.ERROR))
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
        verify(carelevoAlarmNotifier, never()).refreshAlarms()

        appForeground.state.value = true

        verify(carelevoAlarmNotifier, times(1)).refreshAlarms()
    }

    @Test
    fun `each return to the front refreshes the alarms again`() {
        start()

        appForeground.state.value = true
        appForeground.state.value = false
        appForeground.state.value = true

        verify(carelevoAlarmNotifier, times(2)).refreshAlarms()
    }

    @Test
    fun `onStart refreshes the alarms once when AAPS is already in front`() {
        appForeground.state.value = true

        start()

        verify(carelevoAlarmNotifier, times(1)).refreshAlarms()
    }

    // ---- onStop ---------------------------------------------------------------------------------

    @Test
    fun `onStop stops the alarm observer`() {
        start()

        runBlocking { plugin.onStop() }
        started = false

        verify(carelevoAlarmNotifier).stopObserving()
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

        appForeground.state.value = true

        verify(carelevoAlarmNotifier, never()).refreshAlarms()
    }

    @Test
    fun `onStop without a preceding onStart does not throw`() {
        // PluginBase launches onStart/onStop as separate unjoined coroutines, so a teardown with no
        // live generation (null scope) must be a clean no-op.
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

    // ---- auto-resume watchdog (issue #4993) -------------------------------------------------------
    // The first check runs at once after onStart, the next ones every 30 s.

    /** A patch stopped for [stopMinutes], with the stop stored [stoppedFor] ago. */
    private fun stoppedPatch(stopMinutes: Int?, stoppedFor: Duration) {
        patchInfoFlow.tryEmit(samplePatchInfo().copy(isStopped = true, stopMinutes = stopMinutes))
        val basal = CarelevoBasalInfusionInfoDomainModel(
            infusionId = "basal", address = "AA:BB:CC:DD:EE:FF", mode = 0, updatedAt = Clock.System.now() - stoppedFor,
            segments = emptyList(), isStop = true
        )
        infusionInfoFlow.tryEmit(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basal))
    }

    @Test
    fun `a timed stop whose time is over plus the margin is ended and the patch read`() {
        stoppedPatch(stopMinutes = 1, stoppedFor = 2.minutes)

        start()

        verify(carelevoPatch, timeout(2_000)).reconcileAutoResumed()
        verifyBlocking(commandQueue, timeout(2_000)) { readStatus("Carelevo auto-resume") }
    }

    @Test
    fun `a timed stop still inside its time and margin is left alone`() {
        stoppedPatch(stopMinutes = 1, stoppedFor = 70.seconds)

        start()

        verify(carelevoPatch, after(500).never()).reconcileAutoResumed()
    }

    @Test
    fun `a stop without a time is left alone`() {
        stoppedPatch(stopMinutes = 0, stoppedFor = 2.hours)

        start()

        verify(carelevoPatch, after(500).never()).reconcileAutoResumed()
    }

    @Test
    fun `a stop is left alone until the infusion info with its start is loaded`() {
        patchInfoFlow.tryEmit(samplePatchInfo().copy(isStopped = true, stopMinutes = 1))
        infusionInfoFlow.tryEmit(null)

        start()

        verify(carelevoPatch, after(500).never()).reconcileAutoResumed()
    }

    @Test
    fun `a failing check is logged and does not end the watchdog or the start`() {
        stoppedPatch(stopMinutes = 1, stoppedFor = 2.minutes)
        val failure = IllegalStateException("store gone")
        doThrow(failure).whenever(carelevoPatch).reconcileAutoResumed()

        start()

        verify(aapsLogger, timeout(2_000)).error(LTag.PUMPCOMM, "auto-resume check failed (will retry)", failure)
        verifyBlocking(commandQueue, never()) { readStatus("Carelevo auto-resume") }
    }

    // ---- active alarm snapshot after a reconnect -----------------------------------------------

    private val connectedFlow = MutableStateFlow(false)

    private fun snapshot(): ActiveAlarmSnapshotResponse =
        ActiveAlarmSnapshotResponse(
            tier = ActiveAlarmSnapshotTier.CRITICAL,
            infusing = false,
            flags = ActiveAlarmSnapshotFlags(
                outOfInsulin = false, operatingLifeExpired = false, lowBattery = false, outOfRangeTemperature = false,
                autoOff = false, unconnectedBle = false, patchAppIncomplete = false, startInsulin = false,
                selfDiagnosisFailed = false, patchExpired = false, patchError = false, occlusionDetected = true,
                userForcedTermination = false
            )
        )

    private fun startWatchingTheLink(address: String? = "AA:BB:CC:DD:EE:FF") {
        whenever(bleSession.connected).thenReturn(connectedFlow)
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(address)
        start()
        awaitCollector(connectedFlow)
    }

    @Test
    fun `a new link reads the active alarms of the patch and applies them`() {
        val snapshots = listOf(snapshot())
        whenever { bleSession.readActiveAlarmSnapshots(any()) }.thenReturn(snapshots)
        startWatchingTheLink()

        connectedFlow.value = true

        verifyBlocking(bleSession, timeout(2_000)) { readActiveAlarmSnapshots("AA:BB:CC:DD:EE:FF") }
        verify(carelevoPatch, timeout(2_000)).applyActiveAlarmSnapshots(snapshots)
    }

    @Test
    fun `the link state at the start and a lost link read nothing`() {
        connectedFlow.value = true
        startWatchingTheLink()

        connectedFlow.value = false

        verifyBlocking(bleSession, after(500).never()) { readActiveAlarmSnapshots(any()) }
    }

    @Test
    fun `no answer from an older patch applies nothing`() {
        whenever { bleSession.readActiveAlarmSnapshots(any()) }.thenReturn(emptyList())
        startWatchingTheLink()

        connectedFlow.value = true

        verifyBlocking(bleSession, timeout(2_000)) { readActiveAlarmSnapshots(any()) }
        verify(carelevoPatch, after(300).never()).applyActiveAlarmSnapshots(any())
    }

    @Test
    fun `without a patch address nothing is read`() {
        startWatchingTheLink(address = null)

        connectedFlow.value = true

        verify(aapsLogger, timeout(2_000)).warn(LTag.PUMPCOMM, "active alarm snapshot skipped: no patch address")
        verifyBlocking(bleSession, never()) { readActiveAlarmSnapshots(any()) }
    }

    @Test
    fun `a failed read is logged and the next link reads again`() {
        val failure = IllegalStateException("link down")
        whenever { bleSession.readActiveAlarmSnapshots(any()) }.thenAnswer { throw failure }
        startWatchingTheLink()

        connectedFlow.value = true
        verify(aapsLogger, timeout(2_000)).error(LTag.PUMPCOMM, "active alarm snapshot FAILED", failure)
        connectedFlow.value = false
        Thread.sleep(100)
        connectedFlow.value = true

        verifyBlocking(bleSession, timeout(2_000).times(2)) { readActiveAlarmSnapshots(any()) }
        verify(carelevoPatch, never()).applyActiveAlarmSnapshots(any())
    }

    // ---- profile update: the cancel steps reach the bolus and temp basal coordinators -------------

    @Test
    fun `a profile update may end a running extended bolus and temp basal on the patch`() {
        val coordinator: CarelevoBasalProfileUpdateCoordinator = mock()
        val bolusCoordinator: CarelevoBolusCoordinator = mock()
        val tempBasalCoordinator: CarelevoTempBasalCoordinator = mock()
        val ended: PumpEnactResult = mock()
        whenever { coordinator.updateBasalProfile(any(), any(), any(), any()) }.thenAnswer { invocation ->
            invocation.getArgument<() -> PumpEnactResult>(1).invoke()
            invocation.getArgument<() -> PumpEnactResult>(2).invoke()
            ended
        }
        whenever(bolusCoordinator.cancelExtendedBolus(any(), any())).thenReturn(ended)
        whenever(tempBasalCoordinator.cancelTempBasal(any(), any())).thenReturn(ended)
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.ConnectedBooted)
        plugin = buildPlugin(basalProfileUpdateCoordinator = coordinator, bolusCoordinator = bolusCoordinator, tempBasalCoordinator = tempBasalCoordinator)
        plugin.bleSession = bleSession
        val profile: PumpProfile = mock()

        val result = runBlocking { plugin.setNewBasalProfile(profile) }

        assertThat(result).isSameInstanceAs(ended)
        verify(bolusCoordinator).cancelExtendedBolus(eq("CARELEVO-TEST-001"), any())
        verify(tempBasalCoordinator).cancelTempBasal(eq("CARELEVO-TEST-001"), any())
    }
}
