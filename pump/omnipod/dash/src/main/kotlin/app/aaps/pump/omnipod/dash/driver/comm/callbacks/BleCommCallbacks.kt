package app.aaps.pump.omnipod.dash.driver.comm.callbacks

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.io.CharacteristicType.Companion.byValue
import app.aaps.pump.omnipod.dash.driver.comm.io.IncomingPackets
import app.aaps.pump.omnipod.dash.driver.comm.session.DisconnectHandler
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BleCommCallbacks(
    private val aapsLogger: AAPSLogger,
    private val incomingPackets: IncomingPackets,
    private val disconnectHandler: DisconnectHandler,
) : BluetoothGattCallback() {

    // Synchronized because they can be:
    // - read from various callbacks
    // - written from resetConnection that is called onConnectionLost
    private var serviceDiscoveryComplete: CountDownLatch = CountDownLatch(1)
        @Synchronized get
        @Synchronized set
    private var connected: CountDownLatch = CountDownLatch(1)
        @Synchronized get
        @Synchronized set
    private val writeQueue: BlockingQueue<WriteConfirmation> = LinkedBlockingQueue()

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "OnConnectionStateChange with status/state: $status/$newState")
        super.onConnectionStateChange(gatt, status, newState)
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            connected.countDown()
        }
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // If status == SUCCESS, it means that we initiated the disconnect.
            disconnectHandler.onConnectionLost(status)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "OnServicesDiscovered with status: $status")
        super.onServicesDiscovered(gatt, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            serviceDiscoveryComplete.countDown()
        }
    }

    fun waitForConnection(timeoutMs: Long): Boolean {
        val latch = connected
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while waiting for Connection")
        }
        return latch.count == 0L
    }

    fun startServiceDiscovery() {
        serviceDiscoveryComplete = CountDownLatch(1)
    }

    fun waitForServiceDiscovery(timeoutMs: Long): Boolean {
        val latch = serviceDiscoveryComplete
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while waiting for ServiceDiscovery")
        }
        return latch.count == 0L
    }

    fun confirmWrite(expectedPayload: ByteArray, expectedUUID: String, timeoutMs: Long): WriteConfirmation {
        try {
            return when (val received = writeQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)) {
                null                        -> WriteConfirmationError("Timeout waiting for writeConfirmation")
                is WriteConfirmationSuccess ->
                    if (expectedPayload.contentEquals(received.payload) &&
                        expectedUUID == received.uuid
                    ) {
                        received
                    } else {
                        aapsLogger.warn(
                            LTag.PUMPBTCOMM,
                            "Could not confirm write. Got " + received.payload.toHex() +
                                ".Excepted: " + expectedPayload.toHex()
                        )
                        WriteConfirmationError("Received incorrect writeConfirmation")
                    }

                is WriteConfirmationError   ->
                    received
            }
        } catch (e: InterruptedException) {
            return WriteConfirmationError("Interrupted waiting for confirmation")
        }
    }

    @Suppress("DEPRECATION")
    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "OnCharacteristicWrite with char/status " +
                "${characteristic.uuid} /" +
                "$status"
        )
        super.onCharacteristicWrite(gatt, characteristic, status)

        onWrite(status, characteristic.uuid, characteristic.value)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
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

        val insertResult = incomingPackets.byCharacteristicType(characteristicType).add(payload)
        if (!insertResult) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Could not insert read data to the incoming queue: $characteristicType")
        }
    }

    @Suppress("DEPRECATION")
    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status)

        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "OnDescriptorWrite with descriptor/status " +
                descriptor.uuid.toString() + "/" +
                status + "/"
        )

        onWrite(status, descriptor.uuid, descriptor.value)
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "onMtuChanged with MTU/status: $mtu/$status "
        )
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        super.onReadRemoteRssi(gatt, rssi, status)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "onReadRemoteRssi with rssi/status: $rssi/$status "
        )
    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status)
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "onPhyUpdate with txPhy/rxPhy/status: $txPhy/$rxPhy/$status "
        )
    }

    private fun onWrite(status: Int, uuid: UUID?, value: ByteArray?) {
        val writeConfirmation = when {
            uuid == null || value == null        ->
                WriteConfirmationError("onWrite received Null: UUID=$uuid, value=${value?.toHex()} status=$status")

            status == BluetoothGatt.GATT_SUCCESS -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "OnWrite value " + value.toHex())
                WriteConfirmationSuccess(uuid.toString(), value)
            }

            else                                 -> WriteConfirmationError("onDescriptorWrite status is not success: $status")
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
        if (writeQueue.isNotEmpty()) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "Write queue should be empty, found: ${writeQueue.size}"
            )
            writeQueue.clear()
        }
    }

    fun resetConnection() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Reset connection")
        connected.countDown()
        serviceDiscoveryComplete.countDown()
        connected = CountDownLatch(1)
        serviceDiscoveryComplete = CountDownLatch(1)
        flushConfirmationQueue()
    }

    companion object {

        private const val WRITE_CONFIRM_TIMEOUT_MS = 10 // the confirmation queue should be empty anyway
    }
}
