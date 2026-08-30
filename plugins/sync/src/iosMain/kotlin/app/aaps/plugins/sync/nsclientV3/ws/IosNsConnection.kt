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
import app.aaps.core.nssdk.mapper.toCalibrationMbg
import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.mapper.toNSFood
import app.aaps.core.nssdk.mapper.toNSSgvV3
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
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
 * away. iOS has nothing of the sort, so the connection follows the app: up while it is active, down
 * when it is not. Something outside drives that - see `IosForegroundWatcher` - and this class stays
 * free of UIKit so it can be tested without one.
 *
 * ## Why a closed socket is not a hole
 *
 * Dropping the socket on backgrounding is safe because the shared plugin already handles a dropped
 * socket: [connected] going false starts the REST polling fallback after the disconnect grace, and
 * `initialLoadFinished` going false makes the next round backfill the window that was missed. That
 * machinery was written for connection drops on Android; backgrounding is just another drop.
 *
 * ## What is deliberately different from Android
 *
 * The payload parsing is kotlinx rather than `org.json`, which is the only reason this is a separate
 * implementation rather than shared code. The field names, the collection routing and the ordering
 * rules are the same, and where a rule is subtle it is called out below.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosNsConnection @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val config: Config,
    private val nsClientV3Plugin: NSClientV3Plugin,
    private val nsIncomingDataProcessor: NsIncomingDataProcessor,
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
        if (!nsClientV3Plugin.isAllowed) {
            stop()
            nsClientRepository.addLog("● WS", nsClientV3Plugin.blockingReason)
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
                nsClientRepository.addLog("◄ WS", "Subscribed for: ${response.str("collections")}")
                // Nothing arrives while disconnected, so the next round has to be a catch-up one.
                nsClientV3Plugin.initialLoadFinished = false
                nsClientV3Plugin.executeLoop("WS_CONNECT")
                true
            } else {
                nsClientRepository.addLog("◄ WS", "Auth failed")
                false
            }
            nsClientRepository.updateStatus(nsClientV3Plugin.status)
        }
    }

    private fun onDisconnectStorage(reason: String) {
        aapsLogger.debug(LTag.NSCLIENT, "disconnect storage reason: $reason")
        nsClientRepository.addLog("◄ WS", "disconnect storage event")
        _connected.value = false
        nsClientV3Plugin.initialLoadFinished = false
        nsClientRepository.updateStatus(nsClientV3Plugin.status)
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
        if (nsClientV3Plugin.initialLoadFinished) {
            nsClientV3Plugin.lastLoadedSrvModified.set(collection, srvModified)
            nsClientV3Plugin.storeLastLoadedSrvModified()
        }

        when (collection) {
            "devicestatus" -> nsDeviceStatusHandler.handleNewData(arrayOf(docString.toNSDeviceStatus()), live = true)
            "entries"      -> {
                docString.toNSSgvV3()?.let {
                    nsIncomingDataProcessor.processSgvs(listOf(it), doFullSync = false)
                    storeDataForDb.requestStoreGlucoseValues()
                }
                // The same collection also carries AAPS calibration entries.
                docString.toCalibrationMbg()?.let {
                    nsIncomingDataProcessor.processCalibrations(listOf(it), doFullSync = false)
                    storeDataForDb.requestStoreCalibrationEntries()
                }
            }

            "profile"      -> appScope.launch { nsIncomingDataProcessor.processProfile(doc, doFullSync = false) }
            "treatments"   -> docString.toNSTreatment()?.let {
                nsIncomingDataProcessor.processTreatments(listOf(it), doFullSync = false)
                storeDataForDb.requestStoreTreatments(fullSync = false)
            }

            "foods"        -> docString.toNSFood()?.let {
                nsIncomingDataProcessor.processFood(listOf(it))
                storeDataForDb.requestStoreFoods()
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
        if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)) post(KotlinxNsAlarm(data))
    }

    internal fun onAlarm(raw: String, gate: BooleanKey) {
        val data = parse(raw) ?: return
        nsClientRepository.addLog("◄ ALARM", data.str("title") ?: "")
        if (preferences.get(gate)) post(KotlinxNsAlarm(data))
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
