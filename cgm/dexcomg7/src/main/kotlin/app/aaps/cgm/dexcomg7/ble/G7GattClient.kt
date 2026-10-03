package app.aaps.cgm.dexcomg7.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import app.aaps.cgm.dexcomg7.protocol.G7Characteristic
import app.aaps.cgm.dexcomg7.protocol.G7Gatt
import app.aaps.cgm.dexcomg7.protocol.G7Link
import app.aaps.cgm.dexcomg7.protocol.G7LinkException
import app.aaps.core.utils.extensions.connectGattCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One GATT client for one sensor, as a [G7Link].
 *
 * Android allows one GATT operation at a time and reports the end of each on a callback. So every
 * write and every subscription holds [operationLock] until its callback arrives (or it times out).
 * Sending two writes together silently loses the second, and the handshake sends certificates in
 * 20-byte pieces where losing one means it never finishes. (Learned the hard way in Watch-APS.)
 *
 * A sensor does not keep a link open between readings. It advertises, delivers, and drops, about every
 * five minutes. So a disconnect is the normal case. [reconnect] asks the stack to connect again the
 * next time the sensor advertises. That is a background request the platform keeps for us, and it works
 * while the phone dozes, where a scan would find nothing.
 */
@SuppressLint("MissingPermission")
class G7GattClient(
    private val context: Context,
    val device: BluetoothDevice,
    /** Receives connection events. Can be replaced, which is how pairing hands the link to the session. */
    @Volatile var events: Events?,
    private val log: (String) -> Unit
) : G7Link {

    interface Events {

        /** Connected and services found. The CGM service may still be missing on a non-sensor. */
        fun onReady(client: G7GattClient)

        fun onDisconnected(client: G7GattClient, status: Int)
    }

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var connected = false
    @Volatile private var closed = false

    /** How the current client was opened. A plain re-arm keeps that mode, so [reconnect] checks it. */
    @Volatile private var autoConnect = true

    /**
     * Until then, normal connects and disconnects are not logged. The sensor opens a few short links
     * right after each reading; the session counts those and logs them as one line.
     */
    @Volatile var quietUntil = 0L

    private val listeners = ConcurrentHashMap<G7Characteristic, (ByteArray) -> Unit>()
    private val operationLock = Mutex()
    @Volatile private var pending: CompletableDeferred<Int>? = null

    override val address: String get() = device.address
    override val name: String? get() = runCatching { device.name }.getOrNull()
    override val isConnected: Boolean get() = connected

    val isBonded: Boolean get() = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)

    /**
     * Opens the client. [autoConnect] true is a standing request the stack completes when the sensor next
     * advertises, even while the phone sleeps. False connects now to a sensor that is advertising, which
     * is faster and is what pairing uses.
     */
    fun connect(autoConnect: Boolean) {
        closed = false
        this.autoConnect = autoConnect
        gatt = device.connectGattCompat(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) log("Bluetooth refused to open a connection to $address")
    }

    /**
     * Re-arms a client after a disconnect, so the stack connects when the sensor next advertises. The
     * client is not closed: closing drops that registration and leaves a scan as the only way back.
     *
     * A client opened with a direct connect (pairing, or after a scan) is replaced by a background one.
     * Re-arming it would repeat the direct connect, which gives up after about 10 seconds (status 147)
     * unless the sensor happens to advertise in that time. After a sensor change this cost many
     * readings and once 20 minutes of silence, until the watchdog opened a new client.
     */
    fun reconnect() {
        if (closed) return
        val g = gatt
        if (g == null || !autoConnect) {
            if (g != null) {
                log("Switching to a background connection request")
                runCatching { g.close() }
                gatt = null
            }
            return connect(autoConnect = true)
        }
        runCatching { g.connect() }.onFailure { log("Re-arming the connection failed: ${it.message}") }
    }

    fun disconnect() {
        runCatching { gatt?.disconnect() }
    }

    fun close() {
        closed = true
        connected = false
        failPending(STATUS_CLOSED)
        runCatching { gatt?.close() }
        gatt = null
        listeners.clear()
    }

    /** Removes the Android bond with this sensor. For a bond the sensor no longer knows. */
    fun removeBond() {
        runCatching { device.javaClass.getMethod("removeBond").invoke(device) }
            .onSuccess { log("Removed the phone's Bluetooth bond with $address") }
            .onFailure { log("Could not remove the Bluetooth bond: ${it.message}") }
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED    -> {
                    if (!isQuiet()) log("Connected to $address, finding services")
                    if (!gatt.discoverServices()) log("Could not start service discovery")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = connected
                    connected = false
                    failPending(STATUS_DISCONNECTED)
                    val normal = status == BluetoothGatt.GATT_SUCCESS || status == STATUS_REMOTE_ENDED
                    if ((wasConnected || status != BluetoothGatt.GATT_SUCCESS) && !(normal && isQuiet())) log("Disconnected from $address (status $status)")
                    events?.onDisconnected(this@G7GattClient, status)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed: $status")
                gatt.disconnect()
                return
            }
            connected = true
            events?.onReady(this@G7GattClient)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            deliver(characteristic.uuid, value)
        }

        @Deprecated("Used below API 33")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) deliver(characteristic.uuid, characteristic.value ?: return)
        }
    }

    private fun isQuiet(): Boolean = System.currentTimeMillis() < quietUntil

    private fun deliver(uuid: UUID, value: ByteArray) {
        val characteristic = G7Characteristic.fromUuid(uuid.toString()) ?: return
        listeners[characteristic]?.invoke(value.copyOf())
    }

    private fun failPending(status: Int) {
        pending?.complete(status)
    }

    override fun setListener(characteristic: G7Characteristic, listener: ((ByteArray) -> Unit)?) {
        if (listener == null) listeners.remove(characteristic) else listeners[characteristic] = listener
    }

    private fun characteristic(characteristic: G7Characteristic): BluetoothGattCharacteristic {
        val g = gatt ?: throw G7LinkException("Not connected")
        val service = g.getService(UUID.fromString(G7Gatt.CGM_SERVICE)) ?: throw G7LinkException("CGM service not found")
        return service.getCharacteristic(UUID.fromString(characteristic.uuid)) ?: throw G7LinkException("$characteristic not found")
    }

    /** True when the connected device serves the CGM service. */
    fun hasCgmService(): Boolean = gatt?.getService(UUID.fromString(G7Gatt.CGM_SERVICE)) != null

    override suspend fun enableNotifications(characteristic: G7Characteristic) {
        operationLock.withLock {
            val g = gatt ?: throw G7LinkException("Not connected")
            val target = characteristic(characteristic)
            if (!g.setCharacteristicNotification(target, true)) throw G7LinkException("Could not enable notifications for $characteristic")
            val descriptor = target.getDescriptor(UUID.fromString(G7Gatt.CLIENT_CHARACTERISTIC_CONFIG)) ?: throw G7LinkException("$characteristic has no configuration descriptor")
            // iOS picks indications or notifications by itself; here we have to choose.
            val value =
                if (target.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val status = issueAndWait(characteristic.name + " subscribe") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            }
            checkStatus(status, "Subscribing to $characteristic")
        }
    }

    override suspend fun write(characteristic: G7Characteristic, value: ByteArray, withResponse: Boolean) {
        operationLock.withLock {
            val g = gatt ?: throw G7LinkException("Not connected")
            val target = characteristic(characteristic)
            val writeType = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val status = issueAndWait("$characteristic write") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(target, value, writeType) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    target.writeType = writeType
                    @Suppress("DEPRECATION")
                    target.value = value
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(target)
                }
            }
            checkStatus(status, "Writing to $characteristic")
        }
    }

    override suspend fun onBondRequested() {
        // The sensor normally asks for bonding itself and Android follows. Start it if it has not.
        val state = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        if (state == BluetoothDevice.BOND_NONE) {
            log("Asking Android to bond with the sensor")
            runCatching { device.createBond() }
        }
    }

    /** Issues one operation and waits for its callback. Must be called with [operationLock] held. */
    private suspend fun issueAndWait(what: String, issue: () -> Boolean): Int {
        if (!connected) throw G7LinkException("Not connected")
        val deferred = CompletableDeferred<Int>()
        pending = deferred
        try {
            if (!issue()) throw G7LinkException("Bluetooth refused $what")
            return withTimeoutOrNull(OPERATION_TIMEOUT_MS) { deferred.await() } ?: throw G7LinkException("$what timed out")
        } finally {
            pending = null
        }
    }

    private fun checkStatus(status: Int, what: String) {
        when (status) {
            BluetoothGatt.GATT_SUCCESS                                                  -> Unit
            STATUS_DISCONNECTED, STATUS_CLOSED                                          -> throw G7LinkException("$what: link lost")
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_ENCRYPTION,
            GATT_AUTH_FAIL                                                              -> throw G7LinkException("$what failed: status $status", insufficientAuthentication = true)

            else                                                                        -> throw G7LinkException("$what failed: status $status")
        }
    }

    companion object {

        /** Longer than any operation takes, short enough that a lost callback does not stall a reading. */
        const val OPERATION_TIMEOUT_MS = 5_000L

        /** The sensor ended the link (HCI "remote user terminated"), the normal end of a reading. */
        private const val STATUS_REMOTE_ENDED = 19

        private const val STATUS_DISCONNECTED = -1
        private const val STATUS_CLOSED = -2
        private const val GATT_INSUFFICIENT_ENCRYPTION = 0x0f
        private const val GATT_AUTH_FAIL = 0x89
    }
}
