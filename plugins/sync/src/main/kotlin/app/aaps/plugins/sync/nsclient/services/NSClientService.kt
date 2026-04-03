package app.aaps.plugins.sync.nsclient.services

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.OneTimeWorkRequest
import app.aaps.core.data.time.T.Companion.mins
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.utils.JsonHelper.safeGetString
import app.aaps.core.utils.JsonHelper.safeGetStringAllowNull
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.NSAlarmObject
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclient.DataSyncSelectorV1
import app.aaps.plugins.sync.nsclient.NSClientPlugin
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclient.acks.NSAddAck
import app.aaps.plugins.sync.nsclient.acks.NSAuthAck
import app.aaps.plugins.sync.nsclient.acks.NSUpdateAck
import app.aaps.plugins.sync.nsclient.data.AlarmAck
import app.aaps.plugins.sync.nsclient.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclient.workers.NSClientAddUpdateWorker
import app.aaps.plugins.sync.nsclient.workers.NSClientMbgWorker
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import dagger.android.DaggerService
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException
import java.util.Locale
import javax.inject.Inject

@Suppress("SpellCheckingInspection")
class NSClientService : DaggerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var config: Config
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var dataSyncSelectorV1: DataSyncSelectorV1
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var nsClientRepository: NSClientRepository
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var receiverDelegate: ReceiverDelegate

    companion object {

        private const val WATCHDOG_INTERVAL_MINUTES = 2
        private const val WATCHDOG_RECONNECT_IN = 15
        private const val WATCHDOG_MAX_CONNECTIONS = 5
    }

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var wakeLock: PowerManager.WakeLock? = null
    private val binder: IBinder = LocalBinder()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var socket: Socket? = null
    private var dataCounter = 0
    private var connectCounter = 0
    private var nsEnabled = false
    private var nsAPISecret = ""
    private var nsDevice = ""
    private val nsHours = 48
    internal var lastAckTime: Long = 0
    private var nsApiHashCode = ""
    private val reconnections = ArrayList<Long>()

    var isConnected = false
    var hasWriteAuth = false
    var nsURL = ""
    var latestDateInReceivedData: Long = 0

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NSClientService")
        wakeLock?.acquire()
        initialize()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        rxBus.toFlow(EventConfigBuilderChange::class.java)
            .onEach {
                if (nsEnabled != nsClientPlugin.isEnabled()) {
                    latestDateInReceivedData = 0
                    destroy()
                    initialize()
                }
            }.launchIn(scope)
        val restartOnChange: suspend (Any) -> Unit = {
            latestDateInReceivedData = 0
            destroy()
            initialize()
            nsClientRepository.updateUrl(preferences.get(StringKey.NsClientUrl))
        }
        preferences.observe(StringKey.NsClientUrl).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(StringKey.NsClientApiSecret).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(NsclientBooleanKey.NsPaused).drop(1).onEach(restartOnChange).launchIn(scope)
        receiverDelegate.connectivityStatusFlow
            .drop(1) // skip initial value
            .onEach {
                latestDateInReceivedData = 0
                destroy()
                initialize()
            }.launchIn(scope)
        rxBus.toFlow(EventAppExit::class.java)
            .onEach {
                aapsLogger.debug(LTag.NSCLIENT, "EventAppExit received")
                destroy()
                stopSelf()
            }.launchIn(scope)
        rxBus.toFlow(EventNSClientRestart::class.java)
            .onEach {
                latestDateInReceivedData = 0
                restart()
            }.launchIn(scope)
        rxBus.toFlow(NSAuthAck::class.java)
            .onEach { ack -> processAuthAck(ack) }.launchIn(scope)
        persistenceLayer.observeAnyChange()
            .onEach { types -> resend("DB_CHANGED(${types.joinToString { it.simpleName ?: "?" }})") }.launchIn(scope)
        rxBus.toFlow(EventProfileStoreChanged::class.java)
            .onEach { resend("EventProfileStoreChanged") }.launchIn(scope)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun processAuthAck(ack: NSAuthAck) {
        var connectionStatus = "Authenticated ("
        if (ack.read) connectionStatus += "R"
        if (ack.write) connectionStatus += "W"
        if (ack.writeTreatment) connectionStatus += "T"
        connectionStatus += ')'
        isConnected = true
        hasWriteAuth = ack.write && ack.writeTreatment
        nsClientRepository.updateStatus(connectionStatus)
        nsClientRepository.addLog("◄ AUTH", connectionStatus)
        if (!ack.write) {
            nsClientRepository.addLog("◄ ERROR", "Write permission not granted ")
        }
        if (!ack.writeTreatment) {
            nsClientRepository.addLog("◄ ERROR", "Write treatment permission not granted ")
        }
        if (!hasWriteAuth) {
            notificationManager.post(NotificationId.NSCLIENT_NO_WRITE_PERMISSION, R.string.no_write_permission, level = NotificationLevel.URGENT)
        } else {
            notificationManager.dismiss(NotificationId.NSCLIENT_NO_WRITE_PERMISSION)
        }
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: NSClientService
            get() = this@NSClientService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int = START_STICKY

    private fun initialize() {
        dataCounter = 0
        readPreferences()
        @Suppress("DEPRECATION")
        if (nsAPISecret != "") nsApiHashCode = Hashing.sha1().hashString(nsAPISecret, Charsets.UTF_8).toString()
        nsClientRepository.updateStatus("Initializing")
        if (!nsClientPlugin.isAllowed) {
            nsClientRepository.addLog("● NSCLIENT", nsClientPlugin.blockingReason)
            nsClientRepository.updateStatus(nsClientPlugin.blockingReason)
        } else if (preferences.get(NsclientBooleanKey.NsPaused)) {
            nsClientRepository.addLog("● NSCLIENT", "paused")
            nsClientRepository.updateStatus("Paused")
        } else if (!nsEnabled) {
            nsClientRepository.addLog("● NSCLIENT", "disabled")
            nsClientRepository.updateStatus("Disabled")
        } else if (nsURL != "" && (nsURL.lowercase(Locale.getDefault()).startsWith("https://"))) {
            try {
                nsClientRepository.updateStatus("Connecting ...")
                val opt = IO.Options().also { it.forceNew = true }
                socket = IO.socket(nsURL, opt).also { socket ->
                    socket.on(Socket.EVENT_CONNECT, onConnect)
                    socket.on(Socket.EVENT_DISCONNECT, onDisconnect)
                    nsClientRepository.addLog("● NSCLIENT", "do connect")
                    socket.connect()
                    socket.on("dataUpdate", onDataUpdate)
                    socket.on("announcement", onAnnouncement)
                    socket.on("alarm", onAlarm)
                    socket.on("urgent_alarm", onUrgentAlarm)
                    socket.on("clear_alarm", onClearAlarm)
                }
            } catch (_: URISyntaxException) {
                nsClientRepository.addLog("● NSCLIENT", "Wrong URL syntax")
                nsClientRepository.updateStatus("Wrong URL syntax")
            } catch (_: RuntimeException) {
                nsClientRepository.addLog("● NSCLIENT", "Wrong URL syntax")
                nsClientRepository.updateStatus("Wrong URL syntax")
            }
        } else if (nsURL.lowercase(Locale.getDefault()).startsWith("http://")) {
            nsClientRepository.addLog("● NSCLIENT", "NS URL not encrypted")
            nsClientRepository.updateStatus("Not encrypted")
        } else {
            nsClientRepository.addLog("● NSCLIENT", "No NS URL specified")
            nsClientRepository.updateStatus("Not configured")
        }
    }

    private val onConnect = Emitter.Listener {
        connectCounter++
        val socketId = socket?.id() ?: "NULL"
        nsClientRepository.addLog("● NSCLIENT", "connect #$connectCounter event. ID: $socketId")
        if (socket != null) sendAuthMessage(NSAuthAck(rxBus))
        watchdog()
    }

    private fun watchdog() {
        synchronized(reconnections) {
            val now = dateUtil.now()
            reconnections.add(now)
            for (r in reconnections.reversed()) {
                if (r < now - mins(WATCHDOG_INTERVAL_MINUTES.toLong()).msecs()) {
                    reconnections.remove(r)
                }
            }
            nsClientRepository.addLog("● WATCHDOG", "connections in last " + WATCHDOG_INTERVAL_MINUTES + " minutes: " + reconnections.size + "/" + WATCHDOG_MAX_CONNECTIONS)
            if (reconnections.size >= WATCHDOG_MAX_CONNECTIONS) {
                notificationManager.post(NotificationId.NS_MALFUNCTION, R.string.ns_malfunction)
                nsClientRepository.addLog("● WATCHDOG", "pausing for $WATCHDOG_RECONNECT_IN minutes")
                nsClientPlugin.pause(true)
                nsClientRepository.updateStatus(nsClientPlugin.status)
                Thread {
                    SystemClock.sleep(mins(WATCHDOG_RECONNECT_IN.toLong()).msecs())
                    nsClientRepository.addLog("● WATCHDOG", "re-enabling NSClient")
                    nsClientPlugin.pause(false)
                }.start()
            }
        }
    }

    private val onDisconnect = Emitter.Listener { args ->
        aapsLogger.debug(LTag.NSCLIENT, "disconnect reason: {}", *args)
        nsClientRepository.addLog("● NSCLIENT", "disconnect event")
    }

    @Synchronized fun destroy() {
        socket?.off(Socket.EVENT_CONNECT)
        socket?.off(Socket.EVENT_DISCONNECT)
        socket?.off("dataUpdate")
        socket?.off("announcement")
        socket?.off("alarm")
        socket?.off("urgent_alarm")
        socket?.off("clear_alarm")
        nsClientRepository.addLog("● NSCLIENT", "destroy")
        isConnected = false
        hasWriteAuth = false
        socket?.disconnect()
        socket = null
    }

    private fun sendAuthMessage(ack: NSAuthAck?) {
        val authMessage = JSONObject()
        try {
            authMessage.put("client", "Android_$nsDevice")
            authMessage.put("history", nsHours)
            authMessage.put("status", true) // receive status
            authMessage.put("from", latestDateInReceivedData) // send data newer than
            authMessage.put("secret", nsApiHashCode)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
            return
        }
        nsClientRepository.addLog("► AUTH", "requesting auth")
        socket?.emit("authorize", authMessage, ack)
    }

    private fun readPreferences() {
        nsEnabled = nsClientPlugin.isEnabled()
        nsURL = preferences.get(StringKey.NsClientUrl)
        nsAPISecret = preferences.get(StringKey.NsClientApiSecret)
        nsDevice = "AAPS"
    }

    private val onAnnouncement = Emitter.Listener { args ->

        /*
        {
        "level":0,
        "title":"Announcement",
        "message":"test",
        "plugin":{"name":"treatmentnotify","label":"Treatment Notifications","pluginType":"notification","enabled":true},
        "group":"Announcement",
        "isAnnouncement":true,
        "key":"9ac46ad9a1dcda79dd87dae418fce0e7955c68da"
        }
         */
        val data: JSONObject
        try {
            data = args[0] as JSONObject
            handleAnnouncement(data)
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }
    private val onAlarm = Emitter.Listener { args ->

        /*
        {
        "level":1,
        "title":"Warning HIGH",
        "message":"BG Now: 5 -0.2 → mmol\/L\nRaw BG: 4.8 mmol\/L Čistý\nBG 15m: 4.8 mmol\/L\nIOB: -0.02U\nCOB: 0g",
        "eventName":"high",
        "plugin":{"name":"simplealarms","label":"Simple Alarms","pluginType":"notification","enabled":true},
        "pushoverSound":"climb",
        "debug":{"lastSGV":5,"thresholds":{"bgHigh":180,"bgTargetTop":75,"bgTargetBottom":72,"bgLow":70}},
        "group":"default",
        "key":"simplealarms_1"
        }
         */
        val data: JSONObject
        try {
            data = args[0] as JSONObject
            handleAlarm(data)
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }
    private val onUrgentAlarm = Emitter.Listener { args: Array<Any> ->
        val data: JSONObject
        try {
            data = args[0] as JSONObject
            handleUrgentAlarm(data)
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }
    private val onClearAlarm = Emitter.Listener { args ->

        /*
        {
        "clear":true,
        "title":"All Clear",
        "message":"default - Urgent was ack'd",
        "group":"default"
        }
         */
        val data: JSONObject
        try {
            data = args[0] as JSONObject
            nsClientRepository.addLog("◄ CLEARALARM", "received")
            notificationManager.dismiss(NotificationId.NS_ALARM)
            notificationManager.dismiss(NotificationId.NS_URGENT_ALARM)
            aapsLogger.debug(LTag.NSCLIENT, data.toString())
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }
    private val onDataUpdate = Emitter.Listener { args ->
        handler.post {
            // val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            // val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            //     "AndroidAPS:NSClientService_onDataUpdate")
            // wakeLock.acquire(3000)
            try {
                val data = args[0] as JSONObject
                try {
                    // delta means only increment/changes are coming
                    val isDelta = data.has("delta")
                    nsClientRepository.addLog("◄ DATA", "Data packet #" + dataCounter++ + if (isDelta) " delta" else " full")
                    if (data.has("status")) {
                        val status = data.getJSONObject("status")
                        nsSettingsStatus.handleNewData(status)
                    } else if (!isDelta) {
                        nsClientRepository.addLog("◄ ERROR", "Unsupported Nightscout version ")
                    }
                    if (data.has("profiles")) {
                        val profiles = data.getJSONArray("profiles")
                        if (profiles.length() > 0) {
                            // take the newest
                            val profileStoreJson = profiles[profiles.length() - 1] as JSONObject
                            nsClientRepository.addLog("◄ PROFILE", "profile received")
                            nsIncomingDataProcessor.processProfile(profileStoreJson, false)
                        }
                    }
                    if (data.has("treatments")) {
                        val treatments = data.getJSONArray("treatments")
                        val addedOrUpdatedTreatments = JSONArray()
                        if (treatments.length() > 0) nsClientRepository.addLog("◄ DATA", "received " + treatments.length() + " treatments")
                        for (index in 0 until treatments.length()) {
                            val jsonTreatment = treatments.getJSONObject(index)
                            val action = safeGetStringAllowNull(jsonTreatment, "action", null)
                            if (action == null) addedOrUpdatedTreatments.put(jsonTreatment)
                            else if (action == "update") addedOrUpdatedTreatments.put(jsonTreatment)
                        }
                        if (addedOrUpdatedTreatments.length() > 0) {
                            dataWorkerStorage.enqueue(
                                OneTimeWorkRequest.Builder(NSClientAddUpdateWorker::class.java)
                                    .setInputData(dataWorkerStorage.storeInputData(addedOrUpdatedTreatments))
                                    .build()
                            )
                        }
                    }
                    if (data.has("devicestatus")) {
                        val deserializer: JsonDeserializer<JSONObject?> =
                            JsonDeserializer<JSONObject?> { json, _, _ ->
                                JSONObject(json.asJsonObject.toString())
                            }
                        val gson = GsonBuilder().also {
                            it.registerTypeAdapter(JSONObject::class.java, deserializer)
                        }.create()

                        try {
                            // "Live-patch" the JSON data if the battery value is not an integer.
                            // This has caused crashes in the past due to parsing errors. See:
                            // https://github.com/nightscout/AndroidAPS/issues/2223
                            // The reason is that the "battery" data type has been changed:
                            // https://github.com/NightscoutFoundation/xDrip/pull/1709
                            //
                            // Since we cannot reliably derive an integer percentage out
                            // of an arbitrary string, we are forced to replace that string
                            // with a hardcoded percentage. That way, at least, the
                            // subsequent GSON parsing won't crash.
                            val devicestatusJsonArray = data.getJSONArray("devicestatus")
                            for (arrayIndex in 0 until devicestatusJsonArray.length()) {
                                val devicestatusObject = devicestatusJsonArray.getJSONObject(arrayIndex)
                                if (devicestatusObject.has("uploader")) {
                                    val uploaderObject = devicestatusObject.getJSONObject("uploader")
                                    if (uploaderObject.has("battery")) {
                                        val batteryValue = uploaderObject["battery"]
                                        if (batteryValue !is Int) {
                                            aapsLogger.warn(
                                                LTag.NSCLIENT,
                                                "JSON devicestatus object #$arrayIndex (out of ${devicestatusJsonArray.length()}) " +
                                                    "has invalid value \"$batteryValue\" (expected integer); replacing with hardcoded integer 100"
                                            )
                                            uploaderObject.put("battery", 100)
                                        }
                                    }
                                }
                            }

                            val devicestatuses = try {
                                gson.fromJson(data.getString("devicestatus"), Array<NSDeviceStatus>::class.java)
                            } catch (_: Exception) {
                                emptyArray<NSDeviceStatus>()
                            }
                            if (devicestatuses.isNotEmpty()) {
                                nsClientRepository.addLog("◄ DATA", "received " + devicestatuses.size + " device statuses")
                                nsDeviceStatusHandler.handleNewData(devicestatuses)
                            }
                        } catch (e: JSONException) {
                            aapsLogger.error(LTag.NSCLIENT, "Skipping invalid Nightscout devicestatus data; exception: $e")
                        }
                    }
                    if (data.has("food")) {
                        val foods = data.getJSONArray("food")
                        if (foods.length() > 0) nsClientRepository.addLog("◄ DATA", "received " + foods.length() + " foods")
                        nsIncomingDataProcessor.processFood(foods)
                        storeDataForDb.storeFoodsToDb()
                    }
                    if (data.has("mbgs")) {
                        val mbgArray = data.getJSONArray("mbgs")
                        if (mbgArray.length() > 0) nsClientRepository.addLog("◄ DATA", "received " + mbgArray.length() + " mbgs")
                        dataWorkerStorage.enqueue(
                            OneTimeWorkRequest.Builder(NSClientMbgWorker::class.java)
                                .setInputData(dataWorkerStorage.storeInputData(mbgArray))
                                .build()
                        )
                    }
                    if (data.has("cals")) {
                        val cals = data.getJSONArray("cals")
                        if (cals.length() > 0) nsClientRepository.addLog("◄ DATA", "received " + cals.length() + " cals")
                        // Calibrations ignored
                    }
                    if (data.has("sgvs")) {
                        val sgvs = data.getJSONArray("sgvs")
                        if (sgvs.length() > 0) {
                            nsClientRepository.addLog("◄ DATA", "received " + sgvs.length() + " sgvs")
                            nsIncomingDataProcessor.processSgvs(sgvs, false)
                            storeDataForDb.storeGlucoseValuesToDb()
                        }
                    }
                    nsClientRepository.addLog("◄ LAST", dateUtil.dateAndTimeString(latestDateInReceivedData))
                    resend("LAST")
                } catch (e: JSONException) {
                    aapsLogger.error("Unhandled exception", e)
                }
                //rxBus.send(new EventNSClientNewLog("NSCLIENT", "onDataUpdate end");
            } finally {
                // if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    fun dbUpdate(collection: String, @Suppress("LocalVariableName") _id: String?, data: JSONObject, originalObject: Any, progress: String) {
        try {
            if (_id == null) return
            if (!isConnected || !hasWriteAuth) return
            val message = JSONObject()
            message.put("collection", collection)
            message.put("_id", _id)
            message.put("data", data)
            socket?.emit("dbUpdate", message, NSUpdateAck("dbUpdate", _id, aapsLogger, this, dateUtil, dataWorkerStorage, originalObject))
            nsClientRepository.addLog("► UPDATE $collection", "Sent ${originalObject.javaClass.simpleName} $_id $progress", data)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    fun dbAdd(collection: String, data: JSONObject, originalObject: Any, progress: String) {
        try {
            if (!isConnected || !hasWriteAuth) return
            val message = JSONObject()
            message.put("collection", collection)
            message.put("data", data)
            socket?.emit("dbAdd", message, NSAddAck(aapsLogger, rxBus, this, dateUtil, dataWorkerStorage, originalObject))
            nsClientRepository.addLog("► ADD $collection", "Sent " + originalObject.javaClass.simpleName + " " + progress, data)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    fun sendAlarmAck(alarmAck: AlarmAck) {
        if (!isConnected || !hasWriteAuth) return
        socket?.emit("ack", alarmAck.level, alarmAck.group, alarmAck.silenceTime)
        nsClientRepository.addLog("► ALARMACK ", alarmAck.level.toString() + " " + alarmAck.group + " " + alarmAck.silenceTime)
    }

    @Synchronized
    fun resend(reason: String) = runBlocking {
        if (!isConnected || !hasWriteAuth) return@runBlocking
        scope.async {
            if (socket?.connected() != true) return@async
            // if (lastAckTime > System.currentTimeMillis() - 10 * 1000L) {
            //     aapsLogger.debug(LTag.NSCLIENT, "Skipping resend by lastAckTime: " + (System.currentTimeMillis() - lastAckTime) / 1000L + " sec")
            //     return@async
            // }
            nsClientRepository.addLog("● QUEUE", "Resend started: $reason")
            dataSyncSelectorV1.doUpload()
            nsClientRepository.addLog("● QUEUE", "Resend ended: $reason")
        }.join()
    }

    fun restart() {
        destroy()
        initialize()
    }

    private fun handleAnnouncement(announcement: JSONObject) {
        if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)) {
            val nsAlarm = NSAlarmObject(announcement)
            postNsAlarm(nsAlarm)
            nsClientRepository.addLog("◄ ANNOUNCEMENT", safeGetString(announcement, "message", "received"))
            aapsLogger.debug(LTag.NSCLIENT, announcement.toString())
        }
    }

    private fun handleAlarm(alarm: JSONObject) {
        if (preferences.get(BooleanKey.NsClientNotificationsFromAlarms)) {
            val snoozedTo = preferences.get(LongComposedKey.NotificationSnoozedTo, alarm.optString("level"))
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val nsAlarm = NSAlarmObject(alarm)
                postNsAlarm(nsAlarm)
            }
            nsClientRepository.addLog("◄ ALARM", safeGetString(alarm, "message", "received"))
            aapsLogger.debug(LTag.NSCLIENT, alarm.toString())
        }
    }

    private fun handleUrgentAlarm(alarm: JSONObject) {
        if (preferences.get(BooleanKey.NsClientNotificationsFromAlarms)) {
            val snoozedTo = preferences.get(LongComposedKey.NotificationSnoozedTo, alarm.optString("level"))
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val nsAlarm = NSAlarmObject(alarm)
                postNsAlarm(nsAlarm)
            }
            nsClientRepository.addLog("◄ URGENTALARM", safeGetString(alarm, "message", "received"))
            aapsLogger.debug(LTag.NSCLIENT, alarm.toString())
        }
    }

    private fun snoozeActions(nsAlarm: NSAlarmObject): List<NotificationAction> =
        listOf(15, 30, 60).map { minutes ->
            val labelRes = when (minutes) {
                15   -> app.aaps.core.ui.R.string.snooze_15m
                30   -> app.aaps.core.ui.R.string.snooze_30m
                else -> app.aaps.core.ui.R.string.snooze_60m
            }
            NotificationAction(labelRes) {
                activePlugin.activeNsClient?.handleClearAlarm(nsAlarm, minutes * 60 * 1000L)
                preferences.put(LongComposedKey.NotificationSnoozedTo, nsAlarm.level.toString(), value = System.currentTimeMillis() + minutes * 60 * 1000L)
            }
        }

    private fun postNsAlarm(nsAlarm: NSAlarmObject) {
        when (nsAlarm.level) {
            0    -> notificationManager.post(
                id = NotificationId.NS_ANNOUNCEMENT,
                text = nsAlarm.message,
                level = NotificationLevel.ANNOUNCEMENT,
                validTo = System.currentTimeMillis() + mins(60).msecs(),
                actions = snoozeActions(nsAlarm)
            )

            1    -> notificationManager.post(
                id = NotificationId.NS_ALARM,
                text = nsAlarm.title,
                level = NotificationLevel.NORMAL,
                soundRes = app.aaps.core.ui.R.raw.alarm,
                actions = snoozeActions(nsAlarm)
            )

            2    -> notificationManager.post(
                id = NotificationId.NS_URGENT_ALARM,
                text = nsAlarm.title,
                level = NotificationLevel.URGENT,
                soundRes = app.aaps.core.ui.R.raw.urgentalarm,
                actions = snoozeActions(nsAlarm)
            )

            else -> return
        }
    }
}
