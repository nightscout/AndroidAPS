package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * [NsConnection] owned by a coroutine scope instead of a service.
 *
 * Android keeps the sockets in a bound service holding a wake lock, so they survive the screen going
 * away. This class has no equivalent, and it stays free of UIKit so it can be tested without one.
 *
 * ## What happens when the socket drops
 *
 * Nothing here closes the socket on backgrounding: [stop] is called only on app exit, on a
 * connection setting changing, and when connectivity becomes disallowed. `IosForegroundWatcher` was
 * written to drive that and is wired to nothing, so a dropped connection is left to the transport's
 * own reconnect. When it does come back, `onConnectStorage` clears `initialLoadFinished` and asks
 * for a catch-up round, and that round refetches the missed window from the persisted high-water
 * mark.
 *
 * **There is no REST polling fallback behind that**, which this comment used to claim there was. The
 * five-minute tick in `NSClientV3Plugin` runs a load only when websockets are switched off or the
 * platform has none; while they are on it logs and does nothing. `wsDisconnectGraceMs` debounces the
 * `masterReachable` flow and starts no load. So the catch-up is the only recovery, a socket that
 * never reconnects is a real hole, and on a platform that suspends the whole process there is
 * nothing else to close it.
 *
 * ## What is deliberately different from Android
 *
 * The payload parsing is kotlinx rather than `org.json`. That is the stated reason this is a
 * separate implementation, and it is a weak one: [NsSocket] hands every payload across as text, so
 * nothing stops the Android service parsing the same way. The field names, the collection routing
 * and the ordering rules are meant to match, and where a rule is subtle it is called out below.
 *
 * They did not match for the `settings` collection: it was in the subscribe list from the start with
 * no branch here, so every client-control frame was dropped and remote control could not work on any
 * platform using this class. The branch below is the Android one, ported. Two routings for one
 * protocol is what allowed that, and is the reason they should become one.
 */
/*
 * `NSClientV3Plugin` and `NsIncomingDataProcessor` arrive as `() -> T` to break a cycle. The
 * plugin takes an `NsConnection`, which is this class, and the processor reaches the plugin as its
 * `NsClient`. Android never meets either loop: there the socket lives in `NSClientV3Service`, an
 * Android service the system constructs, so `ServiceNsConnection` only binds to it and needs neither
 * of these. iOS has no service, so this class does that work itself and has to name them.
 *
 * A singleton on the class, matching `ServiceNsConnection` on Android. It was previously one only
 * because the `NsConnection` provider that aliases it is scoped - true today, but nothing injects
 * the concrete type, and the day something does it would open a second pair of sockets and its own
 * `connected` flow while the plugin watched the other one.
 *
 * Deferring is safe here rather than merely convenient: nothing is looked up while the graph is
 * built. The plugin is read when a socket connects and the processor when a frame arrives, and by
 * either point both have long existed.
 */
@SingleIn(AppScope::class)
class SocketNsConnection @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val config: Config,
    private val nsClientV3Plugin: () -> NSClientV3Plugin,
    private val nsFrameHandler: NsFrameHandler,
    private val nsConnectHandler: NsConnectHandler,
    private val nsClientRepository: NSClientRepository,
    private val nsSocketFactory: NsSocketFactory
) : NsConnection {

    private var storageSocket: NsSocket? = null
    private var alarmSocket: NsSocket? = null

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Null means "nothing is carrying a connection", which is not the same as "not connected". */
    override val socketConnected: Boolean? get() = storageSocket?.let { _connected.value }

    override val hasLiveSocket: Boolean get() = storageSocket != null

    override fun start(reason: String) {
        // Every one of these is a reason not to hold a socket, and each tears down first so a
        // setting turned off mid-session takes effect rather than leaving the old socket up.
        if (preferences.get(StringKey.NsClientUrl).isEmpty()) return stop()
        if (!preferences.get(BooleanKey.NsClient3UseWs)) return stop()
        if (!nsClientV3Plugin().isAllowed) {
            stop()
            nsClientRepository.addLog("● WS", nsClientV3Plugin().blockingReason)
            return
        }
        if (preferences.get(NsclientBooleanKey.NsPaused)) {
            stop()
            nsClientRepository.addLog("● WS", "paused")
            return
        }
        // Idempotent, as the contract requires: a live connection is left alone.
        if (storageSocket != null) {
            nsClientRepository.addLog("● WS", "already initialized, skip $reason")
            return
        }

        val base = preferences.get(StringKey.NsClientUrl).lowercase().trimEnd('/')
        // socket.io throws for a URL it can parse but cannot use - an unusable transport, a host it
        // will not accept - rather than reporting it. Android still guards this in
        // `NSClientV3Service` and dev did too; the port kept only the URI-syntax half, which is the
        // `create() == null` branch below. Without the guard the throw leaves
        // `NSClientV3Plugin.onStart` part way through, so the plugin ends up enabled with no
        // observers and no upload collector attached and only a restart repairs it. Losing the
        // socket is bad, losing the rest of the plugin silently is worse.
        try {
            openSockets(base, reason)
        } catch (e: RuntimeException) {
            stop()
            nsClientRepository.addLog("● WS", "RuntimeException: ${e.message}")
        }
    }

    /**
     * Both sockets: created, listened to and asked to connect.
     *
     * Split out of [start] only so one `try` can cover all of it, which is the shape Android has.
     */
    private fun openSockets(base: String, reason: String) {
        val storage = nsSocketFactory.create("$base/storage")
        if (storage == null) {
            stop()
            nsClientRepository.addLog("● WS", "Wrong URL syntax")
            return
        }
        // Assigned before the listeners go on, so a failure below still leaves it reachable by stop().
        storageSocket = storage
        storage.on(NsSocket.EVENT_CONNECT) { onConnectStorage() }
        storage.on(NsSocket.EVENT_DISCONNECT) { reason -> onDisconnectStorage(reason) }
        storage.on("create") { raw -> onDataCreateUpdate(raw) }
        storage.on("update") { raw -> onDataCreateUpdate(raw) }
        storage.on("delete") { raw -> onDataDelete(raw) }
        nsClientRepository.addLog("► WS", "do connect storage $reason")
        storage.connect()

        if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements) ||
            preferences.get(BooleanKey.NsClientNotificationsFromAlarms)
        ) {
            val alarm = nsSocketFactory.create("$base/alarm")
            if (alarm == null) {
                stop()
                nsClientRepository.addLog("● WS", "Wrong URL syntax")
                return
            }
            alarmSocket = alarm
            alarm.on(NsSocket.EVENT_CONNECT) { onConnectAlarms() }
            alarm.on(NsSocket.EVENT_DISCONNECT) { reason -> nsConnectHandler.onDisconnectAlarm(reason) }
            alarm.on("announcement") { raw -> onAnnouncement(raw) }
            alarm.on("alarm") { raw -> onAlarm(raw) }
            alarm.on("urgent_alarm") { raw -> onUrgentAlarm(raw) }
            alarm.on("clear_alarm") { raw -> onClearAlarm(raw) }
            nsClientRepository.addLog("► WS", "do connect alarm $reason")
            alarm.connect()
        }
    }

    /** Sockets close before the fields are cleared, so a quick restart cannot find the old ones. */
    override fun stop() {
        storageSocket?.close()
        alarmSocket?.close()
        _connected.value = false
        storageSocket = null
        alarmSocket = null
    }

    override fun acknowledgeAlarm(alarm: NSAlarm, silenceForMillis: Long) {
        alarmSocket?.emitAlarmAck(alarm.level, alarm.group, silenceForMillis)
        nsClientRepository.addLog("► ALARMACK ", "${alarm.level} ${alarm.group} $silenceForMillis")
    }

    // ---------------------------------------------------------------------------------------------
    // Storage socket
    // ---------------------------------------------------------------------------------------------

    private fun onConnectStorage() = nsConnectHandler.onConnectStorage(storageSocket) { _connected.value = it }

    private fun onDisconnectStorage(reason: String) = nsConnectHandler.onDisconnectStorage(reason) { _connected.value = false }

    // ---------------------------------------------------------------------------------------------
    // Alarm socket
    // ---------------------------------------------------------------------------------------------

    private fun onConnectAlarms() = nsConnectHandler.onConnectAlarms(alarmSocket)

    // Frame handling is shared with Android - see NsFrameHandler. These stay as delegates so the
    // characterization tests written against this class still exercise the real routing.
    internal fun onDataCreateUpdate(raw: String) = nsFrameHandler.onDataCreateUpdate(raw)
    internal fun onDataDelete(raw: String) = nsFrameHandler.onDataDelete(raw)
    internal fun onAnnouncement(raw: String) = nsFrameHandler.onAnnouncement(raw)
    internal fun onAlarm(raw: String) = nsFrameHandler.onAlarm(raw)
    internal fun onUrgentAlarm(raw: String) = nsFrameHandler.onUrgentAlarm(raw)
    internal fun onClearAlarm(raw: String) = nsFrameHandler.onClearAlarm(raw)


    // ---------------------------------------------------------------------------------------------

    // Frame reading lives in NsWsPayload, which is tested on its own.
    private fun parse(raw: String): JsonObject? = NsWsPayload.parse(raw)

    private fun JsonObject.str(key: String): String? = NsWsPayload.string(this, key)
    private fun JsonObject.bool(key: String): Boolean? = NsWsPayload.boolean(this, key)
    private fun JsonObject.long(key: String): Long? = NsWsPayload.long(this, key)

    private companion object {

        val COLLECTIONS = listOf("devicestatus", "entries", "profile", "treatments", "foods", "settings")
    }
}
