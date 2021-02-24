package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType.Companion.byValue
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmDescriptorWriteException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.CouldNotConfirmWrite
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BleCommCallbacks(private val aapsLogger: AAPSLogger, private val incomingPackets: Map<CharacteristicType, BlockingQueue<ByteArray>>) : BluetoothGattCallback() {

    private val serviceDiscoveryComplete: CountDownLatch = CountDownLatch(1)
    private val connected: CountDownLatch = CountDownLatch(1)
    private val writeQueue: BlockingQueue<CharacteristicWriteConfirmation> = LinkedBlockingQueue(1)
    private val descriptorWriteQueue: BlockingQueue<DescriptorWriteConfirmation> = LinkedBlockingQueue(1)

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        aapsLogger.debug(LTag.PUMPBTCOMM, "OnConnectionStateChange discovered with status/state$status/$newState")
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            connected.countDown()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        aapsLogger.debug(LTag.PUMPBTCOMM, "OnServicesDiscovered with status$status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            serviceDiscoveryComplete.countDown()
        }
    }

    @Throws(InterruptedException::class)
    fun waitForConnection(timeout_ms: Int) {
        connected.await(timeout_ms.toLong(), TimeUnit.MILLISECONDS)
    }

    @Throws(InterruptedException::class)
    fun waitForServiceDiscovery(timeout_ms: Int) {
        serviceDiscoveryComplete.await(timeout_ms.toLong(), TimeUnit.MILLISECONDS)
    }

    @Throws(InterruptedException::class, TimeoutException::class, CouldNotConfirmWrite::class)
    fun confirmWrite(expectedPayload: ByteArray, timeout_ms: Int) {
        val received: CharacteristicWriteConfirmation = writeQueue.poll(timeout_ms.toLong(), TimeUnit.MILLISECONDS)
            ?: throw TimeoutException()

        when (received) {
            is CharacteristicWriteConfirmationPayload -> confirmWritePayload(expectedPayload, received)
            is CharacteristicWriteConfirmationError ->
                aapsLogger.debug(LTag.PUMPBTCOMM, "Could not confirm write: status was ${received.status}")
        }

    }

    private fun confirmWritePayload(expectedPayload: ByteArray, received: CharacteristicWriteConfirmationPayload) {
        if (!expectedPayload.contentEquals(received.payload)) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Could not confirm write. Got " + received.payload + ".Excepted: " + expectedPayload + ". Status: " + received.status)
            throw CouldNotConfirmWrite(expectedPayload, received.payload)
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Confirmed write with value: " + received.payload)
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        val writeConfirmation = if (status == BluetoothGatt.GATT_SUCCESS) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "OnCharacteristicWrite value " + characteristic.getStringValue(0))
            CharacteristicWriteConfirmationPayload(characteristic.value, status)
        } else {
            CharacteristicWriteConfirmationError(status)
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "OnCharacteristicWrite with status/char/value " +
            status + "/" + byValue(characteristic.uuid.toString()) + "/" + characteristic.value)
        try {
            if (writeQueue.size > 0) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Write confirm queue should be empty. found: " + writeQueue.size)
                writeQueue.clear()
            }
            val offered = writeQueue.offer(writeConfirmation, WRITE_CONFIRM_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            if (!offered) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Received delayed write confirmation")
            }
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while sending write confirmation")
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        super.onCharacteristicChanged(gatt, characteristic)
        val payload = characteristic.value
        val characteristicType = byValue(characteristic.uuid.toString())
        aapsLogger.debug(LTag.PUMPBTCOMM, "OnCharacteristicChanged with char/value " +
            characteristicType + "/" +
            payload)
        incomingPackets[characteristicType]!!.add(payload)
    }

    @Throws(InterruptedException::class, CouldNotConfirmDescriptorWriteException::class)
    fun confirmWriteDescriptor(descriptorUUID: String, timeout_ms: Int) {
        val confirmed = descriptorWriteQueue.poll(timeout_ms.toLong(), TimeUnit.MILLISECONDS)
        if (descriptorUUID != confirmed.uuid) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Could not confirm descriptor write. Got " + confirmed.uuid + ".Expected: " + descriptorUUID + ". Status: " + confirmed.status)
            throw CouldNotConfirmDescriptorWriteException(confirmed.uuid!!, descriptorUUID)
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status)
        var uuid: String? = null
        if (status == BluetoothGatt.GATT_SUCCESS) {
            uuid = descriptor.uuid.toString()
        }
        val confirmation = DescriptorWriteConfirmation(status, uuid)
        try {
            if (descriptorWriteQueue.size > 0) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Descriptor write queue should be empty, found: " + descriptorWriteQueue.size)
                descriptorWriteQueue.clear()
            }
            val offered = descriptorWriteQueue.offer(confirmation, WRITE_CONFIRM_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            if (!offered) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Received delayed descriptor write confirmation")
            }
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while sending descriptor write confirmation")
        }
    }

    companion object {

        private const val WRITE_CONFIRM_TIMEOUT_MS = 10 // the other thread should be waiting for the exchange
    }
}