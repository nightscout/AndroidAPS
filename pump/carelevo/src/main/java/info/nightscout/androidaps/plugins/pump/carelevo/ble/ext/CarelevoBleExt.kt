package info.nightscout.androidaps.plugins.pump.carelevo.ble.ext

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import java.util.UUID

@SuppressLint("MissingPermission")
internal fun BluetoothManager.existBondedDevice(macAddress: String): Boolean {
    if (adapter == null) {
        return false
    }
    val devices = getConnectedDevices(BluetoothProfile.GATT)
    var isExist = false
    devices.forEach {
        if (it.address == macAddress) {
            isExist = true
        }
    }
    return isExist
}

internal fun BluetoothDevice.removeBond(): Boolean {
    return runCatching {
        this.javaClass.getMethod("removeBond").invoke(this)
    }.fold(
        onSuccess = {
            true
        },
        onFailure = {
            it.printStackTrace()
            false
        }
    )
}

internal fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    services?.forEach { service ->
        service.characteristics?.firstOrNull() { characteristic ->
            characteristic.uuid == uuid
        }?.let {
            return it
        }
    }

    return null
}

internal fun BluetoothGattCharacteristic.containerProperty(property: Int) = property and property != 0

internal fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containerProperty(BluetoothGattCharacteristic.PERMISSION_READ)

internal fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containerProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

internal fun BluetoothGattDescriptor.character(type: ByteArray): Boolean =
    value.contentEquals(type)

internal fun BluetoothGattDescriptor.isEnabled(): Boolean =
    character(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) || character(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)

fun BluetoothGatt.refresh(): Boolean {
    return try {
        this.javaClass.getMethod("refresh").invoke(this) as Boolean
    } catch (e: Exception) {
        false
    }
}