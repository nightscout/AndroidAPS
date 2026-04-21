package info.nightscout.androidaps.plugins.pump.carelevo.ble.core

import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.BleParams
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import io.reactivex.rxjava3.core.Single

interface CarelevoBleController {

    fun initController()
    fun registerPeripheralInfo()
    fun unRegisterPeripheralInfo()
    fun isBluetoothEnabled(): Boolean
    fun getConnectedAddress(): String?
    fun getParams(): BleParams
    fun checkGatt(): Boolean
    fun clearGatt()
    fun clearOnlyGatt()
    fun clearScan()
    fun isBonded(address: String): Boolean
    fun clearBond(address: String): CommandResult<Boolean>
    fun unBondDevice(): CommandResult<Boolean>
    fun unBondDevice(address: String): CommandResult<Boolean>
    fun pend(command: BleCommand): CommandResult<Boolean>
    fun execute(command: BleCommand): Single<CommandResult<Boolean>>
    fun stop(): Boolean

    fun isConnectedNow(address: String): Boolean
}