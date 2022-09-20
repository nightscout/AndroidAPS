package info.nightscout.androidaps.plugins.sync.nsclient.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.work.OneTimeWorkRequest
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.events.EventConfigBuilderChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.food.FoodPlugin.FoodWorker
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin.NSClientSourceWorker
import info.nightscout.androidaps.plugins.sync.nsclient.NSClientAddAckWorker
import info.nightscout.androidaps.plugins.sync.nsclient.NSClientAddUpdateWorker
import info.nightscout.androidaps.plugins.sync.nsclient.NSClientMbgWorker
import info.nightscout.androidaps.plugins.sync.nsclient.NSClientPlugin
import info.nightscout.androidaps.plugins.sync.nsclient.NSClientUpdateRemoveAckWorker
import info.nightscout.androidaps.plugins.sync.nsclient.acks.NSAddAck
import info.nightscout.androidaps.plugins.sync.nsclient.acks.NSAuthAck
import info.nightscout.androidaps.plugins.sync.nsclient.acks.NSUpdateAck
import info.nightscout.androidaps.plugins.sync.nsclient.data.NSAlarm
import info.nightscout.androidaps.plugins.sync.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.sync.nsclient.data.NSSettingsStatus
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientStatus
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientUpdateGUI
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.JsonHelper.safeGetStringAllowNull
import info.nightscout.androidaps.utils.T.Companion.mins
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException
import java.util.*
import javax.inject.Inject

class NSClientService : DaggerService(), NsClient.NSClientService {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var nsDeviceStatus: NSDeviceStatus
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var config: Config
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var repository: AppRepository

    companion object {

        private const val WATCHDOG_INTERVAL_MINUTES = 2
        private const val WATCHDOG_RECONNECT_IN = 15
        private const val WATCHDOG_MAX_CONNECTIONS = 5
    }

    private val disposable = CompositeDisposable()

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
    private var lastAckTime: Long = 0
    private var nsApiHashCode = ""
    private val reconnections = ArrayList<Long>()

    var isConnected = false
    var hasWriteAuth = false
    var nsURL = ""
    var latestDateInReceivedData: Long = 0

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NSClientService")
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
                           if (event.isChanged(rh, R.string.key_nsclientinternal_url) ||
                               event.isChanged(rh, R.string.key_nsclientinternal_api_secret) ||
                               event.isChanged(rh, R.string.key_nsclientinternal_paused)
                           ) {
                               latestDateInReceivedData = 0
                               destroy()
                               initialize()
                           }
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
            .toObservable(NSUpdateAck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ack -> processUpdateAck(ack) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(NSAddAck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ack -> processAddAck(ack) }, fabricPrivacy::logException)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun processAddAck(ack: NSAddAck) {
        lastAckTime = dateUtil.now()
        dataWorker.enqueue(
            OneTimeWorkRequest.Builder(NSClientAddAckWorker::class.java)
                .setInputData(dataWorker.storeInputData(ack))
                .build()
        )
    }

    private fun processUpdateAck(ack: NSUpdateAck) {
        lastAckTime = dateUtil.now()
        dataWorker.enqueue(
            OneTimeWorkRequest.Builder(NSClientUpdateRemoveAckWorker::class.java)
                .setInputData(dataWorker.storeInputData(ack))
                .build()
        )
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
        rxBus.send(EventNSClientNewLog("AUTH", connectionStatus, NsClient.Version.V1))
        if (!ack.write) {
            rxBus.send(EventNSClientNewLog("ERROR", "Write permission not granted ", NsClient.Version.V1))
        }
        if (!ack.writeTreatment) {
            rxBus.send(EventNSClientNewLog("ERROR", "Write treatment permission not granted ", NsClient.Version.V1))
        }
        if (!hasWriteAuth) {
            val noWritePerm = Notification(Notification.NSCLIENT_NO_WRITE_PERMISSION, rh.gs(R.string.nowritepermission), Notification.URGENT)
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

    fun initialize() {
        dataCounter = 0
        readPreferences()
        @Suppress("UnstableApiUsage", "DEPRECATION")
        if (nsAPISecret != "") nsApiHashCode = Hashing.sha1().hashString(nsAPISecret, Charsets.UTF_8).toString()
        rxBus.send(EventNSClientStatus("Initializing"))
        if (!nsClientPlugin.isAllowed) {
            rxBus.send(EventNSClientNewLog("NSCLIENT", nsClientPlugin.blockingReason, NsClient.Version.V1))
            rxBus.send(EventNSClientStatus(nsClientPlugin.blockingReason))
        } else if (sp.getBoolean(R.string.key_nsclientinternal_paused, false)) {
            rxBus.send(EventNSClientNewLog("NSCLIENT", "paused", NsClient.Version.V1))
            rxBus.send(EventNSClientStatus("Paused"))
        } else if (!nsEnabled) {
            rxBus.send(EventNSClientNewLog("NSCLIENT", "disabled", NsClient.Version.V1))
            rxBus.send(EventNSClientStatus("Disabled"))
        } else if (nsURL != "" && (buildHelper.isEngineeringMode() || nsURL.lowercase(Locale.getDefault()).startsWith("https://"))) {
            try {
                rxBus.send(EventNSClientStatus("Connecting ..."))
                val opt = IO.Options()
                opt.forceNew = true
                opt.reconnection = true
                socket = IO.socket(nsURL, opt).also { socket ->
                    socket.on(Socket.EVENT_CONNECT, onConnect)
                    socket.on(Socket.EVENT_DISCONNECT, onDisconnect)
                    socket.on(Socket.EVENT_ERROR, onError)
                    socket.on(Socket.EVENT_CONNECT_ERROR, onError)
                    socket.on(Socket.EVENT_CONNECT_TIMEOUT, onError)
                    socket.on(Socket.EVENT_PING, onPing)
                    rxBus.send(EventNSClientNewLog("NSCLIENT", "do connect", NsClient.Version.V1))
                    socket.connect()
                    socket.on("dataUpdate", onDataUpdate)
                    socket.on("announcement", onAnnouncement)
                    socket.on("alarm", onAlarm)
                    socket.on("urgent_alarm", onUrgentAlarm)
                    socket.on("clear_alarm", onClearAlarm)
                }
            } catch (e: URISyntaxException) {
                rxBus.send(EventNSClientNewLog("NSCLIENT", "Wrong URL syntax", NsClient.Version.V1))
                rxBus.send(EventNSClientStatus("Wrong URL syntax"))
            } catch (e: RuntimeException) {
                rxBus.send(EventNSClientNewLog("NSCLIENT", "Wrong URL syntax", NsClient.Version.V1))
                rxBus.send(EventNSClientStatus("Wrong URL syntax"))
            }
        } else if (nsURL.lowercase(Locale.getDefault()).startsWith("http://")) {
            rxBus.send(EventNSClientNewLog("NSCLIENT", "NS URL not encrypted", NsClient.Version.V1))
            rxBus.send(EventNSClientStatus("Not encrypted"))
        } else {
            rxBus.send(EventNSClientNewLog("NSCLIENT", "No NS URL specified", NsClient.Version.V1))
            rxBus.send(EventNSClientStatus("Not configured"))
        }
    }

    private val onConnect = Emitter.Listener {
        connectCounter++
        val socketId = socket?.id() ?: "NULL"
        rxBus.send(EventNSClientNewLog("NSCLIENT", "connect #$connectCounter event. ID: $socketId", NsClient.Version.V1))
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
            rxBus.send(EventNSClientNewLog("WATCHDOG", "connections in last " + WATCHDOG_INTERVAL_MINUTES + " minutes: " + reconnections.size + "/" + WATCHDOG_MAX_CONNECTIONS, NsClient.Version.V1))
            if (reconnections.size >= WATCHDOG_MAX_CONNECTIONS) {
                val n = Notification(Notification.NS_MALFUNCTION, rh.gs(R.string.nsmalfunction), Notification.URGENT)
                rxBus.send(EventNewNotification(n))
                rxBus.send(EventNSClientNewLog("WATCHDOG", "pausing for $WATCHDOG_RECONNECT_IN minutes", NsClient.Version.V1))
                nsClientPlugin.pause(true)
                rxBus.send(EventNSClientUpdateGUI())
                Thread {
                    SystemClock.sleep(mins(WATCHDOG_RECONNECT_IN.toLong()).msecs())
                    rxBus.send(EventNSClientNewLog("WATCHDOG", "re-enabling NSClient", NsClient.Version.V1))
                    nsClientPlugin.pause(false)
                }.start()
            }
        }
    }

    private val onDisconnect = Emitter.Listener { args ->
        aapsLogger.debug(LTag.NSCLIENT, "disconnect reason: {}", *args)
        rxBus.send(EventNSClientNewLog("NSCLIENT", "disconnect event", NsClient.Version.V1))
    }

    @Synchronized fun destroy() {
        socket?.off(Socket.EVENT_CONNECT)
        socket?.off(Socket.EVENT_DISCONNECT)
        socket?.off(Socket.EVENT_PING)
        socket?.off("dataUpdate")
        socket?.off("announcement")
        socket?.off("alarm")
        socket?.off("urgent_alarm")
        socket?.off("clear_alarm")
        rxBus.send(EventNSClientNewLog("NSCLIENT", "destroy", NsClient.Version.V1))
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
        rxBus.send(EventNSClientNewLog("AUTH", "requesting auth", NsClient.Version.V1))
        socket?.emit("authorize", authMessage, ack)
    }

    private fun readPreferences() {
        nsEnabled = nsClientPlugin.isEnabled()
        nsURL = sp.getString(R.string.key_nsclientinternal_url, "")
        nsAPISecret = sp.getString(R.string.key_nsclientinternal_api_secret, "")
        nsDevice = sp.getString("careportal_enteredby", "")
    }

    private val onError = Emitter.Listener { args ->
        var msg = "Unknown Error"
        if (args.isNotEmpty() && args[0] != null) {
            msg = args[0].toString()
        }
        rxBus.send(EventNSClientNewLog("ERROR", msg, NsClient.Version.V1))
    }
    private val onPing = Emitter.Listener {
        rxBus.send(EventNSClientNewLog("PING", "received", NsClient.Version.V1))
        // send data if there is something waiting
        resend("Ping received")
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
            rxBus.send(EventNSClientNewLog("CLEARALARM", "received", NsClient.Version.V1))
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
                    rxBus.send(EventNSClientNewLog("DATA", "Data packet #" + dataCounter++ + if (isDelta) " delta" else " full", NsClient.Version.V1))
                    if (data.has("status")) {
                        val status = data.getJSONObject("status")
                        nsSettingsStatus.handleNewData(status)
                    } else if (!isDelta) {
                        rxBus.send(EventNSClientNewLog("ERROR", "Unsupported Nightscout version ", NsClient.Version.V1))
                    }
                    if (data.has("profiles")) {
                        val profiles = data.getJSONArray("profiles")
                        if (profiles.length() > 0) {
                            // take the newest
                            val profileStoreJson = profiles[profiles.length() - 1] as JSONObject
                            rxBus.send(EventNSClientNewLog("PROFILE", "profile received", NsClient.Version.V1))
                            dataWorker.enqueue(
                                OneTimeWorkRequest.Builder(LocalProfilePlugin.NSProfileWorker::class.java)
                                    .setInputData(dataWorker.storeInputData(profileStoreJson))
                                    .build()
                            )
                        }
                    }
                    if (data.has("treatments")) {
                        val treatments = data.getJSONArray("treatments")
                        val addedOrUpdatedTreatments = JSONArray()
                        if (treatments.length() > 0) rxBus.send(EventNSClientNewLog("DATA", "received " + treatments.length() + " treatments", NsClient.Version.V1))
                        for (index in 0 until treatments.length()) {
                            val jsonTreatment = treatments.getJSONObject(index)
                            val action = safeGetStringAllowNull(jsonTreatment, "action", null)
                            if (action == null) addedOrUpdatedTreatments.put(jsonTreatment)
                            else if (action == "update") addedOrUpdatedTreatments.put(jsonTreatment)
                        }
                        if (addedOrUpdatedTreatments.length() > 0) {
                            dataWorker.enqueue(
                                OneTimeWorkRequest.Builder(NSClientAddUpdateWorker::class.java)
                                    .setInputData(dataWorker.storeInputData(addedOrUpdatedTreatments))
                                    .build()
                            )
                        }
                    }
                    if (data.has("devicestatus")) {
                        val devicestatuses = data.getJSONArray("devicestatus")
                        if (devicestatuses.length() > 0) {
                            rxBus.send(EventNSClientNewLog("DATA", "received " + devicestatuses.length() + " device statuses", NsClient.Version.V1))
                            nsDeviceStatus.handleNewData(devicestatuses, NsClient.Version.V1)
                        }
                    }
                    if (data.has("food")) {
                        val foods = data.getJSONArray("food")
                        if (foods.length() > 0) rxBus.send(EventNSClientNewLog("DATA", "received " + foods.length() + " foods", NsClient.Version.V1))
                        dataWorker.enqueue(
                            OneTimeWorkRequest.Builder(FoodWorker::class.java)
                                .setInputData(dataWorker.storeInputData(foods))
                                .build()
                        )
                    }
                    if (data.has("mbgs")) {
                        val mbgArray = data.getJSONArray("mbgs")
                        if (mbgArray.length() > 0) rxBus.send(EventNSClientNewLog("DATA", "received " + mbgArray.length() + " mbgs", NsClient.Version.V1))
                        dataWorker.enqueue(
                            OneTimeWorkRequest.Builder(NSClientMbgWorker::class.java)
                                .setInputData(dataWorker.storeInputData(mbgArray))
                                .build()
                        )
                    }
                    if (data.has("cals")) {
                        val cals = data.getJSONArray("cals")
                        if (cals.length() > 0) rxBus.send(EventNSClientNewLog("DATA", "received " + cals.length() + " cals", NsClient.Version.V1))
                        // Calibrations ignored
                    }
                    if (data.has("sgvs")) {
                        val sgvs = data.getJSONArray("sgvs")
                        if (sgvs.length() > 0) {
                            rxBus.send(EventNSClientNewLog("DATA", "received " + sgvs.length() + " sgvs", NsClient.Version.V1))
                            // Objective0
                            sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
                            dataWorker.enqueue(
                                OneTimeWorkRequest.Builder(NSClientSourceWorker::class.java)
                                    .setInputData(dataWorker.storeInputData(sgvs))
                                    .build()
                            )
                        }
                    }
                    rxBus.send(EventNSClientNewLog("LAST", dateUtil.dateAndTimeString(latestDateInReceivedData), NsClient.Version.V1))
                } catch (e: JSONException) {
                    aapsLogger.error("Unhandled exception", e)
                }
                //rxBus.send(new EventNSClientNewLog("NSCLIENT", "onDataUpdate end");
            } finally {
                // if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    override fun dbUpdate(collection: String, _id: String?, data: JSONObject?, originalObject: Any, progress: String) {
        try {
            if (_id == null) return
            if (!isConnected || !hasWriteAuth) return
            val message = JSONObject()
            message.put("collection", collection)
            message.put("_id", _id)
            message.put("data", data)
            socket?.emit("dbUpdate", message, NSUpdateAck("dbUpdate", _id, aapsLogger, rxBus, originalObject))
            rxBus.send(
                EventNSClientNewLog(
                    "DBUPDATE $collection", "Sent " + originalObject.javaClass.simpleName + " " +
                        "" + _id + " " + data + progress
                    , NsClient.Version.V1)
            )
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    override fun dbAdd(collection: String, data: JSONObject, originalObject: Any, progress: String) {
        try {
            if (!isConnected || !hasWriteAuth) return
            val message = JSONObject()
            message.put("collection", collection)
            message.put("data", data)
            socket?.emit("dbAdd", message, NSAddAck(aapsLogger, rxBus, originalObject))
            rxBus.send(EventNSClientNewLog("DBADD $collection", "Sent " + originalObject.javaClass.simpleName + " " + data + " " + progress, NsClient.Version.V1))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    fun sendAlarmAck(alarmAck: info.nightscout.androidaps.plugins.sync.nsclient.data.AlarmAck) {
        if (!isConnected || !hasWriteAuth) return
        socket?.emit("ack", alarmAck.level, alarmAck.group, alarmAck.silenceTime)
        rxBus.send(EventNSClientNewLog("ALARMACK ", alarmAck.level.toString() + " " + alarmAck.group + " " + alarmAck.silenceTime, NsClient.Version.V1))
    }

    fun resend(reason: String) {
        if (!isConnected || !hasWriteAuth) return
        handler.post {
            if (socket?.connected() != true) return@post
            if (lastAckTime > System.currentTimeMillis() - 10 * 1000L) {
                aapsLogger.debug(LTag.NSCLIENT, "Skipping resend by lastAckTime: " + (System.currentTimeMillis() - lastAckTime) / 1000L + " sec")
                return@post
            }
            // val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            // val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            //     "AndroidAPS:NSClientService_onDataUpdate")
            // wakeLock.acquire(mins(10).msecs())
            try {
                rxBus.send(EventNSClientNewLog("QUEUE", "Resend started: $reason", NsClient.Version.V1))
                dataSyncSelector.doUpload()
                rxBus.send(EventNSClientNewLog("QUEUE", "Resend ended: $reason", NsClient.Version.V1))
            } finally {
                // if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    fun restart() {
        destroy()
        initialize()
    }

    private fun handleAnnouncement(announcement: JSONObject) {
        val defaultVal = config.NSCLIENT
        if (sp.getBoolean(R.string.key_ns_announcements, defaultVal)) {
            val nsAlarm = NSAlarm(announcement)
            val notification: Notification = NotificationWithAction(injector, nsAlarm)
            rxBus.send(EventNewNotification(notification))
            rxBus.send(EventNSClientNewLog("ANNOUNCEMENT", safeGetString(announcement, "message", "received"), NsClient.Version.V1))
            aapsLogger.debug(LTag.NSCLIENT, announcement.toString())
        }
    }

    private fun handleAlarm(alarm: JSONObject) {
        val defaultVal = config.NSCLIENT
        if (sp.getBoolean(R.string.key_ns_alarms, defaultVal)) {
            val snoozedTo = sp.getLong(R.string.key_snoozedTo, 0L)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val nsAlarm = NSAlarm(alarm)
                val notification: Notification = NotificationWithAction(injector, nsAlarm)
                rxBus.send(EventNewNotification(notification))
            }
            rxBus.send(EventNSClientNewLog("ALARM", safeGetString(alarm, "message", "received"), NsClient.Version.V1))
            aapsLogger.debug(LTag.NSCLIENT, alarm.toString())
        }
    }

    private fun handleUrgentAlarm(alarm: JSONObject) {
        val defaultVal = config.NSCLIENT
        if (sp.getBoolean(R.string.key_ns_alarms, defaultVal)) {
            val snoozedTo = sp.getLong(R.string.key_snoozedTo, 0L)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val nsAlarm = NSAlarm(alarm)
                val notification: Notification = NotificationWithAction(injector, nsAlarm)
                rxBus.send(EventNewNotification(notification))
            }
            rxBus.send(EventNSClientNewLog("URGENTALARM", safeGetString(alarm, "message", "received"), NsClient.Version.V1))
            aapsLogger.debug(LTag.NSCLIENT, alarm.toString())
        }
    }
}