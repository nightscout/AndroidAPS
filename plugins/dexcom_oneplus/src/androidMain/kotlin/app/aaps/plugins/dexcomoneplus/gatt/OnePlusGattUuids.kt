package app.aaps.plugins.dexcomoneplus.gatt

import java.util.UUID

/**
 * The shared [OnePlusBluetoothUuids] text, as the `java.util.UUID` values the Android BLE stack
 * takes. Built once, so a `when` branch can compare them by identity the way it always did.
 */
object OnePlusGattUuids {

    val DeviceInfo: UUID = UUID.fromString(OnePlusBluetoothUuids.DeviceInfo)
    val Advertisement: UUID = UUID.fromString(OnePlusBluetoothUuids.Advertisement)
    val CgmService: UUID = UUID.fromString(OnePlusBluetoothUuids.CgmService)
    val ServiceB: UUID = UUID.fromString(OnePlusBluetoothUuids.ServiceB)

    val ManufacturerNameString: UUID = UUID.fromString(OnePlusBluetoothUuids.ManufacturerNameString)

    val Communication: UUID = UUID.fromString(OnePlusBluetoothUuids.Communication)
    val Control: UUID = UUID.fromString(OnePlusBluetoothUuids.Control)
    val Authentication: UUID = UUID.fromString(OnePlusBluetoothUuids.Authentication)
    val ProbablyBackfill: UUID = UUID.fromString(OnePlusBluetoothUuids.ProbablyBackfill)
    val ExtraData: UUID = UUID.fromString(OnePlusBluetoothUuids.ExtraData)

    val CharacteristicE: UUID = UUID.fromString(OnePlusBluetoothUuids.CharacteristicE)
    val CharacteristicF: UUID = UUID.fromString(OnePlusBluetoothUuids.CharacteristicF)

    val CharacteristicUpdateNotification: UUID = UUID.fromString(OnePlusBluetoothUuids.CharacteristicUpdateNotification)
}
