package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.CharacteristicType.Companion.byValue
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io.IncomingPackets
import info.nightscout.androidaps.utils.extensions.toHex
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BleCommCallbacks(
    private val aapsLogger: AAPSLogger,
    private val incomingPackets: IncomingPackets,
) : BluetoothGattCallback() {

    private val serviceDiscoveryComplete: CountDownLatch = CountDownLatch(1)
    private val connected: CountDownLatch = CountDownLatch(1)
    private val writeQueue: BlockingQueue<WriteConfirmation> = LinkedBlockingQueue(1)

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        aapsLogger.debug(LTag.PUMPBTCOMM, "OnConnectionStateChange with status/state: $status/$newState")
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

    fun waitForConnection(timeoutMs: Int) {
        try {
            connected.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while waiting for Connection")
        }
    }

    fun waitForServiceDiscovery(timeoutMs: Int) {
        try {
            serviceDiscoveryComplete.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while waiting for ServiceDiscovery")
        }
    }

    fun confirmWrite(expectedPayload: ByteArray, expectedUUID: String, timeoutMs: Long): WriteConfirmation {
        try {
            return when (val received = writeQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)) {
                null -> return WriteConfirmationError("Timeout waiting for writeConfirmation")
                is WriteConfirmationSuccess ->
                    if (expectedPayload.contentEquals(received.payload) &&
                        expectedUUID == received.uuid) {
                        received
                    } else {
                        aapsLogger.warn(
                            LTag.PUMPBTCOMM,
                            "Could not confirm write. Got " + received.payload.toHex() + ".Excepted: " + expectedPayload.toHex()
                        )
                        WriteConfirmationError("Received incorrect writeConfirmation")
                    }
                is WriteConfirmationError ->
                    received
            }
        } catch (e: InterruptedException) {
            return WriteConfirmationError("Interrupted waiting for confirmation")
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        onWrite(status, characteristic.uuid, characteristic.value)
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        super.onCharacteristicChanged(gatt, characteristic)
        val payload = characteristic.value
        val characteristicType = byValue(characteristic.uuid.toString())
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "OnCharacteristicChanged with char/value " +
                characteristicType + "/" +
                payload.toHex()
        )

        incomingPackets.byCharacteristicType(characteristicType).add(payload)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status)

        onWrite(status, descriptor.uuid, descriptor.value)
    }

    private fun onWrite(status: Int, uuid: UUID?, value: ByteArray?) {
        if (uuid == null || value == null) {
            return
        }
        val writeConfirmation = when {
            uuid == null || value == null ->
                WriteConfirmationError("onWrite received Null: UUID=$uuid, value=${value.toHex()} status=$status")

            status == BluetoothGatt.GATT_SUCCESS -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "OnWrite value " + value.toHex())
                WriteConfirmationSuccess(uuid.toString(), value)
            }

            else -> WriteConfirmationError("onDescriptorWrite status is not success: $status")
        }

        try {
            flushConfirmationQueue()
            val offered = writeQueue.offer(
                writeConfirmation,
                WRITE_CONFIRM_TIMEOUT_MS.toLong(),
                TimeUnit.MILLISECONDS
            )
            if (!offered) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Received delayed write confirmation")
            }
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while sending write confirmation")
        }
    }

    fun flushConfirmationQueue() {
        if (writeQueue.size > 0) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "Write queue should be empty, found: ${writeQueue.size}"
            )
            writeQueue.clear()
        }
    }

    companion object {

        private const val WRITE_CONFIRM_TIMEOUT_MS = 10 // the confirmation queue should be empty anyway
    }
}
