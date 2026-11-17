package app.aaps.core.interfaces.pump.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A device that [EmulatedBleCentral] can find and connect to: the radio side of a pump emulator.
 *
 * The central calls these functions on coroutines of its own, a moment after the driver asked, as a real Bluetooth
 * stack answers. The operations of one link come one at a time. A function that returns null leaves the operation with
 * no end, as a stack that does not answer: the driver then gets [BleFailure.Kind.TIMEOUT] and the link is closed.
 */
interface EmulatedPeripheral {

    val device: DeviceId
    val services: GattServices

    /** What the device advertises now, or null while it does not advertise (out of range, for example). */
    fun advertisement(): Advertisement?

    /**
     * A central connects. False when the device does not answer, as when it is out of range: the connect then waits
     * until its time limit. [link] is the device's side of the new link.
     */
    suspend fun onConnect(link: PeripheralLink): Boolean

    /** Ok makes the characteristic subscribed: [PeripheralLink.send] works on it from then on. */
    suspend fun onSubscribe(link: PeripheralLink, characteristic: CharacteristicId): BleResult<Unit>? = BleResult.Ok(Unit)

    suspend fun onWrite(link: PeripheralLink, characteristic: CharacteristicId, data: ByteArray, type: WriteType): BleResult<Unit>?

    suspend fun onRead(link: PeripheralLink, characteristic: CharacteristicId): BleResult<ByteArray>? =
        BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "the emulator does not read $characteristic"))

    /** The link ended, from either side. Nothing sent on it arrives any more. */
    fun onDisconnect(link: PeripheralLink) {}
}

/** The device's side of one link of [EmulatedBleCentral]. Each connect makes a new one. */
class PeripheralLink internal constructor() {

    internal lateinit var port: EmulatedBleCentral.EmulatedPort

    /** True until the link ends, from either side. */
    val isUp: Boolean get() = port.link.state.value == LinkState.Up

    /**
     * The device sends [data] on [characteristic]. False when it does not arrive: the link is down, or the central
     * has not subscribed to the characteristic. A real device sends nothing before the subscription either.
     */
    fun send(characteristic: CharacteristicId, data: ByteArray): Boolean {
        if (!isUp || characteristic !in port.subscribed.value) return false
        port.link.received(characteristic, data.copyOf())
        return true
    }

    /** The device drops the link, as when it goes out of range. */
    fun drop() = port.link.lost(BleFailure(BleFailure.Kind.NO_LINK, message = "the device dropped the link"))
}

/**
 * A [BleCentral] with no radio, for the pump emulators: in tests and in the emulator mode of a real install. Its devices
 * are [EmulatedPeripheral]s in the app.
 *
 * Its links are [QueuedBleLink]s, the code the real centrals use too. So a driver on an emulator goes through the same
 * operation queue and time limits as on a real pump.
 */
class EmulatedBleCentral(
    private val aapsLogger: AAPSLogger? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : BleCentral {

    /** How often a device advertises while a scan runs. A test makes it short, the real speed makes it long. */
    var advertisingInterval: Duration = 20.milliseconds

    private val peripherals = MutableStateFlow<Map<DeviceId, EmulatedPeripheral>>(emptyMap())
    private val links = MutableStateFlow<Set<QueuedBleLink>>(emptySet())
    private val scanning = Mutex()

    private val _adapterState = MutableStateFlow(AdapterState.ON)
    override val adapterState: StateFlow<AdapterState> = _adapterState

    private val emulatedBonding = EmulatedBonding()
    override val bonding: BleBonding = emulatedBonding

    fun add(peripheral: EmulatedPeripheral, bonded: Boolean = false) {
        peripherals.update { it + (peripheral.device to peripheral) }
        if (bonded) emulatedBonding.set(peripheral.device, BondState.BONDED)
    }

    fun remove(device: DeviceId) {
        peripherals.update { it - device }
    }

    /** Bluetooth switched on or off, or the permission taken away. Not [AdapterState.ON] drops every link, as a phone does. */
    fun setAdapterState(state: AdapterState) {
        _adapterState.value = state
        if (state != AdapterState.ON) links.value.forEach { it.lost(BleFailure(BleFailure.Kind.BLUETOOTH_OFF, message = "Bluetooth is $state")) }
    }

    override fun scan(filter: ScanFilter): Flow<Advertisement> = flow {
        scanning.withLock {
            while (true) {
                unavailable()?.let { throw BleException(it) }
                for (peripheral in peripherals.value.values) {
                    val advertisement = peripheral.advertisement() ?: continue
                    if (filter.matches(advertisement)) emit(advertisement)
                }
                delay(advertisingInterval)
            }
        }
    }

    override suspend fun connect(device: DeviceId, options: ConnectOptions): BleResult<BleLink> {
        unavailable()?.let { return BleResult.Failed(it) }
        val link = withTimeoutOrNull(options.timeout) {
            // A device that is not there, or does not answer, leaves the connect waiting, as a real one does.
            val peripheral = peripherals.value[device] ?: awaitCancellation()
            val peripheralLink = PeripheralLink()
            val port = EmulatedPort(peripheral, peripheralLink)
            port.link = QueuedBleLink(device, peripheral.services, port, aapsLogger)
            peripheralLink.port = port
            if (!peripheral.onConnect(peripheralLink)) awaitCancellation()
            links.update { it + port.link }
            port.link
        }
        return if (link == null) BleResult.Failed(BleFailure(BleFailure.Kind.TIMEOUT, message = "$device did not answer in ${options.timeout}"))
        else BleResult.Ok(link)
    }

    private fun unavailable(): BleFailure? = when (_adapterState.value) {
        AdapterState.ON           -> null
        AdapterState.UNAUTHORIZED -> BleFailure(BleFailure.Kind.NOT_AUTHORIZED)
        AdapterState.UNSUPPORTED  -> BleFailure(BleFailure.Kind.NOT_SUPPORTED)
        AdapterState.OFF,
        AdapterState.UNKNOWN      -> BleFailure(BleFailure.Kind.BLUETOOTH_OFF)
    }

    /** One link to one [EmulatedPeripheral]. Each operation ends on a coroutine of its own, as from a real stack. */
    internal inner class EmulatedPort(private val peripheral: EmulatedPeripheral, private val peripheralLink: PeripheralLink) : BleLinkPort {

        lateinit var link: QueuedBleLink
        val subscribed = MutableStateFlow<Set<CharacteristicId>>(emptySet())
        private var mtu = DEFAULT_MTU

        override fun startWrite(characteristic: CharacteristicId, data: ByteArray, type: WriteType): Boolean {
            scope.launch { peripheral.onWrite(peripheralLink, characteristic, data, type)?.let { link.writeDone(characteristic, it.failureOrNull()) } }
            return true
        }

        override fun startRead(characteristic: CharacteristicId): Boolean {
            scope.launch {
                when (val result = peripheral.onRead(peripheralLink, characteristic)) {
                    null                -> Unit
                    is BleResult.Ok     -> link.readDone(characteristic, result.value.copyOf(), null)
                    is BleResult.Failed -> link.readDone(characteristic, null, result.failure)
                }
            }
            return true
        }

        override fun startSubscribe(characteristic: CharacteristicId, kind: Subscription): Boolean {
            scope.launch {
                val result = peripheral.onSubscribe(peripheralLink, characteristic) ?: return@launch
                if (result is BleResult.Ok) subscribed.update { it + characteristic }
                link.subscribeDone(characteristic, result.failureOrNull())
            }
            return true
        }

        override fun startMtu(wanted: Int): Boolean {
            mtu = wanted.coerceIn(DEFAULT_MTU, MAX_MTU)
            scope.launch { link.mtuDone(mtu, null) }
            return true
        }

        override fun maxWriteLength(type: WriteType): Int = mtu - ATT_HEADER

        override fun requestPriority(priority: LinkPriority) {}

        override fun release() {
            links.update { it - link }
            peripheral.onDisconnect(peripheralLink)
        }
    }

    private inner class EmulatedBonding : BleBonding {

        private val states = MutableStateFlow<Map<DeviceId, BondState>>(emptyMap())

        fun set(device: DeviceId, state: BondState) = states.update { it + (device to state) }

        override fun bondState(device: DeviceId): Flow<BondState> = states.map { it[device] ?: BondState.NONE }.distinctUntilChanged()

        override suspend fun createBond(device: DeviceId, timeout: Duration): BleResult<Unit> {
            if (device !in peripherals.value) return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "no device $device"))
            set(device, BondState.BONDING)
            delay(BOND_TIME)
            set(device, BondState.BONDED)
            return BleResult.Ok(Unit)
        }

        override suspend fun removeBond(device: DeviceId): BleResult<Unit> {
            states.update { it - device }
            return BleResult.Ok(Unit)
        }
    }

    private companion object {

        /** The MTU of every link before a request, and the largest a request gets. */
        const val DEFAULT_MTU = 23
        const val MAX_MTU = 517
        const val ATT_HEADER = 3
        val BOND_TIME = 10.milliseconds
    }
}

private fun BleResult<*>.failureOrNull(): BleFailure? = (this as? BleResult.Failed)?.failure
