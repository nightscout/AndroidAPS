package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.*
import info.nightscout.androidaps.utils.extensions.toHex
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BleIO(private val aapsLogger: AAPSLogger, private val chars: Map<CharacteristicType, BluetoothGattCharacteristic>, private val incomingPackets: Map<CharacteristicType, BlockingQueue<ByteArray>>, private val gatt: BluetoothGatt, private val bleCommCallbacks: BleCommCallbacks) {

    private var state: IOState = IOState.IDLE

    /***
     *
     * @param characteristic where to read from(CMD or DATA)
     * @return a byte array with the received data
     */
    @Throws(BleIOBusyException::class, InterruptedException::class, TimeoutException::class)
    fun receivePacket(characteristic: CharacteristicType): ByteArray {
        synchronized(state) {
            if (state != IOState.IDLE) {
                throw BleIOBusyException()
            }
            state = IOState.READING
        }
        val ret = incomingPackets[characteristic]?.poll(DEFAULT_IO_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            ?: throw TimeoutException()
        synchronized(state) { state = IOState.IDLE }
        return ret
    }

    /***
     *
     * @param characteristic where to write to(CMD or DATA)
     * @param payload the data to send
     * @throws CouldNotSendBleException
     */
    @Throws(CouldNotSendBleException::class, BleIOBusyException::class, InterruptedException::class, CouldNotConfirmWriteException::class, TimeoutException::class)
    fun sendAndConfirmPacket(characteristic: CharacteristicType, payload: ByteArray) {
        synchronized(state) {
            if (state != IOState.IDLE) {
                throw BleIOBusyException()
            }
            state = IOState.WRITING
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "BleIO: Sending data on " + characteristic.name + "/" + payload.toHex())
        aapsLogger.debug(LTag.PUMPBTCOMM, "BleIO: Sending data on " + characteristic.name + "/" + payload.toHex())
        val ch = chars[characteristic]
        val set = ch!!.setValue(payload)
        if (!set) {
            throw CouldNotSendBleException("setValue")
        }
        val sent = gatt.writeCharacteristic(ch)
        if (!sent) {
            throw CouldNotSendBleException("writeCharacteristic")
        }
        bleCommCallbacks.confirmWrite(payload, DEFAULT_IO_TIMEOUT_MS)
        synchronized(state) { state = IOState.IDLE }
    }

    /**
     * Called before sending a new message.
     * The incoming queues should be empty, so we log when they are not.
     */
    fun flushIncomingQueues() {}

    /**
     * Enable intentions on the characteristics.
     * This will signal the pod it can start sending back data
     * @return
     */
    @Throws(CouldNotSendBleException::class, CouldNotEnableNotifications::class, DescriptorNotFoundException::class, InterruptedException::class, CouldNotConfirmDescriptorWriteException::class)
    fun readyToRead() {
        for (type in CharacteristicType.values()) {
            val ch = chars[type]
            val notificationSet = gatt.setCharacteristicNotification(ch, true)
            if (!notificationSet) {
                throw CouldNotEnableNotifications(type)
            }
            val descriptors = ch!!.descriptors
            if (descriptors.size != 1) {
                throw DescriptorNotFoundException()
            }
            val descriptor = descriptors[0]
            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            gatt.writeDescriptor(descriptor)
            bleCommCallbacks.confirmWriteDescriptor(descriptor.uuid.toString(), DEFAULT_IO_TIMEOUT_MS)
        }
    }

    companion object {

        private const val DEFAULT_IO_TIMEOUT_MS = 1000
    }
}