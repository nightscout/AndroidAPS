package info.nightscout.androidaps.plugins.pump.carelevo.ble.core

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanFilter

import java.util.UUID

abstract class BleCommand {
    abstract val isImportant : Boolean
    abstract var retryCnt : Int
}

sealed class BlePeripheralCommand : BleCommand() {
    abstract val address : String
}

data class Delay(
    val duration : Long = 1000,
    override val isImportant: Boolean = false,
    override var retryCnt: Int = 1
) : BleCommand()

data class StartScan(
    val scanFilter : ScanFilter? = null,
    override val isImportant: Boolean = false,
    override var retryCnt : Int = 1
) : BleCommand()

data class StopScan(
    override val isImportant: Boolean = false,
    override var retryCnt: Int = 1
) : BleCommand()

data class Connect(
    override val address : String,
    override val isImportant: Boolean = false,
    override var retryCnt: Int = 1
) : BlePeripheralCommand()

data class Disconnect(
    override val address : String,
    override val isImportant: Boolean = false,
    override var retryCnt : Int = 1
) : BlePeripheralCommand()

data class DiscoveryService(
    override val address : String,
    override val isImportant : Boolean = false,
    override var retryCnt : Int = 1
) : BlePeripheralCommand()

data class ReadFromCharacteristic(
    override val address : String,
    var characteristicUuid : UUID,
    override val isImportant : Boolean = false,
    override var retryCnt : Int = 1
) : BlePeripheralCommand()

data class WriteToCharacteristic(
    override val address : String,
    var characteristicUuid : UUID,
    val writeType : Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
    val payload : ByteArray,
    override val isImportant: Boolean = false,
    override var retryCnt : Int = 1
) : BlePeripheralCommand() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WriteToCharacteristic

        if (address != other.address) return false
        if (characteristicUuid != other.characteristicUuid) return false
        if (writeType != other.writeType) return false
        if (!payload.contentEquals(other.payload)) return false
        if (isImportant != other.isImportant) return false
        if (retryCnt != other.retryCnt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + characteristicUuid.hashCode()
        result = 31 * result + writeType
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + isImportant.hashCode()
        result = 31 * result + retryCnt
        return result
    }
}

data class EnableNotifications(
    override val address : String,
    val characteristicUuid : UUID,
    override val isImportant: Boolean = false,
    override var retryCnt : Int = 1
) : BlePeripheralCommand()

data class DisableNotifications(
    override val address : String,
    val characteristicUuid : UUID,
    override val isImportant: Boolean = false,
    override var retryCnt : Int = 1
) : BlePeripheralCommand()

data class UnBondDevice(
    override val address : String,
    override val isImportant: Boolean = true,
    override var retryCnt : Int = 1
) : BlePeripheralCommand()