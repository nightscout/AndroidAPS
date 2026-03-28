@file:Suppress("WildcardImport")

package app.aaps.pump.omnipod.common.bledriver.comm.legacy.io

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleCharacteristicIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationError
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationSuccess
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

open class BleIO(
    private val aapsLogger: AAPSLogger,
    private var characteristic: BluetoothGattCharacteristic,
    private val incomingPackets: BlockingQueue<ByteArray>,
    private val gatt: BluetoothGatt,
    private val bleCommCallbacks: BleCommCallbacks,
    private val type: CharacteristicType
) : BleCharacteristicIO {

    /**
     * @return a byte array with the received data or error
     */
    override fun receivePacket(timeoutMs: Long): ByteArray? {
        return try {
            val packet = incomingPackets.poll(timeoutMs, TimeUnit.MILLISECONDS)
            if (packet == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Timeout reading $type packet")
            }
            packet
        } catch (e: InterruptedException) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Interrupted while reading packet: $e")
            null
        }
    }

    /**
     * @param payload the data to send
     */
    @Suppress("ReturnCount", "DEPRECATION")
    override fun sendAndConfirmPacket(payload: ByteArray): BleSendResult {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BleIO: Sending on $type: ${payload.toHex()}")
        val set = characteristic.setValue(payload)
        if (!set) {
            return BleSendErrorSending("Could set setValue on $type")
        }
        bleCommCallbacks.flushConfirmationQueue()
        val sent = gatt.writeCharacteristic(characteristic)
        if (!sent) {
            return BleSendErrorSending("Could not writeCharacteristic on $type")
        }

        return when (
            val confirmation = bleCommCallbacks.confirmWrite(
                payload,
                type.value,
                DEFAULT_IO_TIMEOUT_MS
            )
        ) {
            is WriteConfirmationError   ->
                BleSendErrorConfirming(confirmation.msg)

            is WriteConfirmationSuccess ->
                BleSendSuccess
        }
    }

    /**
     * Called before sending a new message.
     * The incoming queues should be empty, so we log when they are not.
     */
    override fun flushIncomingQueue(): Boolean {
        var foundRTS = false
        do {
            val found = incomingPackets.poll()?.also {
                aapsLogger.warn(LTag.PUMPBTCOMM, "BleIO: queue not empty, flushing: ${it.toHex()}")
                if (it.isNotEmpty() && it[0] == BleCommandRTS.data[0]) {
                    foundRTS = true
                }
            }
        } while (found != null)
        return foundRTS
    }

    /**
     * Enable indications on the characteristic.
     * This will signal the pod it can start sending back data.
     * @return
     */
    @Suppress("DEPRECATION")
    override fun readyToRead(): BleSendResult {
        gatt.setCharacteristicNotification(characteristic, true)
            .assertTrue("enable notifications")

        val descriptors = characteristic.descriptors
        if (descriptors.size != 1) {
            throw ConnectException("Expecting one descriptor, found: ${descriptors.size}")
        }
        val descriptor = descriptors[0]
        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        gatt.writeDescriptor(descriptor)
            .assertTrue("enable indications on descriptor")

        aapsLogger.debug(LTag.PUMPBTCOMM, "Enabling indications for $type")
        val confirmation = bleCommCallbacks.confirmWrite(
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE,
            descriptor.uuid.toString(),
            DEFAULT_IO_TIMEOUT_MS
        )
        return when (confirmation) {
            is WriteConfirmationError   ->
                throw ConnectException(confirmation.msg)

            is WriteConfirmationSuccess ->
                BleSendSuccess
        }
    }

    companion object {
        const val DEFAULT_IO_TIMEOUT_MS = BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS
    }
}

private fun Boolean.assertTrue(operation: String) {
    if (!this) {
        throw ConnectException("Could not $operation")
    }
}
