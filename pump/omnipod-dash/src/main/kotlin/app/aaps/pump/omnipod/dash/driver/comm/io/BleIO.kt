@file:Suppress("WildcardImport")

package app.aaps.pump.omnipod.dash.driver.comm.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.dash.driver.comm.callbacks.WriteConfirmationError
import app.aaps.pump.omnipod.dash.driver.comm.callbacks.WriteConfirmationSuccess
import app.aaps.pump.omnipod.dash.driver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.ConnectException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

sealed class BleSendResult

object BleSendSuccess : BleSendResult()
data class BleSendErrorSending(val msg: String, val cause: Throwable? = null) : BleSendResult()
data class BleSendErrorConfirming(val msg: String, val cause: Throwable? = null) : BleSendResult()

open class BleIO(
    private val aapsLogger: AAPSLogger,
    private var characteristic: BluetoothGattCharacteristic,
    private val incomingPackets: BlockingQueue<ByteArray>,
    private val gatt: BluetoothGatt,
    private val bleCommCallbacks: BleCommCallbacks,
    private val type: CharacteristicType
) {

    /***
     *
     * @return a byte array with the received data or error
     */
    fun receivePacket(timeoutMs: Long = DEFAULT_IO_TIMEOUT_MS): ByteArray? {
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

    /***
     *
     * @param payload the data to send
     */
    @Suppress("ReturnCount", "DEPRECATION")
    fun sendAndConfirmPacket(payload: ByteArray): BleSendResult {
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
    open fun flushIncomingQueue(): Boolean {
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
     * Enable intentions on the characteristic
     * This will signal the pod it can start sending back data
     * @return
     */
    @Suppress("DEPRECATION") fun readyToRead(): BleSendResult {
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

        const val DEFAULT_IO_TIMEOUT_MS = 1000.toLong()
    }
}

private fun Boolean.assertTrue(operation: String) {
    if (!this) {
        throw ConnectException("Could not $operation")
    }
}
