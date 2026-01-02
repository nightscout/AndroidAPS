package app.aaps.pump.omnipod.dash.driver.comm

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.dash.driver.comm.io.CharacteristicType
import app.aaps.pump.omnipod.dash.driver.comm.session.Connected
import app.aaps.pump.omnipod.dash.driver.comm.session.Connection
import app.aaps.pump.omnipod.dash.driver.comm.session.Connection.Companion.STOP_CONNECTING_CHECK_INTERVAL_MS
import app.aaps.pump.omnipod.dash.driver.comm.session.ConnectionWaitCondition
import java.math.BigInteger
import java.util.UUID

class ServiceDiscoverer(
    private val logger: AAPSLogger,
    private val gatt: BluetoothGatt,
    private val bleCallbacks: BleCommCallbacks,
    private val connection: Connection
) {

    /***
     * This is first step after connection establishment
     */
    fun discoverServices(connectionWaitCond: ConnectionWaitCondition): Map<CharacteristicType, BluetoothGattCharacteristic> {
        logger.debug(LTag.PUMPBTCOMM, "Discovering services")
        bleCallbacks.startServiceDiscovery()
        try {
            val discover = gatt.discoverServices()
            if (!discover) {
                throw ConnectException("Could not start discovering services`")
            }
        } catch (ex: SecurityException) {
            throw ConnectException("Missing bluetooth permission")
        }
        connectionWaitCond.timeoutMs?.let {
            bleCallbacks.waitForServiceDiscovery(it)
        }
        connectionWaitCond.stopConnection?.let {
            while (!bleCallbacks.waitForServiceDiscovery(STOP_CONNECTING_CHECK_INTERVAL_MS)) {
                if (it.count == 0L || connection.connectionState() !is Connected) {
                    throw ConnectException("stopConnecting called")
                }
            }
        }
        logger.debug(LTag.PUMPBTCOMM, "Services discovered")
        val service = gatt.getService(SERVICE_UUID.toUuid())
            ?: run {
                for (service in gatt.services) {
                    logger.debug(LTag.PUMPBTCOMM, "Found service: ${service.uuid}")
                }
                throw ConnectException("Service not found: $SERVICE_UUID")
            }
        val cmdChar = service.getCharacteristic(CharacteristicType.CMD.uuid)
            ?: throw ConnectException("Characteristic not found: ${CharacteristicType.CMD.value}")
        val dataChar = service.getCharacteristic(CharacteristicType.DATA.uuid)
            ?: throw ConnectException("Characteristic not found: ${CharacteristicType.DATA.value}")
        return mapOf(
            CharacteristicType.CMD to cmdChar,
            CharacteristicType.DATA to dataChar
        )
    }

    private fun String.toUuid(): UUID = UUID(
        BigInteger(replace("-", "").substring(0, 16), 16).toLong(),
        BigInteger(replace("-", "").substring(16), 16).toLong()
    )

    companion object {

        private const val SERVICE_UUID = "1a7e-4024-e3ed-4464-8b7e-751e03d0dc5f"
    }
}
