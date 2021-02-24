package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CharacteristicNotFoundException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.ServiceNotFoundException
import java.math.BigInteger
import java.util.*

class ServiceDiscoverer(private val logger: AAPSLogger, private val gatt: BluetoothGatt, private val bleCallbacks: BleCommCallbacks) {

    /***
     * This is first step after connection establishment
     */
    @Throws(InterruptedException::class, ServiceNotFoundException::class, CharacteristicNotFoundException::class)
    fun discoverServices(): Map<CharacteristicType, BluetoothGattCharacteristic> {
        logger.debug(LTag.PUMPBTCOMM, "Discovering services")
        gatt.discoverServices()
        bleCallbacks.waitForServiceDiscovery(DISCOVER_SERVICES_TIMEOUT_MS)
        logger.debug(LTag.PUMPBTCOMM, "Services discovered")
        val service = gatt.getService(
            uuidFromString(SERVICE_UUID))
            ?: throw ServiceNotFoundException(SERVICE_UUID)
        val cmdChar = service.getCharacteristic(CharacteristicType.CMD.uUID)
            ?: throw CharacteristicNotFoundException(CharacteristicType.CMD.value)
        val dataChar = service.getCharacteristic(CharacteristicType.DATA.uUID)
            ?: throw CharacteristicNotFoundException(CharacteristicType.DATA.value)
        var chars = mapOf(CharacteristicType.CMD to cmdChar,
                CharacteristicType.DATA to dataChar)
        return chars
    }

    companion object {
        private const val SERVICE_UUID = "1a7e-4024-e3ed-4464-8b7e-751e03d0dc5f"
        private const val DISCOVER_SERVICES_TIMEOUT_MS = 5000
        private fun uuidFromString(s: String): UUID {
            return UUID(
                BigInteger(s.replace("-", "").substring(0, 16), 16).toLong(),
                BigInteger(s.replace("-", "").substring(16), 16).toLong()
            )
        }
    }
}