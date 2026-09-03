package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.mapper.toCalibrationMbg
import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.mapper.toNSFood
import app.aaps.core.nssdk.mapper.toNSSgvV3
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.plugins.sync.nsclientV3.NSAlarmObject
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.extensions.toRunningConfiguration
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    private val nsIncomingDataProcessor: () -> NsIncomingDataProcessor,
    // Deferred for the same reason as the two above: both reach back into the client-control side,
    // which reaches the plugin, which owns this connection. Read only when a settings frame arrives.
    private val runningConfiguration: () -> RunningConfiguration,
    private val orphanDetector: () -> OrphanDetector,
    private val storeDataForDb: StoreDataForDb,
    private val notificationManager: NotificationManager,
    private val nsClientRepository: NSClientRepository,
    private val nsDeviceStatusHandler: NSDeviceStatusHandler,
    private val nsSocketFactory: NsSocketFactory,
    private val appScope: CoroutineScope
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
            alarm.on(NsSocket.EVENT_DISCONNECT) { nsClientRepository.addLog("◄ WS", "disconnect alarm event") }
            alarm.on("announcement") { raw -> onAnnouncement(raw) }
            alarm.on("alarm") { raw -> onAlarm(raw, BooleanKey.NsClientNotificationsFromAlarms) }
            alarm.on("urgent_alarm") { raw -> onAlarm(raw, BooleanKey.NsClientNotificationsFromAlarms) }
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

    private fun onConnectStorage() {
        val socket = storageSocket ?: return
        nsClientRepository.addLog("◄ WS", "connected storage ID: ${socket.id ?: "NULL"}")
        val auth = buildJsonObject {
            put("accessToken", preferences.get(StringKey.NsClientAccessToken))
            put("collections", buildJsonArray { COLLECTIONS.forEach { add(it) } })
        }
        nsClientRepository.addLog("► WS", "requesting auth for storage")
        socket.emitWithAck("subscribe", auth.toString()) { raw ->
            val response = parse(raw)
            _connected.value = if (response?.bool("success") == true) {
                nsClientRepository.addLog("◄ WS", "Subscribed for: ${NsWsPayload.text(response, "collections")}")
                // Nothing arrives while disconnected, so the next round has to be a catch-up one.
                nsClientV3Plugin().initialLoadFinished = false
                nsClientV3Plugin().executeLoop("WS_CONNECT")
                true
            } else {
                nsClientRepository.addLog("◄ WS", "Auth failed")
                false
            }
            nsClientRepository.updateStatus(nsClientV3Plugin().status)
        }
    }

    private fun onDisconnectStorage(reason: String) {
        aapsLogger.debug(LTag.NSCLIENT, "disconnect storage reason: $reason")
        nsClientRepository.addLog("◄ WS", "disconnect storage event")
        _connected.value = false
        nsClientV3Plugin().initialLoadFinished = false
        nsClientRepository.updateStatus(nsClientV3Plugin().status)
    }

    internal fun onDataCreateUpdate(raw: String) {
        val response = parse(raw) ?: return
        val collection = response.str("colName") ?: return
        val doc = NsWsPayload.document(response) ?: return
        val docString = doc.toString()
        nsClientRepository.addLog("◄ WS CREATE/UPDATE", collection, doc)

        val srvModified = doc.long("srvModified") ?: 0L
        // The high-water mark must not move until the catch-up round has finished, or the next load
        // asks for "modified since (just moved pointer)" and skips the very window it should backfill.
        if (nsClientV3Plugin().initialLoadFinished) {
            nsClientV3Plugin().lastLoadedSrvModified.set(collection, srvModified)
            nsClientV3Plugin().storeLastLoadedSrvModified()
        }

        when (collection) {
            "devicestatus" -> nsDeviceStatusHandler.handleNewData(arrayOf(docString.toNSDeviceStatus()), live = true)
            "entries"      -> {
                docString.toNSSgvV3()?.let {
                    nsIncomingDataProcessor().processSgvs(listOf(it), doFullSync = false)
                    storeDataForDb.requestStoreGlucoseValues()
                }
                // The same collection also carries AAPS calibration entries.
                docString.toCalibrationMbg()?.let {
                    nsIncomingDataProcessor().processCalibrations(listOf(it), doFullSync = false)
                    storeDataForDb.requestStoreCalibrationEntries()
                }
            }

            "profile"      -> appScope.launch { nsIncomingDataProcessor().processProfile(doc, doFullSync = false) }
            "treatments"   -> docString.toNSTreatment()?.let {
                nsIncomingDataProcessor().processTreatments(listOf(it), doFullSync = false)
                storeDataForDb.requestStoreTreatments(fullSync = false)
            }

            "foods"        -> docString.toNSFood()?.let {
                nsIncomingDataProcessor().processFood(listOf(it))
                storeDataForDb.requestStoreFoods()
            }

            // Client control travels on this collection, so without it pairing and every command
            // after it are inert - the collection was subscribed to from the start, and every frame
            // was dropped here. `srvModified` is the one read at the top of this method: Android
            // reads the same key a second time for the orphan call, which is the same value.
            "settings"     -> {
                val identifier = doc.str("identifier") ?: ""
                when {
                    // Client: cold config doc - apply everything except the active scene.
                    config.AAPSCLIENT && identifier == SettingsIdentifiers.COLD                                   ->
                        docString.toRunningConfiguration()?.let {
                            runningConfiguration().applyCold(it)
                            // Only the orphan bookkeeping is deferred, because onSettingsDoc takes the
                            // repository mutex and this handler runs on the socket's thread, not in a
                            // coroutine. applyCold and the liveness clock stay inline.
                            appScope.launch { orphanDetector().onSettingsDoc(it, srvModified) }
                            // A live config push proves the master is alive now - feed the liveness clock.
                            nsClientV3Plugin().bumpMasterSignal(srvModified)
                        }

                    // Client: hot state doc - the active scene and runtime flags only. Kept apart from
                    // the cold branch so this can never clear a running scene.
                    config.AAPSCLIENT && identifier == SettingsIdentifiers.STATE                                  ->
                        docString.toRunningConfiguration()?.let {
                            runningConfiguration().applyHot(it)
                            nsClientV3Plugin().bumpMasterSignal(srvModified)
                        }

                    // Client: master->client command ACK. Must be tested BEFORE the generic
                    // IDENTIFIER_PREFIX branch, because ack identifiers carry that prefix too and the
                    // master receiver would otherwise try to verify an ack as an inbound command.
                    config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_ACK_PREFIX)      ->
                        appScope.launch { nsClientV3Plugin().handleClientControlAckEvent(doc) }

                    // Client: master->client live bolus-progress mirror. Same ordering rule as the ACK.
                    config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_PROGRESS_PREFIX) ->
                        appScope.launch { nsClientV3Plugin().handleClientControlProgressEvent(doc) }

                    // Master: inbound client-control envelopes. The plugin gates on the master toggle
                    // itself. Guarded by !AAPSCLIENT because Nightscout echoes every write back to its
                    // sender, and a client must not process its own outgoing command (an unknown
                    // clientId leads to deleteSettings and an HTTP 410 tombstone).
                    !config.AAPSCLIENT && identifier.startsWith(ClientControlPublisher.IDENTIFIER_PREFIX)         ->
                        appScope.launch { nsClientV3Plugin().handleClientControlSettingsEvent(identifier, doc) }
                }
            }
        }
    }

    internal fun onDataDelete(raw: String) {
        val response = parse(raw) ?: return
        val collection = response.str("colName") ?: ""
        val identifier = response.str("identifier") ?: ""
        nsClientRepository.addLog("◄ WS DELETE", "$collection $identifier")
        when (collection) {
            "treatments" -> {
                storeDataForDb.addToDeleteTreatment(identifier)
                storeDataForDb.requestUpdateDeletedTreatments()
            }

            "entries"    -> {
                storeDataForDb.addToDeleteGlucoseValue(identifier)
                storeDataForDb.requestUpdateDeletedGlucoseValues()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Alarm socket
    // ---------------------------------------------------------------------------------------------

    private fun onConnectAlarms() {
        val socket = alarmSocket ?: return
        nsClientRepository.addLog("◄ WS", "connected alarms ID: ${socket.id ?: "NULL"}")
        val auth = buildJsonObject { put("accessToken", preferences.get(StringKey.NsClientAccessToken)) }
        nsClientRepository.addLog("► WS", "requesting auth for alarms")
        socket.emitWithAck("subscribe", auth.toString()) { raw ->
            val response = parse(raw)
            if (response?.bool("success") == true) nsClientRepository.addLog("◄ WS", response.str("message") ?: "")
            else nsClientRepository.addLog("◄ WS", "Auth failed")
        }
    }

    internal fun onAnnouncement(raw: String) {
        val data = parse(raw) ?: return
        nsClientRepository.addLog("◄ ANNOUNCEMENT", data.str("message") ?: "")
        if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)) post(NSAlarmObject(data))
    }

    internal fun onAlarm(raw: String, gate: BooleanKey) {
        val data = parse(raw) ?: return
        nsClientRepository.addLog("◄ ALARM", data.str("title") ?: "")
        if (preferences.get(gate)) post(NSAlarmObject(data))
    }

    internal fun onClearAlarm(raw: String) {
        val data = parse(raw) ?: return
        nsClientRepository.addLog("◄ CLEARALARM", data.str("title") ?: "")
        notificationManager.dismiss(NotificationId.NS_ALARM)
        notificationManager.dismiss(NotificationId.NS_URGENT_ALARM)
    }

    /**
     * Level decides the notification, exactly as on Android.
     *
     * The snooze actions are not offered here yet: they write a per-level snooze preference and are
     * worth porting with their own test rather than by eye. An alarm without snooze buttons is still
     * an alarm; one that snoozes the wrong level silently is not.
     */
    private fun post(alarm: NSAlarm) {
        when (alarm.level) {
            0    -> notificationManager.post(
                id = NotificationId.NS_ANNOUNCEMENT,
                text = alarm.message,
                level = NotificationLevel.ANNOUNCEMENT,
                validMinutes = 60
            )

            1    -> notificationManager.post(id = NotificationId.NS_ALARM, text = alarm.title, sound = AlarmSound.ALARM)
            2    -> notificationManager.post(id = NotificationId.NS_URGENT_ALARM, text = alarm.title, sound = AlarmSound.URGENT_ALARM)
            else -> Unit
        }
    }

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
