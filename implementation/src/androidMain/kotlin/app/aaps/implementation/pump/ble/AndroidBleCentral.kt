package app.aaps.implementation.pump.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleBonding
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleException
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleLinkPort
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.ConnectOptions
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.GattServices
import app.aaps.core.interfaces.pump.ble.LinkPriority
import app.aaps.core.interfaces.pump.ble.QueuedBleLink
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.pump.ble.Subscription
import app.aaps.core.interfaces.pump.ble.WriteType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import android.bluetooth.le.ScanFilter as SystemScanFilter

/**
 * The [BleCentral] on Android Bluetooth. Each link is a [QueuedBleLink] on one [BluetoothGatt]; this class only turns
 * the GATT callbacks into the ends of its operations.
 *
 * Nothing is done at construction: the graph is built in tests and at app start, before Bluetooth is needed.
 */
// Every entry point checks the permissions first, in unavailable(), and turns a SecurityException into a failure.
@SuppressLint("MissingPermission")
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidBleCentral(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : BleCentral {

    private val manager: BluetoothManager? by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    /** Made on first use, with the receiver that keeps it current. */
    private val adapterStates: MutableStateFlow<AdapterState> by lazy {
        val states = MutableStateFlow(readAdapterState())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                states.value = readAdapterState()
            }
        }
        registerBluetoothReceiver(receiver, BluetoothAdapter.ACTION_STATE_CHANGED)
        states
    }

    /**
     * Registers a receiver for a broadcast of the Bluetooth stack. It must be EXPORTED. The Bluetooth process sends
     * these broadcasts with its own uid (1002), not the system uid, and Android gives a not-exported receiver only
     * the broadcasts of the system and of this app. With a not-exported bond receiver a real phone never reported
     * BONDED, so a pairing waited until its time limit while the patch had already been bonded. No other app can
     * send these actions, because they are protected broadcasts. Robolectric does not apply this rule, so a test
     * can only check the flag.
     */
    private fun registerBluetoothReceiver(receiver: BroadcastReceiver, action: String) {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
    }

    /** Kept current by the system's broadcast. A permission change sends none, so each scan and connect reads it again. */
    override val adapterState: StateFlow<AdapterState> get() = adapterStates

    override val bonding: BleBonding = AndroidBonding()

    /**
     * How often a bond wait reads the system's state, well inside the 2.4 s a Carelevo patch waits after the bond.
     * A test makes it long to see what the broadcasts alone do.
     */
    internal var bondReadInterval: Duration = 100.milliseconds

    private val scanning = Mutex()

    override fun scan(filter: ScanFilter): Flow<Advertisement> = flow {
        scanning.withLock { emitAll(scanOnce(filter)) }
    }

    private fun scanOnce(filter: ScanFilter): Flow<Advertisement> = callbackFlow {
        unavailable(scan = true)?.let { throw BleException(it) }
        val scanner = adapter?.bluetoothLeScanner ?: throw BleException(BleFailure(BleFailure.Kind.BLUETOOTH_OFF, message = "no scanner"))
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = found(result)

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { found(it) }

            override fun onScanFailed(errorCode: Int) {
                close(BleException(BleFailure(BleFailure.Kind.REFUSED, errorCode, "the scan did not start")))
            }

            private fun found(result: ScanResult) {
                val advertisement = advertisementOf(result)
                if (filter.matches(advertisement)) trySend(advertisement)
            }
        }
        try {
            scanner.startScan(systemFilters(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (e: SecurityException) {
            throw BleException(BleFailure(BleFailure.Kind.NOT_AUTHORIZED, message = e.message))
        }
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                // Bluetooth switched off during the scan: the system has stopped it already.
                aapsLogger.debug(LTag.PUMPBTCOMM, "BLE scan stop: ${e.message}")
            }
        }
    }

    /**
     * The part of [filter] the system can check itself. Android stops a scan with no filter while the screen is off, so
     * a scan in the background needs [ScanFilter.services] or [ScanFilter.manufacturerId]. The name prefix is checked
     * here only: the system compares whole names.
     */
    private fun systemFilters(filter: ScanFilter): List<SystemScanFilter>? {
        fun builder() = SystemScanFilter.Builder().apply {
            // No data: any data of this company matches.
            filter.manufacturerId?.let { setManufacturerData(it, ByteArray(0)) }
        }
        return when {
            filter.services.isNotEmpty()  -> filter.services.map { builder().setServiceUuid(ParcelUuid(UUID.fromString(it.value))).build() }
            filter.manufacturerId != null -> listOf(builder().build())
            else                          -> null
        }
    }

    private fun advertisementOf(result: ScanResult): Advertisement {
        val record = result.scanRecord
        val manufacturer = record?.manufacturerSpecificData
        return Advertisement(
            device = DeviceId(result.device.address),
            name = record?.deviceName ?: nameOf(result.device),
            rssi = result.rssi,
            serviceUuids = record?.serviceUuids.orEmpty().map { BleUuid.of(it.uuid.toString()) },
            manufacturerData = buildMap { if (manufacturer != null) for (i in 0 until manufacturer.size()) put(manufacturer.keyAt(i), manufacturer.valueAt(i)) },
            serviceData = record?.serviceData.orEmpty().entries.associate { BleUuid.of(it.key.uuid.toString()) to it.value }
        )
    }

    private fun nameOf(device: BluetoothDevice): String? =
        try {
            device.name
        } catch (_: SecurityException) {
            null
        }

    override suspend fun connect(device: DeviceId, options: ConnectOptions): BleResult<BleLink> {
        unavailable(scan = false)?.let { return BleResult.Failed(it) }
        val remote = remote(device) ?: return notAnAddress(device)
        val connection = Connection(device)
        val result = try {
            withTimeoutOrNull(options.timeout) { connection.open(remote, options) }
                ?: BleResult.Failed(BleFailure(BleFailure.Kind.TIMEOUT, message = "$device did not connect in ${options.timeout}"))
        } catch (e: CancellationException) {
            connection.release()
            throw e
        } catch (e: SecurityException) {
            BleResult.Failed(BleFailure(BleFailure.Kind.NOT_AUTHORIZED, message = e.message))
        }
        if (result is BleResult.Failed) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "BLE $device: connect failed: ${result.failure}")
            connection.release()
        }
        return result
    }

    private fun remote(device: DeviceId): BluetoothDevice? =
        try {
            adapter?.getRemoteDevice(device.value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun <T> notAnAddress(device: DeviceId): BleResult<T> =
        BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "not a Bluetooth address: ${device.value}"))

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun readAdapterState(): AdapterState {
        val adapter = adapter
        return when {
            adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) -> AdapterState.UNSUPPORTED
            !granted(Manifest.permission.BLUETOOTH_CONNECT)                                                   -> AdapterState.UNAUTHORIZED
            adapter.isEnabled                                                                                 -> AdapterState.ON
            else                                                                                              -> AdapterState.OFF
        }
    }

    /** Why nothing can be done now, or null. Also brings [adapterState] up to date. */
    private fun unavailable(scan: Boolean): BleFailure? {
        val state = readAdapterState()
        adapterStates.value = state
        return when (state) {
            AdapterState.ON           -> if (scan && !granted(Manifest.permission.BLUETOOTH_SCAN)) BleFailure(BleFailure.Kind.NOT_AUTHORIZED, message = "no BLUETOOTH_SCAN") else null
            AdapterState.UNAUTHORIZED -> BleFailure(BleFailure.Kind.NOT_AUTHORIZED, message = "no BLUETOOTH_CONNECT")
            AdapterState.UNSUPPORTED  -> BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "no Bluetooth LE")
            AdapterState.OFF,
            AdapterState.UNKNOWN      -> BleFailure(BleFailure.Kind.BLUETOOTH_OFF)
        }
    }

    /**
     * One [BluetoothGatt]: first the connect and the discovery, then the port of its [QueuedBleLink]. Android calls back
     * on its own threads; [QueuedBleLink] takes the ends from any thread.
     */
    private inner class Connection(private val device: DeviceId) : BluetoothGattCallback(), BleLinkPort {

        private val lock = Any()
        @Volatile private var gatt: BluetoothGatt? = null
        private var link: QueuedBleLink? = null
        private var lostBeforeTheLink: BleFailure? = null
        private var released = false
        @Volatile private var mtu = DEFAULT_MTU

        /** Null when connected; else why not. */
        private val connected = CompletableDeferred<BleFailure?>()
        private val discovered = CompletableDeferred<BleFailure?>()

        suspend fun open(remote: BluetoothDevice, options: ConnectOptions): BleResult<BleLink> {
            val gatt = remote.connectGatt(context, options.androidAutoConnect, this, BluetoothDevice.TRANSPORT_LE)
                ?: return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "the system gave no GATT"))
            this.gatt = gatt
            connected.await()?.let { return BleResult.Failed(it) }
            if (options.androidBondBeforeDiscovery) {
                // The outer time limit of the connect ends the wait; this one only has to be longer.
                val bonded = bonding.createBond(device, options.timeout)
                if (bonded is BleResult.Failed) return bonded
            }
            if (!gatt.discoverServices()) return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "the service discovery did not start"))
            discovered.await()?.let { return BleResult.Failed(it) }
            val link = QueuedBleLink(device, servicesOf(gatt), this, aapsLogger)
            // The device may have gone after the discovery, when there was no link yet to tell.
            val lost = synchronized(lock) {
                this.link = link
                lostBeforeTheLink
            }
            if (lost != null) return BleResult.Failed(lost)
            aapsLogger.debug(LTag.PUMPBTCOMM, "BLE $device: connected, ${link.services.characteristics.size} characteristics")
            return BleResult.Ok(link)
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED    -> connected.complete(null)

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val link = synchronized(lock) {
                        if (link == null) lostBeforeTheLink = BleFailure(BleFailure.Kind.REFUSED, status, "disconnected while connecting")
                        link
                    }
                    if (link != null) link.lost(BleFailure(BleFailure.Kind.NO_LINK, status, "disconnected"))
                    else {
                        connected.complete(lostBeforeTheLink)
                        discovered.complete(lostBeforeTheLink)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            discovered.complete(if (status == BluetoothGatt.GATT_SUCCESS) null else BleFailure(BleFailure.Kind.REFUSED, status, "the service discovery failed"))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            idOf(characteristic)?.let { linkOrNull()?.writeDone(it, failureOf(status)) }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            idOf(characteristic)?.let { linkOrNull()?.readDone(it, value, failureOf(status)) }
        }

        /** Android 12 only. Android 13 and later call both versions, so this one is ignored there. */
        @Deprecated("The system calls the version with the value from Android 13.")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value
            idOf(characteristic)?.let { linkOrNull()?.readDone(it, value, failureOf(status)) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            idOf(characteristic)?.let { linkOrNull()?.received(it, value) }
        }

        /** Android 12 only, as the read above. */
        @Deprecated("The system calls the version with the value from Android 13.")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            idOf(characteristic)?.let { linkOrNull()?.received(it, value.copyOf()) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != CCCD) return
            idOf(descriptor.characteristic)?.let { linkOrNull()?.subscribeDone(it, failureOf(status)) }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // Also comes with no request: Android 14 asks for a large MTU by itself.
            if (status == BluetoothGatt.GATT_SUCCESS) this.mtu = mtu
            linkOrNull()?.mtuDone(mtu, failureOf(status))
        }

        private fun linkOrNull(): QueuedBleLink? = synchronized(lock) { link }

        // ---- BleLinkPort ----------------------------------------------------------------------------------------

        override fun startWrite(characteristic: CharacteristicId, data: ByteArray, type: WriteType): Boolean {
            val gatt = gatt ?: return false
            val target = find(characteristic) ?: return false
            val systemType = if (type == WriteType.WITH_RESPONSE) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) gatt.writeCharacteristic(target, data, systemType) == BluetoothStatusCodes.SUCCESS
            else writeOnAndroid12(gatt, target, data, systemType)
        }

        override fun startRead(characteristic: CharacteristicId): Boolean {
            val gatt = gatt ?: return false
            return gatt.readCharacteristic(find(characteristic) ?: return false)
        }

        override fun startSubscribe(characteristic: CharacteristicId, kind: Subscription): Boolean {
            val gatt = gatt ?: return false
            val target = find(characteristic) ?: return false
            val cccd = target.getDescriptor(CCCD) ?: return false
            if (!gatt.setCharacteristicNotification(target, true)) return false
            val value = if (kind == Subscription.INDICATE) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
            else writeDescriptorOnAndroid12(gatt, cccd, value)
        }

        override fun startMtu(wanted: Int): Boolean = gatt?.requestMtu(wanted) == true

        override fun maxWriteLength(type: WriteType): Int = mtu - ATT_HEADER

        override fun requestPriority(priority: LinkPriority) {
            gatt?.requestConnectionPriority(
                when (priority) {
                    LinkPriority.HIGH      -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
                    LinkPriority.BALANCED  -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
                    LinkPriority.LOW_POWER -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
                }
            )
        }

        /** Also for a connect that failed. A GATT left open blocks the next connects with status 133. */
        override fun release() {
            val gatt = synchronized(lock) {
                if (released) return
                released = true
                gatt
            } ?: return
            try {
                gatt.disconnect()
            } catch (e: Exception) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "BLE $device: disconnect: ${e.message}")
            }
            try {
                gatt.close()
            } catch (e: Exception) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "BLE $device: close: ${e.message}")
            }
        }

        private fun find(id: CharacteristicId): BluetoothGattCharacteristic? =
            gatt?.getService(UUID.fromString(id.service.value))?.getCharacteristic(UUID.fromString(id.characteristic.value))

        private fun idOf(characteristic: BluetoothGattCharacteristic): CharacteristicId? {
            val service = characteristic.service ?: return null
            return CharacteristicId(BleUuid.of(service.uuid.toString()), BleUuid.of(characteristic.uuid.toString()))
        }

        private fun failureOf(status: Int): BleFailure? =
            if (status == BluetoothGatt.GATT_SUCCESS) null else BleFailure(BleFailure.Kind.REFUSED, status)
    }

    private fun servicesOf(gatt: BluetoothGatt): GattServices =
        GattServices(buildMap {
            for (service in gatt.services) for (characteristic in service.characteristics)
                put(CharacteristicId(BleUuid.of(service.uuid.toString()), BleUuid.of(characteristic.uuid.toString())), propertiesOf(characteristic.properties))
        })

    private fun propertiesOf(properties: Int): Set<CharacteristicProperty> = buildSet {
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add(CharacteristicProperty.READ)
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add(CharacteristicProperty.WRITE)
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add(CharacteristicProperty.WRITE_WITHOUT_RESPONSE)
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add(CharacteristicProperty.NOTIFY)
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add(CharacteristicProperty.INDICATE)
    }

    @Suppress("DEPRECATION")
    private fun writeOnAndroid12(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray, type: Int): Boolean {
        characteristic.writeType = type
        characteristic.value = data
        return gatt.writeCharacteristic(characteristic)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptorOnAndroid12(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        descriptor.value = value
        return gatt.writeDescriptor(descriptor)
    }

    private inner class AndroidBonding : BleBonding {

        override fun bondState(device: DeviceId): Flow<BondState> = bondEvents(device).distinctUntilChanged()

        /** The state now, then every event of the system, also one that repeats the state. */
        private fun bondEvents(device: DeviceId): Flow<BondState> = callbackFlow {
            val remote = remote(device) ?: throw BleException(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "not a Bluetooth address: ${device.value}"))
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val changed = deviceOf(intent) ?: return
                    if (changed.address == remote.address) trySend(bondStateOf(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)))
                }
            }
            // First the receiver, then the state now: no change can fall between the two.
            registerBluetoothReceiver(receiver, BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            trySend(currentBondState(remote))
            awaitClose { context.unregisterReceiver(receiver) }
        }

        override suspend fun createBond(device: DeviceId, timeout: Duration, pin: String?): BleResult<Unit> {
            unavailable(scan = false)?.let { return BleResult.Failed(it) }
            val remote = remote(device) ?: return notAnAddress(device)
            // Before createBond(): the request can come at once.
            val pinAnswer = pin?.let { answerPinRequests(remote, it) }
            try {
                return bond(remote, device, timeout)
            } finally {
                pinAnswer?.let { context.unregisterReceiver(it) }
            }
        }

        /**
         * Answers the system's pairing request for [remote] with [pin], so the user sees no PIN dialog. Only a request for
         * a PIN: one that asks for something else gets the system dialog. The request is an ordered broadcast, and the high
         * priority lets this answer come before the dialog. Seen on a real Aaruy pump (2026-11-04): a PIN request
         * (variant 0) about 8 s after createBond().
         */
        private fun answerPinRequests(remote: BluetoothDevice, pin: String): BroadcastReceiver {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (deviceOf(intent)?.address != remote.address) return
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
                    if (variant != BluetoothDevice.PAIRING_VARIANT_PIN) {
                        aapsLogger.debug(LTag.PUMPBTCOMM, "BLE ${remote.address}: pairing request $variant, left to the system")
                        return
                    }
                    val given = try {
                        remote.setPin(pin.encodeToByteArray())
                    } catch (e: SecurityException) {
                        aapsLogger.error(LTag.PUMPBTCOMM, "BLE ${remote.address}: the PIN was not allowed", e)
                        false
                    }
                    aapsLogger.debug(LTag.PUMPBTCOMM, "BLE ${remote.address}: PIN given: $given")
                    if (given) abortBroadcast()
                }
            }
            val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST).apply { priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1 }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            return receiver
        }

        private suspend fun bond(remote: BluetoothDevice, device: DeviceId, timeout: Duration): BleResult<Unit> {
            var refused = false
            var first = true
            val end = withTimeoutOrNull(timeout) {
                // The raw events: a failed bond can report NONE after NONE, which bondState() would not pass on.
                // Merged with a read of the state, because the broadcast can come late: on a real phone it reached
                // this app 3 s after the stack had the bond, and a Carelevo patch drops the link 2.4 s after the
                // bond when no discovery follows. The read only ever gives BONDED, so a failure still comes from
                // the broadcast, and a BONDED read before createBond() means the device was bonded already.
                merge(bondEvents(device), bondedByReading(remote)).first { state ->
                    if (first) {
                        first = false
                        when (state) {
                            BondState.BONDED  -> true
                            BondState.BONDING -> false // started before: wait for its end
                            BondState.NONE    -> if (remote.createBond()) false else true.also { refused = true }
                        }
                    } else state != BondState.BONDING
                }
            }
            return when {
                refused               -> BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "the bonding did not start"))
                end == null           -> BleResult.Failed(BleFailure(BleFailure.Kind.TIMEOUT, message = "not bonded in $timeout"))
                end == BondState.BONDED -> BleResult.Ok(Unit)
                else                  -> BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "the bonding failed"))
            }
        }

        override suspend fun removeBond(device: DeviceId): BleResult<Unit> {
            unavailable(scan = false)?.let { return BleResult.Failed(it) }
            val remote = remote(device) ?: return notAnAddress(device)
            return try {
                // Not in the public API; every pump driver has removed bonds this way.
                if (remote.javaClass.getMethod("removeBond").invoke(remote) == true) BleResult.Ok(Unit)
                else BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "removeBond refused"))
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPBTCOMM, "BLE $device: removeBond failed", e)
                BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = e.message))
            }
        }

        /** BONDED once the system's own state says so, read every [bondReadInterval]. Emits nothing else. */
        private fun bondedByReading(remote: BluetoothDevice): Flow<BondState> = flow {
            while (true) {
                delay(bondReadInterval)
                if (currentBondState(remote) == BondState.BONDED) {
                    emit(BondState.BONDED)
                    return@flow
                }
            }
        }

        private fun currentBondState(remote: BluetoothDevice): BondState =
            try {
                bondStateOf(remote.bondState)
            } catch (_: SecurityException) {
                BondState.NONE
            }

        private fun bondStateOf(state: Int): BondState = when (state) {
            BluetoothDevice.BOND_BONDED  -> BondState.BONDED
            BluetoothDevice.BOND_BONDING -> BondState.BONDING
            else                         -> BondState.NONE
        }

        private fun deviceOf(intent: Intent): BluetoothDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    private companion object {

        /** The client characteristic configuration descriptor: the switch for notifications, the same on every device. */
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEFAULT_MTU = 23
        const val ATT_HEADER = 3
    }
}
