package app.aaps.pump.carelevo.common

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.BondingState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.NotificationState
import app.aaps.pump.carelevo.ble.data.PeripheralConnectionState
import app.aaps.pump.carelevo.ble.data.ServiceDiscoverState
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.model.CarelevoUserSettingInfoRequestModel
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Second suite over the REAL [CarelevoPatch], covering what [CarelevoPatchTest] leaves open:
 * the init/latch lifecycle, the `rxBus` fan-out of [CarelevoPatch.resolvePatchState], the
 * profile-equality helper, the `applyInfusionInfoReport` enum round-trip, the
 * [CarelevoPatch.handleAlarm] seam, the `discardTeardown` happy/single-flight paths, and the three monitor subscriptions
 * ([CarelevoPatch.patchInfo] / [CarelevoPatch.infusionInfo] / [CarelevoPatch.userSettingInfo])
 * including the "no user-setting record → seed defaults from prefs" path.
 *
 * [CarelevoPatchTest] keeps its own (already green) coverage of the state-derivation matrix, the
 * Bluetooth ON→OFF alarm edge and `flushPatchInformation`; nothing here duplicates it.
 *
 * Every Rx hop is pinned to [Schedulers.trampoline] so emissions are delivered synchronously on the
 * test thread, exactly as in [CarelevoPatchTest].
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchMoreTest {

    @Mock lateinit var transport: CarelevoBleTransport
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var sp: SP
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var infusionInfoMonitorUseCase: CarelevoInfusionInfoMonitorUseCase
    @Mock lateinit var patchInfoMonitorUseCase: CarelevoPatchInfoMonitorUseCase
    @Mock lateinit var userSettingInfoMonitorUseCase: CarelevoUserSettingInfoMonitorUseCase
    @Mock lateinit var patchRptInfusionInfoProcessUseCase: CarelevoPatchRptInfusionInfoProcessUseCase
    @Mock lateinit var createUserSettingInfoUseCase: CarelevoCreateUserSettingInfoUseCase
    @Mock lateinit var carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase
    @Mock lateinit var pumpResumeUseCase: CarelevoPumpResumeUseCase
    @Mock lateinit var bleAdapter: BleAdapter

    private lateinit var sut: CarelevoPatch

    private fun bleState(enabled: Boolean): BleState =
        BleState(
            isEnabled = if (enabled) DeviceModuleState.DEVICE_STATE_ON else DeviceModuleState.DEVICE_STATE_OFF,
            isBonded = BondingState.BOND_BONDED,
            isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_DISCOVERED,
            isConnected = PeripheralConnectionState.CONN_STATE_CONNECTED,
            isNotificationEnabled = NotificationState.NOTIFICATION_ENABLED
        )

    private fun patchInfo(): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(
            address = "aa:bb:cc:dd:ee:ff",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now(),
            manufactureNumber = "CARELEVO-TEST-001",
            insulinRemain = 60.0,
            bolusActionSeq = 1,
            mode = 1
        )

    /** Rebuilds the SUT — call after re-stubbing a monitor use case, the wiring happens in the ctor/initPatch. */
    private fun createPatch(): CarelevoPatch =
        CarelevoPatch(
            transport = transport,
            aapsSchedulers = aapsSchedulers,
            rxBus = rxBus,
            sp = sp,
            preferences = preferences,
            aapsLogger = aapsLogger,
            infusionInfoMonitorUseCase = infusionInfoMonitorUseCase,
            patchInfoMonitorUseCase = patchInfoMonitorUseCase,
            userSettingInfoMonitorUseCase = userSettingInfoMonitorUseCase,
            patchRptInfusionInfoProcessUseCase = patchRptInfusionInfoProcessUseCase,
            createUserSettingInfoUseCase = createUserSettingInfoUseCase,
            carelevoAlarmInfoUseCase = carelevoAlarmInfoUseCase,
            pumpResumeUseCase = pumpResumeUseCase
        )

    private fun profileWith(vararg values: Pair<Int, Double>): Profile {
        val profile = mock<Profile>()
        whenever(profile.getBasalValues())
            .thenReturn(values.map { Profile.ProfileValue(it.first, it.second) }.toTypedArray())
        return profile
    }

    /** Everything the RxBus saw, in order. */
    private fun sentEvents(): List<Event> {
        val captor = argumentCaptor<Event>()
        verify(rxBus, atLeastOnce()).send(captor.capture())
        return captor.allValues
    }

    @BeforeEach
    fun setUp() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(patchInfoMonitorUseCase.execute()).thenReturn(Observable.just(ResponseResult.Success(patchInfo())))
        whenever(infusionInfoMonitorUseCase.execute()).thenReturn(Observable.never())
        whenever(userSettingInfoMonitorUseCase.execute()).thenReturn(Observable.never())
        whenever(carelevoAlarmInfoUseCase.upsertAlarm(any())).thenReturn(Completable.complete())
        whenever(patchRptInfusionInfoProcessUseCase.execute(any()))
            .thenReturn(Single.just(ResponseResult.Success<CarelevoUseCaseResponse>(null)))
        whenever(createUserSettingInfoUseCase.execute(any()))
            .thenReturn(Single.just(ResponseResult.Success<CarelevoUseCaseResponse>(null)))
        whenever(transport.adapter).thenReturn(bleAdapter)

        sut = createPatch()
    }

    // ---------------------------------------------------------------------------------------------
    // site placement (wizard hand-off to the CANNULA_CHANGE therapy event)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `site placement defaults to NONE when the wizard step is skipped`() {
        assertThat(sut.sitePlacementLocation).isEqualTo(TE.Location.NONE)
        assertThat(sut.sitePlacementArrow).isEqualTo(TE.Arrow.NONE)
    }

    @Test
    fun `setSitePlacement stores the location and arrow chosen in the wizard`() {
        sut.setSitePlacement(TE.Location.SIDE_RIGHT_UPPER_ARM, TE.Arrow.UP)

        assertThat(sut.sitePlacementLocation).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(sut.sitePlacementArrow).isEqualTo(TE.Arrow.UP)

        // Last write wins — the flow re-sets it on every step change.
        sut.setSitePlacement(TE.Location.NONE, TE.Arrow.NONE)
        assertThat(sut.sitePlacementLocation).isEqualTo(TE.Location.NONE)
        assertThat(sut.sitePlacementArrow).isEqualTo(TE.Arrow.NONE)
    }

    // ---------------------------------------------------------------------------------------------
    // init lifecycle
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `initPatch latches isWorking and subscribes each monitor exactly once`() {
        assertThat(sut.isWorking).isFalse()

        sut.initPatch()
        sut.initPatch()

        assertThat(sut.isWorking).isTrue()
        verify(patchInfoMonitorUseCase, times(1)).execute()
        verify(infusionInfoMonitorUseCase, times(1)).execute()
        verify(userSettingInfoMonitorUseCase, times(1)).execute()
    }

    @Test
    fun `initPatchAndAwait defers initPatch until it is subscribed`() {
        val completable = sut.initPatchAndAwait()

        // Completable.defer: nothing runs at assembly time.
        assertThat(sut.isWorking).isFalse()
        verify(patchInfoMonitorUseCase, never()).execute()

        completable.subscribe({}, {})

        assertThat(sut.isWorking).isTrue()
        verify(patchInfoMonitorUseCase, times(1)).execute()
    }

    @Test
    fun `initPatchOnce shares one in-flight Completable across duplicate callers`() {
        val first = sut.initPatchOnce()
        val second = sut.initPatchOnce()

        assertThat(second).isSameInstanceAs(first)
    }

    // ---------------------------------------------------------------------------------------------
    // observeChangeState → patchState publication + rxBus fan-out
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an activated patch with bluetooth on publishes ConnectedBooted and the CONNECTED fan-out`() {
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))

        assertThat(sut.patchState.value?.get()).isEqualTo(PatchState.ConnectedBooted)

        val events = sentEvents()
        assertThat(events.filterIsInstance<EventPumpStatusChanged>().map { it.status })
            .containsExactly(EventPumpStatusChanged.Status.CONNECTED)
        assertThat(events.filterIsInstance<EventRefreshOverview>().map { it.from })
            .containsExactly("Carelevo connection state")
        assertThat(events.filterIsInstance<EventCustomActionsChanged>()).hasSize(1)
    }

    @Test
    fun `no patch record publishes NotConnectedNotBooting and the DISCONNECTED fan-out`() {
        whenever(patchInfoMonitorUseCase.execute())
            .thenReturn(Observable.just(ResponseResult.Success<CarelevoUseCaseResponse>(null)))
        sut = createPatch()

        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))

        assertThat(sut.patchState.value?.get()).isEqualTo(PatchState.NotConnectedNotBooting)
        assertThat(sut.getPatchInfoAddress()).isNull()

        val events = sentEvents()
        assertThat(events.filterIsInstance<EventPumpStatusChanged>().map { it.status })
            .containsExactly(EventPumpStatusChanged.Status.DISCONNECTED)
        assertThat(events.filterIsInstance<EventRefreshOverview>()).hasSize(1)
        assertThat(events.filterIsInstance<EventCustomActionsChanged>()).hasSize(1)
    }

    @Test
    fun `NotConnectedBooted is published silently without any rxBus fan-out`() {
        // Bluetooth off with a valid patch is a UI-only state: overview must not be told the pump
        // disconnected (the patch keeps delivering basal on its own).
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = false))

        assertThat(sut.patchState.value?.get()).isEqualTo(PatchState.NotConnectedBooted)
        verify(rxBus, never()).send(any())
    }

    // ---------------------------------------------------------------------------------------------
    // checkIsSameProfile
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `checkIsSameProfile is false before any profile has been set`() {
        assertThat(sut.checkIsSameProfile(profileWith(0 to 1.0))).isFalse()
    }

    @Test
    fun `checkIsSameProfile is false for a null candidate`() {
        sut.setProfile(profileWith(0 to 1.0))

        assertThat(sut.checkIsSameProfile(null)).isFalse()
    }

    @Test
    fun `checkIsSameProfile is false when the segment count differs`() {
        sut.setProfile(profileWith(0 to 1.0))

        assertThat(sut.checkIsSameProfile(profileWith(0 to 1.0, 3600 to 1.0))).isFalse()
    }

    @Test
    fun `checkIsSameProfile is false when a segment start time differs`() {
        sut.setProfile(profileWith(0 to 1.0, 3600 to 2.0))

        assertThat(sut.checkIsSameProfile(profileWith(0 to 1.0, 7200 to 2.0))).isFalse()
    }

    @Test
    fun `checkIsSameProfile ignores sub-minute start time differences`() {
        // Comparison is minute-resolution: 0s and 30s are the same pump segment boundary.
        sut.setProfile(profileWith(0 to 1.0))

        assertThat(sut.checkIsSameProfile(profileWith(30 to 1.0))).isTrue()
    }

    @Test
    fun `checkIsSameProfile is true for an identical profile`() {
        sut.setProfile(profileWith(0 to 0.75, 3600 to 1.25))

        assertThat(sut.checkIsSameProfile(profileWith(0 to 0.75, 3600 to 1.25))).isTrue()
    }

    @Test
    fun `checkIsSameProfile tolerates a sub-epsilon basal rate difference`() {
        sut.setProfile(profileWith(0 to 1.0))

        assertThat(sut.checkIsSameProfile(profileWith(0 to 1.0005))).isTrue()
    }

    @Test
    fun `checkIsSameProfile is false for a materially different basal rate`() {
        sut.setProfile(profileWith(0 to 1.0))

        assertThat(sut.checkIsSameProfile(profileWith(0 to 1.5))).isFalse()
    }

    @Test
    fun `checkIsSameProfile is false when a segment is zeroed out`() {
        // Guards nearlyEqual's zero branch: 0 vs 0.5 must never collapse to "same".
        sut.setProfile(profileWith(0 to 0.5))

        assertThat(sut.checkIsSameProfile(profileWith(0 to 0.0))).isFalse()
    }

    // ---------------------------------------------------------------------------------------------
    // applyInfusionInfoReport — raw 0x91 bytes normalized through the enum round-trip
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `applyInfusionInfoReport persists known raw codes through the enum round-trip`() {
        sut.applyInfusionInfoReport(
            runningMinutes = 42,
            remains = 137.5,
            infusedTotalBasalAmount = 12.25,
            infusedTotalBolusAmount = 3.5,
            pumpStateRaw = 2,   // RUNNING
            modeRaw = 5         // EXTEND_BOLUS
        )

        val captor = argumentCaptor<CarelevoPatchRptInfusionInfoRequestModel>()
        verify(patchRptInfusionInfoProcessUseCase).execute(captor.capture())
        val request = captor.firstValue
        assertThat(request.runningMinute).isEqualTo(42)
        assertThat(request.remains).isEqualTo(137.5)
        assertThat(request.infusedTotalBasalAmount).isEqualTo(12.25)
        assertThat(request.infusedTotalBolusAmount).isEqualTo(3.5)
        assertThat(request.pumpState).isEqualTo(2)
        assertThat(request.mode).isEqualTo(5)
        // Not carried by the 0x91 report — the process use case does not persist them.
        assertThat(request.currentInfusedProgramVolume).isEqualTo(0.0)
        assertThat(request.realInfusedTime).isEqualTo(0)
    }

    @Test
    fun `applyInfusionInfoReport maps unknown raw codes onto the ERROR codes`() {
        sut.applyInfusionInfoReport(
            runningMinutes = 1,
            remains = 1.0,
            infusedTotalBasalAmount = 0.0,
            infusedTotalBolusAmount = 0.0,
            pumpStateRaw = 99,
            modeRaw = 99
        )

        val captor = argumentCaptor<CarelevoPatchRptInfusionInfoRequestModel>()
        verify(patchRptInfusionInfoProcessUseCase).execute(captor.capture())
        assertThat(captor.firstValue.pumpState).isEqualTo(3)  // PumpStateResult.ERROR
        assertThat(captor.firstValue.mode).isEqualTo(-1)      // InfusionModeResult.ERROR
    }

    @Test
    fun `applyInfusionInfoReport round-trips every basal-to-bolus mode code unchanged`() {
        listOf(1, 2, 3, 4, 5).forEach { sut.applyInfusionInfoReport(0, 0.0, 0.0, 0.0, 0, it) }

        val captor = argumentCaptor<CarelevoPatchRptInfusionInfoRequestModel>()
        verify(patchRptInfusionInfoProcessUseCase, times(5)).execute(captor.capture())
        assertThat(captor.allValues.map { it.mode }).containsExactly(1, 2, 3, 4, 5).inOrder()
        // pumpStateRaw 0 → READY → 0 on every one of them.
        assertThat(captor.allValues.map { it.pumpState }.distinct()).containsExactly(0)
    }

    // ---------------------------------------------------------------------------------------------
    // handleAlarm — the public unsolicitedEvents bridge seam
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `handleAlarm raises an unacknowledged alarm carrying the cause value and type`() {
        sut.handleAlarm("warning", value = 5, cause = AlarmCause.ALARM_WARNING_LOW_INSULIN)

        val captor = argumentCaptor<CarelevoAlarmInfo>()
        verify(carelevoAlarmInfoUseCase).upsertAlarm(captor.capture())
        val info = captor.firstValue
        assertThat(info.cause).isEqualTo(AlarmCause.ALARM_WARNING_LOW_INSULIN)
        assertThat(info.alarmType).isEqualTo(AlarmCause.ALARM_WARNING_LOW_INSULIN.alarmType)
        assertThat(info.value).isEqualTo(5)
        assertThat(info.isAcknowledged).isFalse()
        assertThat(info.alarmId).isNotEmpty()
        assertThat(info.createdAt).isNotEmpty()
        assertThat(info.updatedAt).isNotEmpty()
    }

    @Test
    fun `handleAlarm swallows an upsert failure instead of tearing down the caller`() {
        whenever(carelevoAlarmInfoUseCase.upsertAlarm(any())).thenReturn(Completable.error(RuntimeException("db down")))

        // The bridge runs on the BLE notification path — an alarm write failure must not escape.
        sut.handleAlarm("alert", value = null, cause = AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED)

        val captor = argumentCaptor<CarelevoAlarmInfo>()
        verify(carelevoAlarmInfoUseCase).upsertAlarm(captor.capture())
        assertThat(captor.firstValue.value).isNull()
    }

    // ---------------------------------------------------------------------------------------------
    // reconcileAutoResumed — the auto-resume guard
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `reconcileAutoResumed clears the stopped state when the pump is stopped`() {
        whenever(patchInfoMonitorUseCase.execute())
            .thenReturn(Observable.just(ResponseResult.Success(patchInfo().copy(isStopped = true, stopMinutes = 30))))
        whenever(pumpResumeUseCase.persistResumed()).thenReturn(true)
        sut = createPatch()
        sut.initPatch()

        sut.reconcileAutoResumed()

        verify(pumpResumeUseCase).persistResumed()
    }

    @Test
    fun `reconcileAutoResumed is a no-op while the pump is running`() {
        // The default patchInfo() has isStopped = null (running); a basal-restart report (0x88) that fires
        // during normal delivery, or races a stop, must NOT clear a legitimate suspend.
        sut.initPatch()

        sut.reconcileAutoResumed()

        verify(pumpResumeUseCase, never()).persistResumed()
    }

    // ---------------------------------------------------------------------------------------------
    // releasePatch / discardTeardown
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `releasePatch clears both the cached patch and infusion info`() {
        whenever(infusionInfoMonitorUseCase.execute())
            .thenReturn(Observable.just(ResponseResult.Success(CarelevoInfusionInfoDomainModel())))
        sut = createPatch()
        sut.initPatch()
        assertThat(sut.infusionInfo.value?.isPresent).isTrue()

        sut.releasePatch()

        assertThat(sut.getPatchInfoAddress()).isNull()
        assertThat(sut.patchInfo.value?.isPresent).isFalse()
        assertThat(sut.infusionInfo.value?.isPresent).isFalse()
    }

    @Test
    fun `discardTeardown removes the OS bond for the uppercased patch address`() {
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))

        sut.discardTeardown()

        verify(bleAdapter).removeBond("AA:BB:CC:DD:EE:FF")
        assertThat(sut.getPatchInfoAddress()).isNull()
        assertThat(sut.resolvePatchState()).isEqualTo(PatchState.NotConnectedNotBooting)
    }

    @Test
    fun `discardTeardown without a patch record skips the unbond`() {
        sut.discardTeardown()

        verify(bleAdapter, never()).removeBond(any())
    }

    @Test
    fun `discardTeardown is single-flight against a re-entrant caller`() {
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))
        var reentered = false
        doAnswer {
            if (!reentered) {
                reentered = true
                // A queued CmdDiscard racing the ViewModel force-discard fallback: must be skipped.
                sut.discardTeardown()
            }
            null
        }.whenever(bleAdapter).removeBond(any())

        sut.discardTeardown()

        assertThat(reentered).isTrue()
        verify(bleAdapter, times(1)).removeBond(any())
    }

    @Test
    fun `discardTeardown releases the single-flight latch for the next patch`() {
        // Drive patchInfo by hand so a SECOND patch can be activated after the first is discarded.
        val patchRecords = PublishSubject.create<ResponseResult<CarelevoUseCaseResponse>>()
        whenever(patchInfoMonitorUseCase.execute()).thenReturn(patchRecords)
        sut = createPatch()
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))

        patchRecords.onNext(ResponseResult.Success(patchInfo()))
        sut.discardTeardown()
        // A later activation must not be blocked by a latch left set by the previous teardown.
        patchRecords.onNext(ResponseResult.Success(patchInfo()))
        sut.discardTeardown()

        verify(bleAdapter, times(2)).removeBond("AA:BB:CC:DD:EE:FF")
        assertThat(sut.getPatchInfoAddress()).isNull()
    }

    // ---------------------------------------------------------------------------------------------
    // monitor subscriptions
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a successful infusion info emission is cached`() {
        val model = CarelevoInfusionInfoDomainModel()
        whenever(infusionInfoMonitorUseCase.execute()).thenReturn(Observable.just(ResponseResult.Success(model)))
        sut = createPatch()

        sut.initPatch()

        assertThat(sut.infusionInfo.value?.get()).isEqualTo(model)
    }

    @Test
    fun `monitor errors and failures leave the cached info empty`() {
        whenever(infusionInfoMonitorUseCase.execute())
            .thenReturn(Observable.just(ResponseResult.Error(RuntimeException("boom")), ResponseResult.Failure("nope")))
        whenever(patchInfoMonitorUseCase.execute())
            .thenReturn(Observable.just(ResponseResult.Error(RuntimeException("boom")), ResponseResult.Failure("nope")))
        whenever(userSettingInfoMonitorUseCase.execute())
            .thenReturn(Observable.just(ResponseResult.Error(RuntimeException("boom")), ResponseResult.Failure("nope")))
        sut = createPatch()

        sut.initPatch()

        assertThat(sut.infusionInfo.value).isNull()
        assertThat(sut.patchInfo.value).isNull()
        assertThat(sut.userSettingInfo.value).isNull()
        // An errored user-setting read must NOT be mistaken for "no record" and seed defaults.
        verify(createUserSettingInfoUseCase, never()).execute(any())
    }

    @Test
    fun `an existing user setting record is cached and no defaults are seeded`() {
        val model = CarelevoUserSettingInfoDomainModel(lowInsulinNoticeAmount = 8, maxBasalSpeed = 15.0, maxBolusDose = 4.0)
        whenever(userSettingInfoMonitorUseCase.execute()).thenReturn(Observable.just(ResponseResult.Success(model)))
        sut = createPatch()

        sut.initPatch()

        assertThat(sut.userSettingInfo.value?.get()).isEqualTo(model)
        verify(createUserSettingInfoUseCase, never()).execute(any())
    }

    @Test
    fun `a missing user setting record seeds the defaults from the preferences`() {
        whenever(userSettingInfoMonitorUseCase.execute())
            .thenReturn(Observable.just(ResponseResult.Success<CarelevoUseCaseResponse>(null)))
        whenever(sp.getInt(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key, 30)).thenReturn(12)
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(7.5)
        sut = createPatch()

        sut.initPatch()

        val captor = argumentCaptor<CarelevoUserSettingInfoRequestModel>()
        verify(createUserSettingInfoUseCase).execute(captor.capture())
        assertThat(captor.firstValue.lowInsulinNoticeAmount).isEqualTo(12)
        assertThat(captor.firstValue.maxBasalSpeed).isEqualTo(15.0)
        assertThat(captor.firstValue.maxBolusDose).isEqualTo(7.5)
        assertThat(captor.firstValue.patchState).isNull()
    }
}
