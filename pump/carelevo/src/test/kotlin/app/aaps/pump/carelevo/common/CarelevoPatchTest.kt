package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.BondingState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.NotificationState
import app.aaps.pump.carelevo.ble.data.PeripheralConnectionState
import app.aaps.pump.carelevo.ble.data.ServiceDiscoverState
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoInfusionInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchInfoMonitorUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchRptInfusionInfoProcessUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoCreateUserSettingInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoUserSettingInfoMonitorUseCase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Tests the REAL [CarelevoPatch] (everywhere else it is mocked away): patch-state derivation,
 * Bluetooth edge-triggered alarm raising, and the flush/teardown bookkeeping every coordinator
 * depends on.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoPatchTest {

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

    @BeforeEach
    fun setUp() {
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(patchInfoMonitorUseCase.execute()).thenReturn(Observable.just(ResponseResult.Success(patchInfo())))
        whenever(infusionInfoMonitorUseCase.execute()).thenReturn(Observable.never())
        whenever(userSettingInfoMonitorUseCase.execute()).thenReturn(Observable.never())
        whenever(carelevoAlarmInfoUseCase.upsertAlarm(any())).thenReturn(Completable.complete())

        sut = CarelevoPatch(
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
    }

    @Test
    fun `resolvePatchState is NotConnectedNotBooting without a patch record`() {
        sut.onBluetoothStateChanged(bleState(enabled = true))

        assertThat(sut.resolvePatchState()).isEqualTo(PatchState.NotConnectedNotBooting)
    }

    @Test
    fun `resolvePatchState is ConnectedBooted with a patch record and available bluetooth`() {
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))

        assertThat(sut.resolvePatchState()).isEqualTo(PatchState.ConnectedBooted)
        assertThat(sut.getPatchInfoAddress()).isEqualTo("aa:bb:cc:dd:ee:ff")
    }

    @Test
    fun `resolvePatchState is NotConnectedBooted with a patch record but bluetooth off`() {
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = false))

        assertThat(sut.resolvePatchState()).isEqualTo(PatchState.NotConnectedBooted)
    }

    @Test
    fun `flushPatchInformation drops the patch record and the derived state`() {
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))
        assertThat(sut.resolvePatchState()).isEqualTo(PatchState.ConnectedBooted)

        sut.flushPatchInformation()

        assertThat(sut.getPatchInfoAddress()).isNull()
        assertThat(sut.resolvePatchState()).isEqualTo(PatchState.NotConnectedNotBooting)
    }

    @Test
    fun `bluetooth-off alarm fires only on the ON to OFF edge`() {
        // Initial OFF observation (no previous state): no alarm — this is a startup seed, not an edge.
        sut.onBluetoothStateChanged(bleState(enabled = false))
        verify(carelevoAlarmInfoUseCase, never()).upsertAlarm(any())

        // ON → OFF: exactly one alarm.
        sut.onBluetoothStateChanged(bleState(enabled = true))
        sut.onBluetoothStateChanged(bleState(enabled = false))
        val captor = argumentCaptor<CarelevoAlarmInfo>()
        verify(carelevoAlarmInfoUseCase, times(1)).upsertAlarm(captor.capture())
        assertThat(captor.firstValue.cause).isEqualTo(AlarmCause.ALARM_ALERT_BLUETOOTH_OFF)
        assertThat(captor.firstValue.isAcknowledged).isFalse()

        // Repeated OFF broadcasts: no alarm spam.
        sut.onBluetoothStateChanged(bleState(enabled = false))
        verify(carelevoAlarmInfoUseCase, times(1)).upsertAlarm(any())
    }

    @Test
    fun `alarm ids are unique across alarms raised in rapid succession`() {
        sut.onBluetoothStateChanged(bleState(enabled = true))
        sut.onBluetoothStateChanged(bleState(enabled = false))
        sut.onBluetoothStateChanged(bleState(enabled = true))
        sut.onBluetoothStateChanged(bleState(enabled = false))

        val captor = argumentCaptor<CarelevoAlarmInfo>()
        verify(carelevoAlarmInfoUseCase, times(2)).upsertAlarm(captor.capture())
        assertThat(captor.firstValue.alarmId).isNotEqualTo(captor.secondValue.alarmId)
    }

    @Test
    fun `isBluetoothEnabled reflects the last adapter state`() {
        assertThat(sut.isBluetoothEnabled()).isFalse()

        sut.onBluetoothStateChanged(bleState(enabled = true))
        assertThat(sut.isBluetoothEnabled()).isTrue()

        sut.onBluetoothStateChanged(bleState(enabled = false))
        assertThat(sut.isBluetoothEnabled()).isFalse()
    }

    @Test
    fun `discardTeardown clears patch state even when the unbond throws`() {
        sut.initPatch()
        sut.onBluetoothStateChanged(bleState(enabled = true))
        whenever(transport.adapter).thenThrow(RuntimeException("no adapter"))

        sut.discardTeardown()

        assertThat(sut.getPatchInfoAddress()).isNull()
        assertThat(sut.resolvePatchState()).isEqualTo(PatchState.NotConnectedNotBooting)
    }
}
