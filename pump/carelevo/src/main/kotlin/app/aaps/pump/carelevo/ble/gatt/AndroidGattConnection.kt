package app.aaps.pump.carelevo.ble.gatt

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Production [GattConnection] that wraps Android's [BluetoothGatt] + [BluetoothGattCallback].
 *
 * **Status: skeleton — requires hardware validation before production use.** The class
 * covers the happy paths for write / discover / enable-notifications and emits every
 * callback into [events]. It deliberately does **not** yet include:
 * - Connection retry / reconnection strategy (caller's policy)
 * - MTU negotiation
 * - Bonding / pairing coordination (use [BluetoothDevice.createBond] separately)
 * - Gatt refresh on abnormal disconnect (see `reflectiveRefresh()` in the legacy manager)
 * - Vendor quirks (Samsung looper affinity, Mediatek connectGatt timing, etc.)
 *
 * Permissions (`BLUETOOTH_CONNECT` on API 31+) are the caller's responsibility — this
 * class suppresses `MissingPermission` under the assumption that the consumer has
 * already checked permissions before constructing it.
 *
 * Threading model:
 * - Android delivers [BluetoothGattCallback] calls on a binder thread.
 * - The callback marshals each event onto [scope] via `launch { _events.emit(...) }` so
 *   downstream collectors observe events on the scope's dispatcher.
 * - `CompletableDeferred.complete(...)` is thread-safe and is called directly from the
 *   binder callback — no need to marshal first.
 * - [gattMutex] serializes suspend operations on the GATT (Android's BLE stack accepts
 *   only one in-flight op per connection).
 */
@SuppressLint("MissingPermission")
class AndroidGattConnection private constructor(
    private val scope: CoroutineScope
) : GattConnection {

    private val _events = MutableSharedFlow<GattEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<GattEvent> = _events.asSharedFlow()

    private val gattMutex = Mutex()

    /** Completes when the current [writeCharacteristic] call gets its `onCharacteristicWrite`. */
    @Volatile
    private var writeAck: CompletableDeferred<Boolean>? = null

    /** Completes when the current [discoverServices] call gets its `onServicesDiscovered`. */
    @Volatile
    private var discoveryAck: CompletableDeferred<Boolean>? = null

    /** Completes when an `enableNotifications` CCCD write gets its `onDescriptorWrite`. */
    @Volatile
    private var descriptorAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var gatt: BluetoothGatt? = null

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
            val state = when (newState) {
                BluetoothProfile.STATE_CONNECTING    -> GattConnState.CONNECTING
                BluetoothProfile.STATE_CONNECTED     -> GattConnState.CONNECTED
                BluetoothProfile.STATE_DISCONNECTING -> GattConnState.DISCONNECTING
                BluetoothProfile.STATE_DISCONNECTED  -> GattConnState.DISCONNECTED
                else                                 -> return
            }
            scope.launch { _events.emit(GattEvent.ConnectionStateChanged(state)) }
            if (state == GattConnState.DISCONNECTED) {
                // Abort any in-flight operations — the connection is gone.
                writeAck?.completeExceptionally(GattWriteException("disconnected"))
                discoveryAck?.completeExceptionally(GattDiscoveryException("disconnected"))
                descriptorAck?.completeExceptionally(GattDiscoveryException("disconnected"))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
            val ok = status == BluetoothGatt.GATT_SUCCESS
            discoveryAck?.complete(ok)
            scope.launch { _events.emit(GattEvent.ServicesDiscovered(ok)) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // API 33+ signature — value is delivered directly.
            val copy = value.copyOf()
            scope.launch { _events.emit(GattEvent.Notification(characteristic.uuid, copy)) }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Pre-API 33 signature; kept for older devices")
        override fun onCharacteristicChanged(
            g: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val c = characteristic ?: return
            val value = c.value?.copyOf() ?: return
            scope.launch { _events.emit(GattEvent.Notification(c.uuid, value)) }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val ok = status == BluetoothGatt.GATT_SUCCESS
            writeAck?.complete(ok)
            characteristic?.uuid?.let { uuid ->
                scope.launch { _events.emit(GattEvent.WriteAck(uuid, ok)) }
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            descriptorAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    // ===== GattConnection implementation =====

    override suspend fun writeCharacteristic(
        uuid: UUID,
        payload: ByteArray,
        withResponse: Boolean
    ) = gattMutex.withLock {
        val g = gatt ?: throw GattWriteException("gatt is null (closed or not connected)")
        val characteristic = g.findCharacteristic(uuid)
            ?: throw GattWriteException("characteristic $uuid not found — call discoverServices first")

        val writeType = if (withResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        val deferred = CompletableDeferred<Boolean>()
        writeAck = deferred
        try {
            val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = writeType
                    characteristic.value = payload
                    g.writeCharacteristic(characteristic)
                }
            }
            if (!queued) throw GattWriteException("BLE stack rejected the write (queue full or busy)")

            val acked = deferred.await()
            if (!acked) throw GattWriteException("onCharacteristicWrite reported non-success status")
        } finally {
            writeAck = null
        }
    }

    override suspend fun discoverServices() = gattMutex.withLock {
        val g = gatt ?: throw GattDiscoveryException("gatt is null (closed or not connected)")
        val deferred = CompletableDeferred<Boolean>()
        discoveryAck = deferred
        try {
            if (!g.discoverServices()) throw GattDiscoveryException("BLE stack rejected discoverServices")
            val ok = deferred.await()
            if (!ok) throw GattDiscoveryException("onServicesDiscovered reported non-success status")
        } finally {
            discoveryAck = null
        }
    }

    override suspend fun enableNotifications(uuid: UUID) = gattMutex.withLock {
        val g = gatt ?: throw GattDiscoveryException("gatt is null (closed or not connected)")
        val characteristic = g.findCharacteristic(uuid)
            ?: throw GattDiscoveryException("characteristic $uuid not found — call discoverServices first")

        if (!g.setCharacteristicNotification(characteristic, true)) {
            throw GattDiscoveryException("setCharacteristicNotification failed")
        }

        val descriptor = characteristic.getDescriptor(CCCD_UUID)
            ?: throw GattDiscoveryException("no CCCD descriptor on characteristic $uuid")

        val deferred = CompletableDeferred<Boolean>()
        descriptorAck = deferred
        try {
            val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(descriptor)
                }
            }
            if (!queued) throw GattDiscoveryException("BLE stack rejected the descriptor write")

            val ok = deferred.await()
            if (!ok) throw GattDiscoveryException("onDescriptorWrite reported non-success status")
        } finally {
            descriptorAck = null
        }
    }

    override fun close() {
        val g = gatt
        gatt = null
        // Abort any in-flight suspending operations — BluetoothGatt.close() does not
        // guarantee a final onConnectionStateChange callback on every chipset, so
        // callers could otherwise hang on deferred.await() forever.
        writeAck?.completeExceptionally(GattWriteException("connection closed"))
        discoveryAck?.completeExceptionally(GattDiscoveryException("connection closed"))
        descriptorAck?.completeExceptionally(GattDiscoveryException("connection closed"))
        g?.close()
    }

    private fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        for (service in services) {
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    companion object {

        /** Bluetooth SIG-assigned UUID for the Client Characteristic Configuration Descriptor. */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Create a connection to [device] and return the bound [AndroidGattConnection].
         *
         * The returned object is usable immediately, but callers typically wait for
         * [GattEvent.ConnectionStateChanged]`(CONNECTED)` via [events] before calling
         * [discoverServices].
         *
         * @param context application context used for `connectGatt`.
         * @param device the remote peripheral.
         * @param scope coroutine scope that owns the event flow; its lifetime bounds
         *              the connection's event delivery.
         * @param autoConnect passed through to [BluetoothDevice.connectGatt]. `false`
         *                    (direct connect) is typical for first-time connections;
         *                    `true` enables Android's background reconnection.
         */
        fun connect(
            context: Context,
            device: BluetoothDevice,
            scope: CoroutineScope,
            autoConnect: Boolean = false
        ): AndroidGattConnection {
            val conn = AndroidGattConnection(scope)
            conn.gatt = device.connectGatt(
                context.applicationContext,
                autoConnect,
                conn.callback,
                BluetoothDevice.TRANSPORT_LE
            )
            return conn
        }
    }
}
