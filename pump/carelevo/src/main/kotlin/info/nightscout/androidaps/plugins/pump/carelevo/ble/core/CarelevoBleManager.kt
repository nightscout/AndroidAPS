package info.nightscout.androidaps.plugins.pump.carelevo.ble.core

import android.bluetooth.BluetoothGatt
import android.bluetooth.le.ScanFilter
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import java.util.UUID

interface CarelevoBleManager {

    fun isBluetoothEnabled(): Boolean
    fun getBluetoothAdapterState(): Int
    fun isNotificationEnabled(): Boolean
    fun registerPeripheralInfoRegistered()
    fun unRegisterPeripheralInfoRegistered()
    fun isConnected(macAddress: String): Boolean
    fun getGatt(): BluetoothGatt?
    fun clearGatt()
    fun isBonded(macAddress: String): Boolean
    fun clearBond(macAddress: String): CommandResult<Boolean>
    fun disableManager(): Boolean

    fun startScan(scanFilter: ScanFilter?): CommandResult<Boolean>
    fun stopScan(): CommandResult<Boolean>
    suspend fun connectTo(macAddress: String): CommandResult<Boolean>
    fun disconnectFrom(): CommandResult<Boolean>
    fun discoverService(): CommandResult<Boolean>
    fun unBondDevice(macAddress: String): CommandResult<Boolean>
    fun writeCharacteristic(uuid: UUID, payload: ByteArray): CommandResult<Boolean>
    fun readCharacteristic(characteristicUuid: UUID): CommandResult<Boolean>
    fun enabledNotifications(uuid: UUID): CommandResult<Boolean>
    fun disabledNotifications(uuid: UUID): CommandResult<Boolean>
}