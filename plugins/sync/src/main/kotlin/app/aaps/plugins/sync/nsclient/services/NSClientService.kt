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
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventDeviceStatusChange
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventRunningModeChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
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
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import app.aaps.plugins.sync.nsShared.events.EventNSClientStatus
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import app.aaps.plugins.sync.nsclient.DataSyncSelectorV1
import app.aaps.plugins.sync.nsclient.NSClientPlugin
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
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var config: Config
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var dataSyncSelectorV1: DataSyncSelectorV1
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var storeDataForDb: StoreDataForDb

    companion object {

        private const val WATCHDOG_INTERVAL_MINUTES = 2
        private const val WATCHDOG_RECONNECT_IN = 15
        private const val WATCHDOG_MAX_CONNECTIONS = 5
    }

    private val disposable = CompositeDisposable()
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
        disposable += rxBus
            .toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (nsEnabled != nsClientPlugin.isEnabled()) {
                               latestDateInReceivedData = 0
                               destroy()
                               initialize()
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventPreferenceChange ->
                           if (event.isChanged(StringKey.NsClientUrl.key) ||
                               event.isChanged(StringKey.NsClientApiSecret.key) ||
                               event.isChanged(NsclientBooleanKey.NsPaused.key)
                           ) {
                               latestDateInReceivedData = 0
                               destroy()
                               initialize()
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventConnectivityOptionChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           latestDateInReceivedData = 0
                           destroy()
                           initialize()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.NSCLIENT, "EventAppExit received")
                           destroy()
                           stopSelf()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientRestart::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           latestDateInReceivedData = 0
                           restart()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(NSAuthAck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ack -> processAuthAck(ack) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ resend("NEW_DATA") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ resend("EventDeviceStatusChange") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ resend("EventTempTargetChange") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ resend("EventProfileSwitchChanged") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ resend("EventTherapyEventChange") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRunningModeChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ resend("EventOfflineChange") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventProfileStoreChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ resend("EventProfileStoreChanged") }, fabricPrivacy::logException)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
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
        rxBus.send(EventNSClientStatus(connectionStatus))
        rxBus.send(EventNSClientNewLog("◄ AUTH", connectionStatus))
        if (!ack.write) {
            rxBus.send(EventNSClientNewLog("◄ ERROR", "Write permission not granted "))
        }
        if (!ack.writeTreatment) {
            rxBus.send(EventNSClientNewLog("◄ ERROR", "Write treatment permission not granted "))
        }
        if (!hasWriteAuth) {
            val noWritePerm = Notification(Notification.NSCLIENT_NO_WRITE_PERMISSION, rh.gs(R.string.no_write_permission), Notification.URGENT)
            rxBus.send(EventNewNotification(noWritePerm))
        } else {
            rxBus.send(EventDismissNotification(Notification.NSCLIENT_NO_WRITE_PERMISSION))
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
        rxBus.send(EventNSClientStatus("Initializing"))
        if (!nsClientPlugin.isAllowed) {
            rxBus.send(EventNSClientNewLog("● NSCLIENT", nsClientPlugin.blockingReason))
            rxBus.send(EventNSClientStatus(nsClientPlugin.blockingReason))
        } else if (preferences.get(NsclientBooleanKey.NsPaused)) {
            rxBus.send(EventNSClientNewLog("● NSCLIENT", "paused"))
            rxBus.send(EventNSClientStatus("Paused"))
        } else if (!nsEnabled) {
            rxBus.send(EventNSClientNewLog("● NSCLIENT", "disabled"))
            rxBus.send(EventNSClientStatus("Disabled"))
        } else if (nsURL != "" && (nsURL.lowercase(Locale.getDefault()).startsWith("https://"))) {
            try {
                rxBus.send(EventNSClientStatus("Connecting ..."))
                val opt = IO.Options().also { it.forceNew = true }
                socket = IO.socket(nsURL, opt).also { socket ->
                    socket.on(Socket.EVENT_CONNECT, onConnect)
                    socket.on(Socket.EVENT_DISCONNECT, onDisconnect)
                    rxBus.send(EventNSClientNewLog("● NSCLIENT", "do connect"))
                    socket.connect()
                    socket.on("dataUpdate", onDataUpdate)
                    socket.on("announcement", onAnnouncement)
                    socket.on("alarm", onAlarm)
                    socket.on("urgent_alarm", onUrgentAlarm)
                    socket.on("clear_alarm", onClearAlarm)
                }
            } catch (_: URISyntaxException) {
                rxBus.send(EventNSClientNewLog("● NSCLIENT", "Wrong URL syntax"))
                rxBus.send(EventNSClientStatus("Wrong URL syntax"))
            } catch (_: RuntimeException) {
                rxBus.send(EventNSClientNewLog("● NSCLIENT", "Wrong URL syntax"))
                rxBus.send(EventNSClientStatus("Wrong URL syntax"))
            }
        } else if (nsURL.lowercase(Locale.getDefault()).startsWith("http://")) {
            rxBus.send(EventNSClientNewLog("● NSCLIENT", "NS URL not encrypted"))
            rxBus.send(EventNSClientStatus("Not encrypted"))
        } else {
            rxBus.send(EventNSClientNewLog("● NSCLIENT", "No NS URL specified"))
            rxBus.send(EventNSClientStatus("Not configured"))
        }
    }

    private val onConnect = Emitter.Listener {
        connectCounter++
        val socketId = socket?.id() ?: "NULL"
        rxBus.send(EventNSClientNewLog("● NSCLIENT", "connect #$connectCounter event. ID: $socketId"))
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
            rxBus.send(EventNSClientNewLog("● WATCHDOG", "connections in last " + WATCHDOG_INTERVAL_MINUTES + " minutes: " + reconnections.size + "/" + WATCHDOG_MAX_CONNECTIONS))
            if (reconnections.size >= WATCHDOG_MAX_CONNECTIONS) {
                val n = Notification(Notification.NS_MALFUNCTION, rh.gs(R.string.ns_malfunction), Notification.URGENT)
                rxBus.send(EventNewNotification(n))
                rxBus.send(EventNSClientNewLog("● WATCHDOG", "pausing for $WATCHDOG_RECONNECT_IN minutes"))
                nsClientPlugin.pause(true)
                rxBus.send(EventNSClientUpdateGuiStatus())
                Thread {
                    SystemClock.sleep(mins(WATCHDOG_RECONNECT_IN.toLong()).msecs())
                    rxBus.send(EventNSClientNewLog("● WATCHDOG", "re-enabling NSClient"))
                    nsClientPlugin.pause(false)
                }.start()
            }
        }
    }

    private val onDisconnect = Emitter.Listener { args ->
        aapsLogger.debug(LTag.NSCLIENT, "disconnect reason: {}", *args)
        rxBus.send(EventNSClientNewLog("● NSCLIENT", "disconnect event"))
    }

    @Synchronized fun destroy() {
        socket?.off(Socket.EVENT_CONNECT)
        socket?.off(Socket.EVENT_DISCONNECT)
        socket?.off("dataUpdate")
        socket?.off("announcement")
        socket?.off("alarm")
        socket?.off("urgent_alarm")
        socket?.off("clear_alarm")
        rxBus.send(EventNSClientNewLog("● NSCLIENT", "destroy"))
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
        rxBus.send(EventNSClientNewLog("► AUTH", "requesting auth"))
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
            rxBus.send(EventNSClientNewLog("◄ CLEARALARM", "received"))
            rxBus.send(EventDismissNotification(Notification.NS_ALARM))
            rxBus.send(EventDismissNotification(Notification.NS_URGENT_ALARM))
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
                    rxBus.send(EventNSClientNewLog("◄ DATA", "Data packet #" + dataCounter++ + if (isDelta) " delta" else " full"))
                    if (data.has("status")) {
                        val status = data.getJSONObject("status")
                        nsSettingsStatus.handleNewData(status)
                    } else if (!isDelta) {
                        rxBus.send(EventNSClientNewLog("◄ ERROR", "Unsupported Nightscout version "))
                    }
                    if (data.has("profiles")) {
                        val profiles = data.getJSONArray("profiles")
                        if (profiles.length() > 0) {
                            // take the newest
                            val profileStoreJson = profiles[profiles.length() - 1] as JSONObject
                            rxBus.send(EventNSClientNewLog("◄ PROFILE", "profile received"))
                            nsIncomingDataProcessor.processProfile(profileStoreJson, false)
                        }
                    }
                    if (data.has("treatments")) {
                        val treatments = data.getJSONArray("treatments")
                        val addedOrUpdatedTreatments = JSONArray()
                        if (treatments.length() > 0) rxBus.send(EventNSClientNewLog("◄ DATA", "received " + treatments.length() + " treatments"))
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
                                rxBus.send(EventNSClientNewLog("◄ DATA", "received " + devicestatuses.size + " device statuses"))
                                nsDeviceStatusHandler.handleNewData(devicestatuses)
                            }
                        } catch (e: JSONException) {
                            aapsLogger.error(LTag.NSCLIENT, "Skipping invalid Nightscout devicestatus data; exception: $e")
                        }
                    }
                    if (data.has("food")) {
                        val foods = data.getJSONArray("food")
                        if (foods.length() > 0) rxBus.send(EventNSClientNewLog("◄ DATA", "received " + foods.length() + " foods"))
                        nsIncomingDataProcessor.processFood(foods)
                        storeDataForDb.storeFoodsToDb()
                    }
                    if (data.has("mbgs")) {
                        val mbgArray = data.getJSONArray("mbgs")
                        if (mbgArray.length() > 0) rxBus.send(EventNSClientNewLog("◄ DATA", "received " + mbgArray.length() + " mbgs"))
                        dataWorkerStorage.enqueue(
                            OneTimeWorkRequest.Builder(NSClientMbgWorker::class.java)
                                .setInputData(dataWorkerStorage.storeInputData(mbgArray))
                                .build()
                        )
                    }
                    if (data.has("cals")) {
                        val cals = data.getJSONArray("cals")
                        if (cals.length() > 0) rxBus.send(EventNSClientNewLog("◄ DATA", "received " + cals.length() + " cals"))
                        // Calibrations ignored
                    }
                    if (data.has("sgvs")) {
                        val sgvs = data.getJSONArray("sgvs")
                        if (sgvs.length() > 0) {
                            rxBus.send(EventNSClientNewLog("◄ DATA", "received " + sgvs.length() + " sgvs"))
                            nsIncomingDataProcessor.processSgvs(sgvs, false)
                            storeDataForDb.storeGlucoseValuesToDb()
                        }
                    }
                    rxBus.send(EventNSClientNewLog("◄ LAST", dateUtil.dateAndTimeString(latestDateInReceivedData)))
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

    fun dbUpdate(collection: String, @Suppress("LocalVariableName") _id: String?, data: JSONObject?, originalObject: Any, progress: String) {
        try {
            if (_id == null) return
            if (!isConnected || !hasWriteAuth) return
            val message = JSONObject()
            message.put("collection", collection)
            message.put("_id", _id)
            message.put("data", data)
            socket?.emit("dbUpdate", message, NSUpdateAck("dbUpdate", _id, aapsLogger, this, dateUtil, dataWorkerStorage, originalObject))
            rxBus.send(
                EventNSClientNewLog(
                    "► UPDATE $collection", "Sent " + originalObject.javaClass.simpleName + " " +
                        "" + _id + " " + data + progress
                )
            )
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
            rxBus.send(EventNSClientNewLog("► ADD $collection", "Sent " + originalObject.javaClass.simpleName + " " + data + " " + progress))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    fun sendAlarmAck(alarmAck: AlarmAck) {
        if (!isConnected || !hasWriteAuth) return
        socket?.emit("ack", alarmAck.level, alarmAck.group, alarmAck.silenceTime)
        rxBus.send(EventNSClientNewLog("► ALARMACK ", alarmAck.level.toString() + " " + alarmAck.group + " " + alarmAck.silenceTime))
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
            rxBus.send(EventNSClientNewLog("● QUEUE", "Resend started: $reason"))
            dataSyncSelectorV1.doUpload()
            rxBus.send(EventNSClientNewLog("● QUEUE", "Resend ended: $reason"))
        }.join()
    }

    fun restart() {
        destroy()
        initialize()
    }

    private fun handleAnnouncement(announcement: JSONObject) {
        if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)) {
            val nsAlarm = NSAlarmObject(announcement)
            uiInteraction.addNotificationWithAction(nsAlarm)
            rxBus.send(EventNSClientNewLog("◄ ANNOUNCEMENT", safeGetString(announcement, "message", "received")))
            aapsLogger.debug(LTag.NSCLIENT, announcement.toString())
        }
    }

    private fun handleAlarm(alarm: JSONObject) {
        if (preferences.get(BooleanKey.NsClientNotificationsFromAlarms)) {
            val snoozedTo = preferences.get(LongComposedKey.NotificationSnoozedTo, alarm.optString("level"))
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val nsAlarm = NSAlarmObject(alarm)
                uiInteraction.addNotificationWithAction(nsAlarm)
            }
            rxBus.send(EventNSClientNewLog("◄ ALARM", safeGetString(alarm, "message", "received")))
            aapsLogger.debug(LTag.NSCLIENT, alarm.toString())
        }
    }

    private fun handleUrgentAlarm(alarm: JSONObject) {
        if (preferences.get(BooleanKey.NsClientNotificationsFromAlarms)) {
            val snoozedTo = preferences.get(LongComposedKey.NotificationSnoozedTo, alarm.optString("level"))
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val nsAlarm = NSAlarmObject(alarm)
                uiInteraction.addNotificationWithAction(nsAlarm)
            }
            rxBus.send(EventNSClientNewLog("◄ URGENTALARM", safeGetString(alarm, "message", "received")))
            aapsLogger.debug(LTag.NSCLIENT, alarm.toString())
        }
    }
}
