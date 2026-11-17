package app.aaps.implementation.pump.ble

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
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSThread
import platform.Foundation.NSUUID
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.time.Duration.Companion.seconds

/**
 * The [BleCentral] on CoreBluetooth. Each link is a [QueuedBleLink]; this class only turns the delegate calls into the
 * ends of its operations. CoreBluetooth calls back on the main queue, and every call into it is made there too.
 *
 * What iOS does differently, as the API says: the device id is the peripheral's identifier (another one on another
 * phone), the system pairs by itself ([bonding] is null), and the MTU is the system's choice.
 *
 * Not yet: state restoration, which lets iOS start the app again for its pump. It comes with the first driver on iOS,
 * together with `NSBluetoothAlwaysUsageDescription` in the app, without which iOS ends the app at the first use.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosBleCentral(
    private val aapsLogger: AAPSLogger
) : BleCentral {

    private val states = MutableStateFlow(AdapterState.UNKNOWN)

    /** Made on first use, not with the graph: making it shows the Bluetooth permission dialog the first time. */
    private val manager: CBCentralManager by lazy { CBCentralManager(delegate = CentralDelegate(this), queue = null, options = null) }

    override val adapterState: StateFlow<AdapterState>
        get() {
            manager
            return states
        }

    override val bonding: BleBonding? = null

    private val scanning = Mutex()

    /** Where the found devices go while a scan runs. */
    private val scanSink = MutableStateFlow<((Advertisement, CBPeripheral) -> Unit)?>(null)

    /** Devices a scan returned, by identifier. A connect needs the peripheral object; iOS can find a known one again too. */
    private val found = MutableStateFlow<Map<String, CBPeripheral>>(emptyMap())
    private val connections = MutableStateFlow<Map<String, Connection>>(emptyMap())

    override fun scan(filter: ScanFilter): Flow<Advertisement> = flow {
        scanning.withLock { emitAll(scanOnce(filter)) }
    }

    private fun scanOnce(filter: ScanFilter): Flow<Advertisement> = callbackFlow {
        unavailable()?.let { throw BleException(it) }
        scanSink.value = { advertisement, peripheral ->
            if (filter.matches(advertisement)) {
                found.update { it + (advertisement.device.value to peripheral) }
                trySend(advertisement)
            }
        }
        onMain {
            // Only the services filter works in the background; the rest is checked by filter.matches above.
            manager.scanForPeripheralsWithServices(
                filter.services.map { it.toCBUUID() }.ifEmpty { null },
                mapOf<Any?, Any>(CBCentralManagerScanOptionAllowDuplicatesKey to true)
            )
        }
        awaitClose {
            scanSink.value = null
            onMain { manager.stopScan() }
        }
    }

    override suspend fun connect(device: DeviceId, options: ConnectOptions): BleResult<BleLink> {
        unavailable()?.let { return BleResult.Failed(it) }
        val peripheral = peripheralOf(device)
            ?: return BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "iOS does not know ${device.value}: scan for it first"))
        // A delegate call names only the peripheral. The end of the last link must come first, or it reaches this one.
        val key = peripheral.identifier.UUIDString
        withTimeoutOrNull(OLD_LINK_WAIT) { connections.first { key !in it } }
        val connection = Connection(device, peripheral)
        connections.update { it + (connection.key to connection) }
        val result = try {
            withTimeoutOrNull(options.timeout) { connection.open(options) }
                ?: BleResult.Failed(BleFailure(BleFailure.Kind.TIMEOUT, message = "$device did not connect in ${options.timeout}"))
        } catch (e: CancellationException) {
            connection.release()
            throw e
        }
        if (result is BleResult.Failed) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "BLE $device: connect failed: ${result.failure}")
            connection.release()
        }
        return result
    }

    private fun peripheralOf(device: DeviceId): CBPeripheral? {
        found.value[device.value]?.let { return it }
        if (!isPeripheralIdentifier(device.value)) return null
        return manager.retrievePeripheralsWithIdentifiers(listOf(NSUUID(uUIDString = device.value))).firstOrNull() as? CBPeripheral
    }

    /** Waits for the first state after the manager is made: iOS reports it a moment later. */
    private suspend fun unavailable(): BleFailure? {
        manager
        val state = withTimeoutOrNull(STATE_WAIT) { states.first { it != AdapterState.UNKNOWN } } ?: AdapterState.UNKNOWN
        return when (state) {
            AdapterState.ON           -> null
            AdapterState.UNAUTHORIZED -> BleFailure(BleFailure.Kind.NOT_AUTHORIZED)
            AdapterState.UNSUPPORTED  -> BleFailure(BleFailure.Kind.NOT_SUPPORTED)
            AdapterState.OFF,
            AdapterState.UNKNOWN      -> BleFailure(BleFailure.Kind.BLUETOOTH_OFF)
        }
    }

    // ---- From the central's delegate, on the main queue ---------------------------------------------------------

    internal fun stateChanged(state: CBManagerState) {
        val adapterState = when (state) {
            CBManagerStatePoweredOn     -> AdapterState.ON
            CBManagerStatePoweredOff    -> AdapterState.OFF
            CBManagerStateUnauthorized  -> AdapterState.UNAUTHORIZED
            CBManagerStateUnsupported   -> AdapterState.UNSUPPORTED
            else                        -> AdapterState.UNKNOWN // unknown, or resetting
        }
        states.value = adapterState
        if (adapterState != AdapterState.ON)
            connections.value.values.forEach { it.disconnected(BleFailure(BleFailure.Kind.BLUETOOTH_OFF, message = "Bluetooth is $adapterState")) }
    }

    internal fun discovered(peripheral: CBPeripheral, data: Map<Any?, *>, rssi: NSNumber) {
        val sink = scanSink.value ?: return
        sink(advertisementOf(DeviceId(peripheral.identifier.UUIDString), peripheral.name, data, rssi.intValue), peripheral)
    }

    internal fun connectionOf(peripheral: CBPeripheral): Connection? = connections.value[peripheral.identifier.UUIDString]

    /**
     * One connection to one peripheral: first the connect and the discovery, then the port of its [QueuedBleLink].
     * Its state changes only on the main queue, so it needs no lock.
     */
    internal inner class Connection(private val device: DeviceId, private val peripheral: CBPeripheral) : BleLinkPort {

        val key: String = peripheral.identifier.UUIDString
        private val delegate = PeripheralDelegate(this)
        private val connected = CompletableDeferred<BleFailure?>()
        private val discovered = CompletableDeferred<BleFailure?>()

        private var link: QueuedBleLink? = null
        private var lostBeforeTheLink: BleFailure? = null
        private var servicesLeft = 0
        private var readWaiting: CharacteristicId? = null
        private var writeWaiting: (() -> Unit)? = null
        private var released = false

        /** iOS has reported the end of this connection; no more delegate calls come for it. */
        private var gone = false

        suspend fun open(options: ConnectOptions): BleResult<BleLink> {
            withContext(Dispatchers.Main) {
                peripheral.delegate = delegate
                manager.connectPeripheral(peripheral, null)
            }
            connected.await()?.let { return BleResult.Failed(it) }
            withContext(Dispatchers.Main) { peripheral.discoverServices(options.services.map { it.toCBUUID() }.ifEmpty { null }) }
            discovered.await()?.let { return BleResult.Failed(it) }
            return withContext(Dispatchers.Main) {
                // The device may have gone after the discovery, when there was no link yet to tell.
                lostBeforeTheLink?.let { return@withContext BleResult.Failed(it) }
                val link = QueuedBleLink(device, servicesOf(peripheral), this@Connection, aapsLogger)
                this@Connection.link = link
                BleResult.Ok(link)
            }
        }

        // ---- From the delegates ---------------------------------------------------------------------------------

        fun didConnect() {
            connected.complete(null)
        }

        fun disconnected(failure: BleFailure) {
            gone = true
            if (released) {
                forget()
                return
            }
            val link = link
            if (link != null) link.lost(failure)
            else {
                val early = failure.copy(kind = if (failure.kind == BleFailure.Kind.BLUETOOTH_OFF) failure.kind else BleFailure.Kind.REFUSED)
                lostBeforeTheLink = early
                connected.complete(early)
                discovered.complete(early)
            }
        }

        fun didDiscoverServices(error: NSError?) {
            if (error != null) {
                discovered.complete(failureOf(error))
                return
            }
            val services = peripheral.services.orEmpty().filterIsInstance<CBService>()
            servicesLeft = services.size
            if (services.isEmpty()) discovered.complete(null)
            services.forEach { peripheral.discoverCharacteristics(null, it) }
        }

        fun didDiscoverCharacteristics(error: NSError?) {
            if (error != null) discovered.complete(failureOf(error))
            else if (--servicesLeft == 0) discovered.complete(null)
        }

        /** iOS gives the answer to a read and a notification the same way: an answer is what comes while a read waits. */
        fun didUpdateValue(characteristic: CBCharacteristic, error: NSError?) {
            val link = link ?: return
            val id = idOf(characteristic) ?: return
            if (readWaiting == id) {
                readWaiting = null
                link.readDone(id, characteristic.value?.toByteArray(), failureOf(error))
            } else if (error == null) characteristic.value?.let { link.received(id, it.toByteArray()) }
        }

        fun didWriteValue(characteristic: CBCharacteristic, error: NSError?) {
            idOf(characteristic)?.let { link?.writeDone(it, failureOf(error)) }
        }

        fun didUpdateNotificationState(characteristic: CBCharacteristic, error: NSError?) {
            val id = idOf(characteristic) ?: return
            val failure = failureOf(error) ?: if (characteristic.isNotifying) null else BleFailure(BleFailure.Kind.REFUSED, message = "not notifying")
            link?.subscribeDone(id, failure)
        }

        fun readyToSendWithoutResponse() {
            val write = writeWaiting ?: return
            writeWaiting = null
            write()
        }

        // ---- BleLinkPort: each call goes to the main queue; what fails there ends the operation ------------------

        override fun startWrite(characteristic: CharacteristicId, data: ByteArray, type: WriteType): Boolean {
            onMain {
                val target = find(characteristic) ?: return@onMain link?.writeDone(characteristic, missing(characteristic)) ?: Unit
                val bytes = data.toNSData()
                when {
                    type == WriteType.WITH_RESPONSE        -> peripheral.writeValue(bytes, target, CBCharacteristicWriteWithResponse)
                    // Without response: sent when iOS has room for it, which iOS says with readyToSendWithoutResponse.
                    peripheral.canSendWriteWithoutResponse -> sendWithoutResponse(characteristic, target, bytes)
                    else                                   -> writeWaiting = { sendWithoutResponse(characteristic, target, bytes) }
                }
            }
            return true
        }

        private fun sendWithoutResponse(id: CharacteristicId, target: CBCharacteristic, bytes: NSData) {
            peripheral.writeValue(bytes, target, CBCharacteristicWriteWithoutResponse)
            link?.writeDone(id, null)
        }

        override fun startRead(characteristic: CharacteristicId): Boolean {
            onMain {
                val target = find(characteristic) ?: return@onMain link?.readDone(characteristic, null, missing(characteristic)) ?: Unit
                readWaiting = characteristic
                peripheral.readValueForCharacteristic(target)
            }
            return true
        }

        /** iOS chooses notifications or indications by itself, from the characteristic. */
        override fun startSubscribe(characteristic: CharacteristicId, kind: Subscription): Boolean {
            onMain {
                val target = find(characteristic) ?: return@onMain link?.subscribeDone(characteristic, missing(characteristic)) ?: Unit
                peripheral.setNotifyValue(true, target)
            }
            return true
        }

        /** iOS has chosen the MTU itself; this only tells it. */
        override fun startMtu(wanted: Int): Boolean {
            onMain { link?.mtuDone(maxWriteLength(WriteType.WITHOUT_RESPONSE) + ATT_HEADER, null) }
            return true
        }

        override fun maxWriteLength(type: WriteType): Int =
            peripheral.maximumWriteValueLengthForType(if (type == WriteType.WITH_RESPONSE) CBCharacteristicWriteWithResponse else CBCharacteristicWriteWithoutResponse).toInt()

        override fun requestPriority(priority: LinkPriority) {}

        /** The connection stays known until iOS reports its end, so that report cannot reach the next connection. */
        override fun release() {
            onMain {
                if (released) return@onMain
                released = true
                writeWaiting = null
                if (gone) forget() else manager.cancelPeripheralConnection(peripheral)
            }
        }

        private fun forget() = connections.update { if (it[key] === this) it - key else it }

        private fun find(id: CharacteristicId): CBCharacteristic? =
            peripheral.services.orEmpty().filterIsInstance<CBService>().firstOrNull { it.UUID.toBleUuid() == id.service }
                ?.characteristics.orEmpty().filterIsInstance<CBCharacteristic>().firstOrNull { it.UUID.toBleUuid() == id.characteristic }

        private fun missing(id: CharacteristicId) = BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "no $id")
    }

    private fun servicesOf(peripheral: CBPeripheral): GattServices =
        GattServices(buildMap {
            for (service in peripheral.services.orEmpty().filterIsInstance<CBService>())
                for (characteristic in service.characteristics.orEmpty().filterIsInstance<CBCharacteristic>())
                    put(CharacteristicId(service.UUID.toBleUuid(), characteristic.UUID.toBleUuid()), propertiesOf(characteristic.properties))
        })

    private fun propertiesOf(properties: ULong): Set<CharacteristicProperty> = buildSet {
        if (properties and CBCharacteristicPropertyRead != 0uL) add(CharacteristicProperty.READ)
        if (properties and CBCharacteristicPropertyWrite != 0uL) add(CharacteristicProperty.WRITE)
        if (properties and CBCharacteristicPropertyWriteWithoutResponse != 0uL) add(CharacteristicProperty.WRITE_WITHOUT_RESPONSE)
        if (properties and CBCharacteristicPropertyNotify != 0uL) add(CharacteristicProperty.NOTIFY)
        if (properties and CBCharacteristicPropertyIndicate != 0uL) add(CharacteristicProperty.INDICATE)
    }

    private companion object {

        val STATE_WAIT = 2.seconds

        /** How long a connect waits for iOS to end the last link to the same device. */
        val OLD_LINK_WAIT = 2.seconds
        const val ATT_HEADER = 3
    }
}

private fun idOf(characteristic: CBCharacteristic): CharacteristicId? {
    val service = characteristic.service ?: return null
    return CharacteristicId(service.UUID.toBleUuid(), characteristic.UUID.toBleUuid())
}

private fun failureOf(error: NSError?): BleFailure? = error?.let { BleFailure(BleFailure.Kind.REFUSED, it.code.toInt(), it.localizedDescription) }

/** CoreBluetooth wants its calls on the queue it calls back on: the main queue here. */
private fun onMain(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue(), block)
}

private class CentralDelegate(private val owner: IosBleCentral) : NSObject(), CBCentralManagerDelegateProtocol {

    override fun centralManagerDidUpdateState(central: CBCentralManager) = owner.stateChanged(central.state)

    override fun centralManager(central: CBCentralManager, didDiscoverPeripheral: CBPeripheral, advertisementData: Map<Any?, *>, RSSI: NSNumber) =
        owner.discovered(didDiscoverPeripheral, advertisementData, RSSI)

    @ObjCSignatureOverride
    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        owner.connectionOf(didConnectPeripheral)?.didConnect()
    }

    @ObjCSignatureOverride
    override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
        owner.connectionOf(didFailToConnectPeripheral)?.disconnected(failureOf(error) ?: BleFailure(BleFailure.Kind.REFUSED, message = "the connect failed"))
    }

    @ObjCSignatureOverride
    override fun centralManager(central: CBCentralManager, didDisconnectPeripheral: CBPeripheral, error: NSError?) {
        val failure = error?.let { BleFailure(BleFailure.Kind.NO_LINK, it.code.toInt(), it.localizedDescription) } ?: BleFailure(BleFailure.Kind.NO_LINK, message = "disconnected")
        owner.connectionOf(didDisconnectPeripheral)?.disconnected(failure)
    }
}

private class PeripheralDelegate(private val connection: IosBleCentral.Connection) : NSObject(), CBPeripheralDelegateProtocol {

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) = connection.didDiscoverServices(didDiscoverServices)

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) =
        connection.didDiscoverCharacteristics(error)

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) =
        connection.didUpdateValue(didUpdateValueForCharacteristic, error)

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didWriteValueForCharacteristic: CBCharacteristic, error: NSError?) =
        connection.didWriteValue(didWriteValueForCharacteristic, error)

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didUpdateNotificationStateForCharacteristic: CBCharacteristic, error: NSError?) =
        connection.didUpdateNotificationState(didUpdateNotificationStateForCharacteristic, error)

    override fun peripheralIsReadyToSendWriteWithoutResponse(peripheral: CBPeripheral) = connection.readyToSendWithoutResponse()
}
