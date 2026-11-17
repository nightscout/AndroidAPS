package app.aaps.core.interfaces.pump.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Version 2 of the pump Bluetooth layer, for Android and iOS.
 *
 * A driver uses only [BleCentral] and the [BleLink] that [BleCentral.connect] gives. The platform code is one
 * implementation per platform in `:implementation` (androidMain on Android Bluetooth, iosMain on CoreBluetooth), the
 * same for every pump. An emulator replaces the central in tests and in the emulator mode of a real install.
 *
 * Rules that hold for every implementation:
 * - Every operation returns its result, also a failure. A write that failed is never reported as written.
 * - The operations of one link run one at a time, in the order they were called. A driver needs no pauses for the
 *   Bluetooth stack. A pause that its pump needs (Medtrum wants 600 ms between two subscriptions) stays in the driver.
 * - A link is one connection. After [BleLink.close] or a lost connection it is dead and stays dead, and nothing of
 *   an old link can reach a new one. To talk again, connect again.
 *
 * [QueuedBleLink] keeps these rules once for every platform, and its tests run on every platform. Each central adds only
 * the calls of its Bluetooth stack, and its own test checks them.
 *
 * Version 1 ([BleTransport]) stays until the last driver has moved here, and is deleted then.
 */

/** The phone's Bluetooth LE central: it scans, connects and, on Android, bonds. One for the whole app. */
interface BleCentral {

    /** Whether Bluetooth can be used now. The app cannot switch it on (iOS does not allow it), only tell the user. */
    val adapterState: StateFlow<AdapterState>

    /**
     * Bonding, where the app controls it: Android. Null on iOS, where the system pairs by itself when the app first
     * touches a characteristic that needs encryption, and shows its own dialog.
     */
    val bonding: BleBonding?

    /**
     * Scans while the flow is collected and stops when the collection stops, so a driver limits a scan with
     * `withTimeout` or `take`. Only devices that match [filter] come. One device can come many times, with a new rssi.
     *
     * Only one scan runs at a time: a second collection waits until the first one ends.
     *
     * A scan that cannot run (Bluetooth off, not allowed, refused by the system) ends the flow with a [BleException].
     */
    fun scan(filter: ScanFilter): Flow<Advertisement>

    /**
     * Connects to [device], discovers its services and returns the link, ready for [BleLink.subscribe]. A device that
     * is not near fails with [BleFailure.Kind.TIMEOUT] after [ConnectOptions.timeout]; it never waits for ever.
     */
    suspend fun connect(device: DeviceId, options: ConnectOptions = ConnectOptions()): BleResult<BleLink>
}

/**
 * One connection to one device. Made by [BleCentral.connect], dead after [close] or when the connection is lost.
 *
 * The operations run one at a time: a call waits until the call before it has its result. Each one has a time limit.
 * An operation with no answer from the Bluetooth stack in its limit fails with [BleFailure.Kind.TIMEOUT], and the link
 * is closed then, because a stack that does not answer one operation does not answer the next one either.
 */
interface BleLink {

    val device: DeviceId

    /** [LinkState.Up] until [close] or until the connection is lost. Never up again: connect again for a new link. */
    val state: StateFlow<LinkState>

    /** The services and characteristics found when the link was made. */
    val services: GattServices

    /**
     * Data the device sent on a subscribed characteristic, in the order it came. Data that comes while nobody
     * collects is kept until it is collected. Collect it once: two collectors would split the data between them.
     * The flow ends when the link is dead.
     */
    val incoming: Flow<Incoming>

    /** The longest [write] of [type] that fits in one packet: MTU - 3 on Android, `maximumWriteValueLength` on iOS. */
    fun maxWriteLength(type: WriteType): Int

    /**
     * Turns on notifications or indications of [characteristic]. Ok only when the device has confirmed it: on Android
     * the descriptor write came back as written, on iOS the characteristic reports that it notifies.
     */
    suspend fun subscribe(characteristic: CharacteristicId, kind: Subscription = Subscription.AUTO, timeout: Duration = OPERATION_TIMEOUT): BleResult<Unit>

    /**
     * Writes [data], which must not be longer than [maxWriteLength]. A driver cuts longer messages itself, because
     * every pump cuts them in its own way.
     *
     * [WriteType.WITH_RESPONSE]: Ok when the device confirmed the write. [WriteType.WITHOUT_RESPONSE]: Ok when the
     * phone has sent it; the device confirms nothing, so a lost packet is known only from the pump's answer.
     */
    suspend fun write(characteristic: CharacteristicId, data: ByteArray, type: WriteType, timeout: Duration = OPERATION_TIMEOUT): BleResult<Unit>

    suspend fun read(characteristic: CharacteristicId, timeout: Duration = OPERATION_TIMEOUT): BleResult<ByteArray>

    /** Asks for a larger MTU and returns the one in use. iOS chooses the MTU by itself and returns it here at once. */
    suspend fun requestMtu(wanted: Int): Int

    /** Asks for a faster or a slower link. Only a wish: Android may follow it, iOS has nothing like it and ignores it. */
    suspend fun requestPriority(priority: LinkPriority)

    /** Disconnects and frees the link. Safe to call more than once, and on a link that is lost already. */
    suspend fun close()

    companion object {

        val OPERATION_TIMEOUT: Duration = 5.seconds
    }
}

/** Bonding of the phone with a device, on Android. See [BleCentral.bonding]. */
interface BleBonding {

    /** The bond state now and each change of it, from the system's events, not by polling. */
    fun bondState(device: DeviceId): Flow<BondState>

    /**
     * Starts bonding and waits until the system reports [BondState.BONDED]. Ok then; a failure when the bond ends in
     * [BondState.NONE] or [timeout] passes. [BondState.BONDING] is not bonded: a pump that needs an encrypted link must
     * not get a command while bonding still runs.
     */
    suspend fun createBond(device: DeviceId, timeout: Duration): BleResult<Unit>

    suspend fun removeBond(device: DeviceId): BleResult<Unit>
}

/**
 * A device as the platform names it: the MAC address on Android (upper case, `AA:BB:CC:DD:EE:FF`), an identifier UUID
 * on iOS. Store it as it is and compare it, never take it apart: on iOS the same pump has another id on another
 * phone, and a MAC address that a pump reports in its own protocol cannot be used to connect there.
 */
@JvmInline
value class DeviceId(val value: String)

/**
 * A Bluetooth UUID in its full 128-bit form, lower case. [of] also takes the 16-bit and 32-bit short forms, as
 * `"fff1"`, and expands them with the Bluetooth base UUID, so ids compare equal however a driver wrote them.
 */
@JvmInline
value class BleUuid private constructor(val value: String) {

    companion object {

        private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

        fun of(uuid: String): BleUuid {
            val lower = uuid.trim().lowercase()
            return BleUuid(
                when (lower.length) {
                    4    -> "0000$lower$BASE_SUFFIX"
                    8    -> "$lower$BASE_SUFFIX"
                    36   -> lower
                    else -> throw IllegalArgumentException("Not a Bluetooth UUID: $uuid")
                }
            )
        }
    }
}

/** One characteristic of one service. */
data class CharacteristicId(val service: BleUuid, val characteristic: BleUuid)

/** What a scan found. A field the device did not advertise is empty or null. */
class Advertisement(
    val device: DeviceId,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<BleUuid>,
    /** By company id, as Medtrum's 18305. */
    val manufacturerData: Map<Int, ByteArray>,
    val serviceData: Map<BleUuid, ByteArray>
)

/**
 * Which devices a scan returns. Empty fields do not filter. All given fields must match; for [services] it is enough
 * that the device advertises one of them, as on both platforms.
 *
 * On iOS only [services] works while the app is in the background; [namePrefix] and [manufacturerId] are checked in
 * the app, so a scan in the background needs [services].
 */
data class ScanFilter(
    val services: List<BleUuid> = emptyList(),
    val namePrefix: String? = null,
    val manufacturerId: Int? = null
) {

    /** The same check on every platform, for what the platform's own filter cannot do. */
    fun matches(advertisement: Advertisement): Boolean =
        (services.isEmpty() || services.any { it in advertisement.serviceUuids }) &&
            (namePrefix == null || advertisement.name?.startsWith(namePrefix) == true) &&
            (manufacturerId == null || manufacturerId in advertisement.manufacturerData)
}

data class ConnectOptions(
    val timeout: Duration = 30.seconds,
    /** The services to discover. Empty: all. iOS discovers faster with a list. */
    val services: List<BleUuid> = emptyList(),
    /**
     * Android only: let the system make the connection when the device comes in range, with no time limit of its own
     * (`autoConnect`). [timeout] still ends the wait. Ignored on iOS, whose connect always waits like this.
     */
    val androidAutoConnect: Boolean = false
)

/** Data that came from the device on [characteristic]. */
class Incoming(val characteristic: CharacteristicId, val data: ByteArray)

class GattServices(val characteristics: Map<CharacteristicId, Set<CharacteristicProperty>>) {

    fun has(characteristic: CharacteristicId): Boolean = characteristic in characteristics
}

enum class CharacteristicProperty { READ, WRITE, WRITE_WITHOUT_RESPONSE, NOTIFY, INDICATE }

enum class AdapterState {

    /** Not known yet: iOS reports the state a moment after the app starts. */
    UNKNOWN,
    ON,
    OFF,

    /** The user has not allowed the app to use Bluetooth. */
    UNAUTHORIZED,
    UNSUPPORTED
}

sealed interface LinkState {

    data object Up : LinkState

    /** The connection was lost; [failure] says why, with the platform's code for the log. */
    data class Lost(val failure: BleFailure) : LinkState

    /** The driver closed the link. */
    data object Closed : LinkState
}

enum class WriteType { WITH_RESPONSE, WITHOUT_RESPONSE }

enum class Subscription {

    /** Indications when the characteristic offers them, else notifications. */
    AUTO,
    NOTIFY,
    INDICATE
}

enum class LinkPriority { HIGH, BALANCED, LOW_POWER }

sealed interface BleResult<out T> {

    data class Ok<out T>(val value: T) : BleResult<T>
    data class Failed(val failure: BleFailure) : BleResult<Nothing>
}

/** A failure where no [BleResult] can be returned: a [BleCentral.scan] that cannot run, a [BleBonding.bondState] of a bad id. */
class BleException(val failure: BleFailure) : Exception(failure.toString())

/** Why an operation did not work. [platformStatus] is the raw code (the Android GATT status, the iOS error code), for the log. */
data class BleFailure(val kind: Kind, val platformStatus: Int? = null, val message: String? = null) {

    enum class Kind {

        BLUETOOTH_OFF,
        NOT_AUTHORIZED,

        /** The link is dead: closed, or the connection was lost. */
        NO_LINK,

        /** The device does not have the service or the characteristic, or it does not allow the operation on it. */
        NOT_SUPPORTED,
        TIMEOUT,

        /** The platform or the device refused the operation, see [platformStatus]. */
        REFUSED
    }
}
