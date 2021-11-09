@file:Suppress("WildcardImport")

package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import info.nightscout.androidaps.extensions.toHex
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.BleCommCallbacks
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.WriteConfirmationError
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.callbacks.WriteConfirmationSuccess
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommandRTS
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

sealed class BleSendResult

object BleSendSuccess : BleSendResult()
data class BleSendErrorSending(val msg: String, val cause: Throwable? = null) : BleSendResult()
data class BleSendErrorConfirming(val msg: String, val cause: Throwable? = null) : BleSendResult()

open class BleIO(
    private val aapsLogger: AAPSLogger,
    var characteristic: BluetoothGattCharacteristic,
    private val incomingPackets: BlockingQueue<ByteArray>,
    private val gatt: BluetoothGatt,
    private val bleCommCallbacks: BleCommCallbacks,
    private val type: CharacteristicType
) {

    /***
     *
     * @param characteristic where to read from(CMD or DATA)
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
     * @param characteristic where to write to(CMD or DATA)
     * @param payload the data to send
     */
    @Suppress("ReturnCount")
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
            is WriteConfirmationError ->
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
    fun readyToRead(): BleSendResult {
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
            is WriteConfirmationError ->
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
