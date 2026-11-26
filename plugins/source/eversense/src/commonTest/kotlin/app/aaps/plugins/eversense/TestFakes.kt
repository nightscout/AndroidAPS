package app.aaps.plugins.eversense

import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleBonding
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.BondState
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.ConnectOptions
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.GattServices
import app.aaps.core.interfaces.pump.ble.Incoming
import app.aaps.core.interfaces.pump.ble.LinkPriority
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.pump.ble.Subscription
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.connection.EversenseSession
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.protocol.WireEncoding
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.time.Duration

/**
 * One link to a transmitter with no radio. The test plays the transmitter: [onWrite] sees each frame the driver
 * writes and answers with [send]; [lose] drops the link as the radio or the transmitter would.
 */
internal class FakeBleLink(override val device: DeviceId, val is365: Boolean) : BleLink {

    val request: CharacteristicId = if (is365) EversenseSession.V2_REQUEST else EversenseSession.E3_REQUEST
    val response: CharacteristicId = if (is365) EversenseSession.V2_RESPONSE else EversenseSession.E3_RESPONSE

    private val _state = MutableStateFlow<LinkState>(LinkState.Up)
    override val state: StateFlow<LinkState> = _state
    override val services = GattServices(
        mapOf(request to setOf(CharacteristicProperty.WRITE), response to setOf(CharacteristicProperty.NOTIFY))
    )
    private val received = Channel<Incoming>(Channel.UNLIMITED)
    override val incoming: Flow<Incoming> = received.receiveAsFlow()

    /** Every frame written, in order. */
    val writes = mutableListOf<ByteArray>()

    /** The transmitter: called for each written frame, before the write returns. */
    var onWrite: FakeBleLink.(ByteArray) -> Unit = {}
    var writeResult: BleResult<Unit> = BleResult.Ok(Unit)
    var subscribeResult: BleResult<Unit> = BleResult.Ok(Unit)
    var closes = 0
        private set

    override fun maxWriteLength(type: WriteType): Int = MTU - 3
    override suspend fun subscribe(characteristic: CharacteristicId, kind: Subscription, timeout: Duration): BleResult<Unit> = subscribeResult

    override suspend fun write(characteristic: CharacteristicId, data: ByteArray, type: WriteType, timeout: Duration): BleResult<Unit> {
        if (_state.value != LinkState.Up) return BleResult.Failed(BleFailure(BleFailure.Kind.NO_LINK))
        writes += data
        if (writeResult is BleResult.Ok) onWrite(data)
        return writeResult
    }

    override suspend fun read(characteristic: CharacteristicId, timeout: Duration): BleResult<ByteArray> =
        BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED))

    override suspend fun requestMtu(wanted: Int): Int = MTU
    override suspend fun requestPriority(priority: LinkPriority) {}

    override suspend fun close() {
        closes++
        if (_state.value == LinkState.Up) _state.value = LinkState.Closed
        received.close()
    }

    /** The transmitter sends [data]; false when the link is dead. */
    fun send(data: ByteArray, characteristic: CharacteristicId = response): Boolean =
        received.trySend(Incoming(characteristic, data)).isSuccess

    /** The connection is lost, with [status] as the platform's code (19: closed by the transmitter). */
    fun lose(status: Int) {
        if (_state.value != LinkState.Up) return
        _state.value = LinkState.Lost(BleFailure(BleFailure.Kind.NO_LINK, platformStatus = status))
        received.close()
    }

    val isUp: Boolean get() = _state.value == LinkState.Up

    companion object {

        const val MTU = 23
    }
}

/** An E3 transmitter: it takes the bonding, and rejects every other command, so a sync after it ends at once. */
internal fun FakeBleLink.actAsE3(acceptBonding: Boolean = true) {
    onWrite = { frame ->
        if (frame[0] == EversenseE3Packets.SaveBLEBondingInformationCommandId && acceptBonding)
            send(e3Frame(EversenseE3Packets.SaveBLEBondingInformationResponseId))
        else send(e3Frame(EversenseE3Packets.ErrorResponseId, frame[0], 5))
    }
}

/** An E3 frame as the transmitter sends it: the response id, the payload and the CRC. */
internal fun e3Frame(responseId: Byte, vararg payload: Byte): ByteArray = WireEncoding.e3(responseId, payload)

/** A [BleCentral] whose connects the test decides. By default each connect gives a new E3 that bonds. */
internal class FakeBleCentral : BleCentral {

    override val adapterState = MutableStateFlow(AdapterState.ON)

    /** Null as on iOS; a [FakeBleBonding] as on Android */
    override var bonding: BleBonding? = null

    /** The options of each connect, in order. */
    val connects = mutableListOf<ConnectOptions>()

    /** The links handed out, in order. */
    val links = mutableListOf<FakeBleLink>()

    /** Makes the link of the next connect. */
    var newLink: (DeviceId) -> FakeBleLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3() } }

    /** The answer to a connect: by default a link from [newLink]. */
    var answer: suspend (DeviceId) -> BleResult<BleLink> = { device -> BleResult.Ok(newLink(device).also { links += it }) }

    /** What a scan finds, before the scan's filter */
    val advertisements = mutableListOf<Advertisement>()

    override fun scan(filter: ScanFilter): Flow<Advertisement> = advertisements.filter(filter::matches).asFlow()

    override suspend fun connect(device: DeviceId, options: ConnectOptions): BleResult<BleLink> {
        connects += options
        return answer(device)
    }

    fun refuseConnects(status: Int) {
        answer = { BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, platformStatus = status)) }
    }

    fun acceptConnects() {
        answer = { device -> BleResult.Ok(newLink(device).also { links += it }) }
    }
}

/** Android's bonding, recording what the driver removes and whether the link was still up then. */
internal class FakeBleBonding(private val central: FakeBleCentral) : BleBonding {

    /** Each removed device, with true when a link to it was still up at that moment */
    val removed = mutableListOf<Pair<DeviceId, Boolean>>()

    override fun bondState(device: DeviceId): Flow<BondState> = emptyFlow()
    override suspend fun createBond(device: DeviceId, timeout: Duration, pin: String?): BleResult<Unit> = BleResult.Ok(Unit)

    override suspend fun removeBond(device: DeviceId): BleResult<Unit> {
        removed += device to central.links.any { it.device == device && it.isUp }
        return BleResult.Ok(Unit)
    }
}

/** What the driver told its watchers. */
internal class RecordingWatcher : EversenseWatcher {

    val connections = mutableListOf<Boolean>()
    val alarms = mutableListOf<ActiveAlarm>()
    val reads = mutableListOf<List<EversenseCGMResult>>()
    var ready = 0
    var notPlaced = 0
    var states = 0

    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {
        reads += readings
    }

    override fun onStateChanged(state: EversenseState) {
        states++
    }

    override fun onConnectionChanged(connected: Boolean) {
        connections += connected
    }

    override fun onAlarmReceived(alarm: ActiveAlarm) {
        alarms += alarm
    }

    override fun onTransmitterNotPlaced() {
        notPlaced++
    }

    override fun onTransmitterReady() {
        ready++
    }
}

/** Texts as their resource name, with the arguments in brackets: `units_format_percent(85)`. */
internal class NameTextResolver : TextResolver {

    override fun gs(ref: TextRef): String = name(ref)
    override fun gs(ref: TextRef, vararg args: Any?): String = if (args.isEmpty()) name(ref) else "${name(ref)}(${args.joinToString(",")})"
    override fun gsNotLocalised(ref: TextRef): String = name(ref)

    companion object {

        fun name(ref: TextRef): String = when (ref) {
            is TextRef.Named      -> ref.name
            is TextRef.AndroidRes -> "id${ref.id}"
            is TextRef.Literal    -> ref.text
        }
    }
}

/** The notifications posted and dismissed, with a new handle for each post. */
internal class RecordingNotificationManager : NotificationManager {

    data class Posted(val handle: NotificationHandle, val id: NotificationId, val text: String, val level: NotificationLevel, val sound: AlarmSound?)

    val posted = mutableListOf<Posted>()
    val dismissedIds = mutableListOf<NotificationId>()
    val dismissedHandles = mutableListOf<NotificationHandle>()
    private var next = 1

    /** What is on screen now: posted and not dismissed. */
    val shown: List<Posted> get() = posted.filter { it.handle !in dismissedHandles && it.id !in dismissedIds }

    override val notifications: StateFlow<List<AapsNotification>> = MutableStateFlow(emptyList())
    override fun cleanUp() {}

    private fun record(id: NotificationId, text: String, level: NotificationLevel, sound: AlarmSound?): NotificationHandle =
        NotificationHandle(next++).also { posted += Posted(it, id, text, level, sound) }

    override fun post(
        id: NotificationId, text: String, level: NotificationLevel, validMinutes: Int, sound: AlarmSound?,
        actions: List<NotificationAction>, validityCheck: (() -> Boolean)?
    ): NotificationHandle = record(id, text, level, sound)

    override fun post(
        id: NotificationId, text: String, level: NotificationLevel, date: Long, validTo: Long, sound: AlarmSound?,
        actions: List<NotificationAction>, validityCheck: (() -> Boolean)?, alarmTitle: String?, canMute: Boolean, tellWatch: Boolean, breakThroughDnd: Boolean, ringtoneUri: String?,
        systemText: String?,
        vibrateOnly: Boolean
    ): NotificationHandle = record(id, text, level, sound)

    override fun post(
        id: NotificationId, textRef: TextRef, level: NotificationLevel, validMinutes: Int, date: Long, validTo: Long,
        sound: AlarmSound?, actions: List<NotificationAction>, validityCheck: (() -> Boolean)?
    ): NotificationHandle = record(id, NameTextResolver.name(textRef), level, sound)

    override fun dismiss(id: NotificationId) {
        dismissedIds += id
    }

    override fun dismiss(handle: NotificationHandle) {
        dismissedHandles += handle
    }

    override fun muteAllAlarms() {}
    override fun clearLeftovers() {}
}
