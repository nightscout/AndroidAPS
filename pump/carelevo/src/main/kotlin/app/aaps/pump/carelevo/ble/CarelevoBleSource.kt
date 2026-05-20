package app.aaps.pump.carelevo.ble

import app.aaps.pump.carelevo.ble.core.BleCommand
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.BondingState
import app.aaps.pump.carelevo.ble.data.CharacterResult
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.NotificationState
import app.aaps.pump.carelevo.ble.data.PeripheralConnectionState
import app.aaps.pump.carelevo.ble.data.PeripheralScanResult
import app.aaps.pump.carelevo.ble.data.ServiceDiscoverState
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject

object CarelevoBleSource {

    val bleCommandChains: PublishSubject<MutableList<BleCommand>> = PublishSubject.create()

    internal val _bluetoothState: BehaviorSubject<BleState> = BehaviorSubject.createDefault(
        BleState(
            isEnabled = DeviceModuleState.DEVICE_NONE,
            isBonded = BondingState.BOND_NONE,
            isConnected = PeripheralConnectionState.CONN_STATE_NONE,
            isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
            isNotificationEnabled = NotificationState.NOTIFICATION_NONE
        )
    )
    val bluetoothState get() = _bluetoothState

    val _scanDevices: BehaviorSubject<PeripheralScanResult> = BehaviorSubject.createDefault(PeripheralScanResult.Init(listOf()))
    val scanDevices get() = _scanDevices

    val _notifyIndicateBytes: PublishSubject<CharacterResult> = PublishSubject.create()
    val notifyIndicateByte get() = _notifyIndicateBytes
}