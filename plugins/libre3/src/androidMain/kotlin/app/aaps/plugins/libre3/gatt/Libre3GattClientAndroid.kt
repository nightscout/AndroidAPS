package app.aaps.plugins.libre3.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import app.aaps.plugins.libre3.Libre3Log
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.Libre3LogMask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The real Bluetooth link.
 *
 * ⚠️ ASYNC IMPACT: every method of [BluetoothGattCallback] arrives on a binder thread of the
 * system. Only one write or one descriptor change may be in flight at a time, so each one is
 * started and then waited for. The waiting methods block the calling thread on purpose, and that
 * thread must be the driver's own executor, never the main thread and never the NFC thread.
 *
 * This class has no method that writes to the sensor's control channel, and it must never get one
 * in this version. See `Libre3DisconnectPolicy`.
 */
@SuppressLint("MissingPermission")
class Libre3GattClientAndroid(context: Context) : Libre3GattClient {

    private val appContext = context.applicationContext

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var connected = false

    /**
     * Pieces exactly as the sensor sent them, one queue per channel.
     *
     * Nothing is put together here. A piece that arrives before anybody waits for it is kept, so
     * a message sent right after a command answer cannot be lost. Whoever waits decides what to do
     * with the pieces, because the three kinds of channel need three different things.
     */
    private val inboxes = ConcurrentHashMap<String, LinkedBlockingQueue<ByteArray>>()

    /** One mixed stream for the seven channels of a running session. */
    private val dataPlaneInbox = LinkedBlockingQueue<Pair<String, ByteArray>>()

    @Volatile
    private var operationLatch: CountDownLatch? = null

    @Volatile
    private var operationOk = false

    /**
     * How many answers are still owed by operations that already gave up waiting.
     *
     * Android answers in the order it was asked, so the first answer after a wait ran out belongs
     * to the operation that gave up. Without this count that late answer would land on the next
     * operation and finish it early, with the old result. During a handshake that means a step
     * reported as done when the sensor never confirmed it.
     */
    private val abandonedAnswers = AtomicInteger(0)

    /** True once the link is gone. Anything that starts waiting after that gives up at once. */
    @Volatile
    private var linkDown = false

    /** True when [disconnect] ran during a search or a connect, so this attempt must stop. */
    @Volatile
    private var cancelled = false

    /** Counted down when a search should stop, so a newer connect is not stuck behind it. */
    @Volatile
    private var scanLatch: CountDownLatch? = null

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Libre3Log.i("${Libre3LogMarkers.SESSION}: link up, discovering channels")
                gatt.discoverServices()
            } else {
                connected = false
                linkDown = true
                Libre3Log.i("${Libre3LogMarkers.SESSION}: link down, status=$status")
                // Everything that is waiting must be woken, otherwise the driver thread would sit
                // on a queue that can never fill again.
                releaseWaiters()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            connected = status == BluetoothGatt.GATT_SUCCESS
            Libre3Log.i("${Libre3LogMarkers.SESSION}: channels discovered, ok=$connected")
            finishOperation(connected)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            finishOperation(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            finishOperation(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Kept for Android 12 and older, which do not call the newer method.")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onPiece(Libre3GattUuids.textOf(characteristic.uuid), characteristic.value ?: ByteArray(0))
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onPiece(Libre3GattUuids.textOf(characteristic.uuid), value)
        }
    }

    override fun connect(deviceAddress: String) {
        cancelled = false
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        // LibreLoop connects the device that was just seen on the air. A connect by stored MAC
        // alone is the classic Android 147 on this phone: the stack never finds the sensor.
        val scanned = awaitAdvertisedDevice(adapter, deviceAddress, SCAN_TIMEOUT_MS)
        if (cancelled) return
        val device = scanned ?: try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (_: IllegalArgumentException) {
            Libre3Log.e("${Libre3LogMarkers.ERROR}: stored address is not a Bluetooth address")
            return
        }
        if (scanned != null) {
            Libre3Log.i("${Libre3LogMarkers.SCAN}: sensor seen, connecting that device")
            sleepQuiet(SCAN_HANDOFF_MS)
            if (cancelled) return
        } else {
            Libre3Log.w("${Libre3LogMarkers.SCAN}: sensor was not seen, connecting by stored address")
        }
        inboxes.clear()
        dataPlaneInbox.clear()
        linkDown = false
        abandonedAnswers.set(0)
        startOperation()
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        // Discovery answers this wait, so the caller returns only once the channels are known.
        waitForOperation(CONNECT_TIMEOUT_MS)
    }

    /**
     * Waits until the stored sensor advertises, then returns that [BluetoothDevice].
     *
     * The filter is the address only. Adding the service UUID as well matches nothing when the
     * UUID lives in the scan response, which is how a Libre 3 often advertises.
     */
    private fun awaitAdvertisedDevice(
        adapter: BluetoothAdapter,
        address: String,
        timeoutMs: Long,
    ): BluetoothDevice? {
        val scanner = adapter.bluetoothLeScanner ?: return null
        val found = AtomicReference<BluetoothDevice?>(null)
        val latch = CountDownLatch(1)
        scanLatch = latch
        val callback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                if (!device.address.equals(address, ignoreCase = true)) return
                if (found.compareAndSet(null, device)) {
                    Libre3Log.i("${Libre3LogMarkers.SCAN}: seen ${Libre3LogMask.mac(device.address)} rssi=${result.rssi}")
                    latch.countDown()
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Libre3Log.w("${Libre3LogMarkers.SCAN}: the search failed, code=$errorCode")
                latch.countDown()
            }
        }
        val filter = try {
            ScanFilter.Builder().setDeviceAddress(address).build()
        } catch (_: IllegalArgumentException) {
            Libre3Log.e("${Libre3LogMarkers.ERROR}: stored address is not a Bluetooth address")
            return null
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            Libre3Log.i("${Libre3LogMarkers.SCAN}: looking for ${Libre3LogMask.mac(address)}")
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Libre3Log.w("${Libre3LogMarkers.SCAN}: the search threw ${e.javaClass.simpleName}")
        } finally {
            scanLatch = null
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                Libre3Log.w("${Libre3LogMarkers.SCAN}: stopping the search failed, ${e.javaClass.simpleName}")
            }
        }
        return found.get()
    }

    private fun sleepQuiet(durationMs: Long) {
        if (durationMs <= 0L) return
        try {
            Thread.sleep(durationMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun disconnect() {
        // Link level only. No command is ever written to the sensor here.
        cancelled = true
        scanLatch?.countDown()
        val current = gatt
        gatt = null
        connected = false
        linkDown = true
        try {
            current?.disconnect()
            current?.close()
        } catch (e: Exception) {
            Libre3Log.w("${Libre3LogMarkers.SESSION}: closing the link failed, ${e.javaClass.simpleName}")
        }
        releaseWaiters()
    }

    override fun isConnected(): Boolean = connected

    /**
     * Turns a channel on or off.
     *
     * The kind of message is chosen from what the channel itself offers. A channel that offers
     * confirmed messages must be asked for confirmed messages: asking for the other kind leaves
     * the link open while nothing ever arrives. That mistake cost a long hunt on the Dexcom ONE+
     * work in this same project, so it is spelled out here.
     */
    override fun setNotify(characteristic: String, enabled: Boolean): Boolean {
        val gattRef = gatt ?: return false
        val target = findCharacteristic(gattRef, characteristic) ?: return false
        if (!gattRef.setCharacteristicNotification(target, enabled)) return false
        val descriptor = target.getDescriptor(Libre3GattUuids.CLIENT_CHARACTERISTIC_CONFIG) ?: return false
        val wantsConfirmed =
            target.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 &&
                target.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
        val value = when {
            !enabled       -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            wantsConfirmed -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else           -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        startOperation()
        val started = writeDescriptorCompat(gattRef, descriptor, value)
        if (!started) return false
        return waitForOperation(OPERATION_TIMEOUT_MS)
    }

    override fun write(characteristic: String, payload: ByteArray) {
        // One piece at a time. Android refuses a second write while the first is still running.
        for (piece in Libre3BleFraming.fragmentForWrite(payload)) writeOnce(characteristic, piece)
    }

    override fun writeRaw(characteristic: String, payload: ByteArray) {
        writeOnce(characteristic, payload)
    }

    private fun writeOnce(characteristic: String, bytes: ByteArray) {
        val gattRef = gatt ?: return
        val target = findCharacteristic(gattRef, characteristic) ?: return
        startOperation()
        if (!writeCharacteristicCompat(gattRef, target, bytes)) return
        waitForOperation(OPERATION_TIMEOUT_MS)
    }

    override fun awaitNotifyRaw(characteristic: String, timeoutMs: Long): ByteArray? =
        nextPiece(characteristic, timeoutMs)

    override fun awaitNotify(characteristic: String, exactly: Int, timeoutMs: Long): ByteArray? {
        // A fresh holder per message, exactly like the upstream code. A message that was cut short
        // by a lost link must not leave anything behind for the next one.
        val assembler = Libre3BleFraming.NotifyReassembler()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) return null
            val piece = nextPiece(characteristic, remaining) ?: return null
            if (piece.isEmpty()) return null
            try {
                assembler.feed(piece)
            } catch (e: Libre3FramingException) {
                // A piece was lost, so anything built from here would be wrong. The rest of that
                // message is left in the queue, which is harmless: the failure ends the session
                // attempt, and the next attempt builds a new client with empty queues.
                Libre3Log.w("${Libre3LogMarkers.SESSION}: ${e.message}")
                return null
            }
            if (assembler.availableBytes >= exactly) return assembler.take(exactly)
        }
    }

    override fun awaitDataPlaneNotify(timeoutMs: Long): Pair<String, ByteArray>? {
        if (linkDown) return null
        return try {
            dataPlaneInbox.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private fun nextPiece(characteristic: String, timeoutMs: Long): ByteArray? {
        // A wait that starts after the link is already gone would otherwise sit for the whole
        // timeout on a queue that nothing can ever fill.
        if (linkDown) return null
        val inbox = inboxes.getOrPut(characteristic) { LinkedBlockingQueue() }
        return try {
            inbox.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    /**
     * Keeps one arriving piece exactly as it came.
     *
     * Nothing is put together here on purpose. The command channel answers with one piece whose
     * first byte is the answer itself, the certificate and challenge channels send pieces that
     * carry a counter, and the data channels send pieces whose length is part of the meaning. One
     * rule for all three would break two of them.
     */
    private fun onPiece(characteristic: String, piece: ByteArray) {
        if (characteristic in Libre3BluetoothUuids.DATA_PLANE_CHANNELS) {
            dataPlaneInbox.offer(characteristic to piece)
            return
        }
        inboxes.getOrPut(characteristic) { LinkedBlockingQueue() }.offer(piece)
    }

    private fun findCharacteristic(gattRef: BluetoothGatt, uuid: String): BluetoothGattCharacteristic? {
        val platformUuid = Libre3GattUuids.uuidOf(uuid)
        for (service in gattRef.services) {
            service.getCharacteristic(platformUuid)?.let { return it }
        }
        Libre3Log.w("${Libre3LogMarkers.SESSION}: the sensor has no channel $uuid")
        return null
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(
        gattRef: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattRef.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gattRef.writeDescriptor(descriptor)
        }

    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(
        gattRef: BluetoothGatt,
        target: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattRef.writeCharacteristic(target, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            target.value = value
            target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gattRef.writeCharacteristic(target)
        }

    private fun startOperation() {
        operationOk = false
        operationLatch = CountDownLatch(1)
    }

    private fun finishOperation(ok: Boolean) {
        // An answer that is owed to an operation which already gave up is swallowed here, so it
        // cannot finish the operation that is running now.
        if (abandonedAnswers.get() > 0) {
            abandonedAnswers.decrementAndGet()
            return
        }
        if (linkDown) return
        operationOk = ok
        operationLatch?.countDown()
    }

    private fun waitForOperation(timeoutMs: Long): Boolean {
        val latch = operationLatch ?: return false
        return try {
            val answered = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            operationLatch = null
            if (!answered) {
                // One owed answer per step that gave up, and never more than the number of steps
                // that can still be in flight. Letting this grow without bound would swallow the
                // answers of every later operation and kill the session for good.
                if (abandonedAnswers.get() < MAX_OWED_ANSWERS) abandonedAnswers.incrementAndGet()
                false
            } else {
                operationOk
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /**
     * Wakes everything that waits, so a dropped link never leaves a thread stuck.
     *
     * A wait that has not started yet is covered by [linkDown], which is set before this runs.
     */
    private fun releaseWaiters() {
        // Without this the wait would end and hand back the result of the **previous** operation,
        // so a link that dropped in the middle of opening the data channels would be read as
        // success and the session would be declared up on a dead link.
        operationOk = false
        operationLatch?.countDown()
        inboxes.values.forEach { it.offer(LINK_LOST) }
        dataPlaneInbox.offer(LINK_LOST_EVENT)
    }

    companion object {

        const val CONNECT_TIMEOUT_MS = 30_000L

        /** How long to look for the sensor on the air before falling back to the stored address. */
        const val SCAN_TIMEOUT_MS = 20_000L

        /** Pause after the scan stops, so the radio can switch to a connect. */
        const val SCAN_HANDOFF_MS = 400L

        /**
         * How long one write or one channel change may take.
         *
         * The upstream project measured about 1.6 seconds for a single answer on a real phone and
         * settled on 15 seconds, so a slow phone does not fail a handshake that would have worked.
         */
        const val OPERATION_TIMEOUT_MS = 15_000L

        /** Upper bound on answers still owed by steps that gave up waiting. */
        const val MAX_OWED_ANSWERS = 2

        /** Put into every inbox when the link drops, so a waiting thread wakes up at once. */
        private val LINK_LOST = ByteArray(0)

        /** The same wake-up for the mixed stream of a running session. */
        private val LINK_LOST_EVENT = Libre3BluetoothUuids.GLUCOSE_DATA to LINK_LOST
    }
}
