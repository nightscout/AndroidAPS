package app.aaps.plugins.sync.nsclientV3.services

import app.aaps.core.ui.CoreUiStrings
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.OpenForTesting
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.mapper.toCalibrationMbg
import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.mapper.toNSFood
import app.aaps.core.nssdk.mapper.toNSSgvV3
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.core.objects.workflow.MetroService
import app.aaps.plugins.sync.nsclientV3.NSAlarmObject
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlPublisher
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.extensions.toRunningConfiguration
import app.aaps.plugins.sync.nsclientV3.json.JsonBridge.toKotlinxJson
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.plugins.sync.nsclientV3.ws.NsConnectHandler
import app.aaps.plugins.sync.nsclientV3.ws.NsFrameHandler
import app.aaps.plugins.sync.nsclientV3.ws.NsSocket
import app.aaps.plugins.sync.nsclientV3.ws.NsSocketFactory
import app.aaps.plugins.sync.nsclientV3.ws.ServiceNsConnection
import dev.zacsweers.metro.Inject
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Suppress("SpellCheckingInspection")
class NSClientV3Service : MetroService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var config: Config
    @Inject lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Inject lateinit var storeDataForDb: StoreDataForDb
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Inject lateinit var nsFrameHandler: NsFrameHandler
    @Inject lateinit var nsConnectHandler: NsConnectHandler
    @Inject lateinit var nsClientRepository: NSClientRepository
    @Inject lateinit var runningConfiguration: RunningConfiguration
    @Inject lateinit var orphanDetector: OrphanDetector
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    // The sockets are made here rather than handed in, so they are created, driven and closed on
    // this service, while it holds the wake lock.
    @Inject lateinit var nsSocketFactory: NsSocketFactory

    // The concrete type, not the NsConnection interface: the service reports its own websocket
    // state into it, which is not something every implementation of the port offers.
    @Inject lateinit var nsConnection: ServiceNsConnection


    private var wakeLock: PowerManager.WakeLock? = null
    private val binder: IBinder = LocalBinder(this)

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:NSClientService")
        wakeLock?.acquire()
        initializeWebSockets("onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdownWebsockets()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    class LocalBinder(service: NSClientV3Service) : Binder() {

        private val serviceRef = WeakReference(service)
        val serviceInstance: NSClientV3Service?
            get() = serviceRef.get()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int = START_STICKY

    var storageSocket: NsSocket? = null
    var alarmSocket: NsSocket? = null

    /**
     * WS connection state. Pass-through to [ServiceNsConnection] — that is the singleton which
     * survives service rebinds, so the canonical StateFlow lives there and UI subscribers don't get
     * torn down across service lifecycles.
     */
    internal var wsConnected: Boolean
        get() = nsConnection.connected.value
        set(value) = nsConnection.setConnected(value)

    @OpenForTesting
    fun shutdownWebsockets() {
        // close() drops the listeners and disconnects; the socket is not reused afterwards.
        storageSocket?.close()
        alarmSocket?.close()
        wsConnected = false
        storageSocket = null
        alarmSocket = null
    }

    @Suppress("SameParameterValue")
    fun initializeWebSockets(reason: String) {
        if (preferences.get(StringKey.NsClientUrl).isEmpty()) {
            shutdownWebsockets()
            return
        }
        if (!preferences.get(BooleanKey.NsClient3UseWs)) {
            shutdownWebsockets()
            return
        }
        if (!nsClientV3Plugin.isAllowed) {
            shutdownWebsockets()
            nsClientRepository.addLog("● WS", nsClientV3Plugin.blockingReason)
            return
        }
        if (preferences.get(NsclientBooleanKey.NsPaused)) {
            shutdownWebsockets()
            nsClientRepository.addLog("● WS", "paused")
            return
        }
        if (storageSocket != null) {
            nsClientRepository.addLog("● WS", "already initialized, skip $reason")
            return
        }
        val urlStorage = preferences.get(StringKey.NsClientUrl).lowercase().replace(Regex("/$"), "") + "/storage"
        val urlAlarm = preferences.get(StringKey.NsClientUrl).lowercase().replace(Regex("/$"), "") + "/alarm"
        try {
            // java io.client doesn't support multiplexing. create 2 sockets.
            // Assign the field BEFORE attaching listeners / connecting: the socket is already in
            // socket.io's process-static, never-pruned Manager.nsps cache once it exists, so if
            // anything below throws it must still be reachable by shutdownWebsockets(). Otherwise the
            // socket is orphaned in nsps with our listeners attached and leaks this service for the
            // process lifetime (LeakCanary: reconnect Timer → Manager.nsps →
            // Socket.callbacks["disconnect"] → this service).
            val storage = nsSocketFactory.create(urlStorage)
            if (storage == null) {
                shutdownWebsockets()
                nsClientRepository.addLog("● WS", "Wrong URL syntax")
                return
            }
            storageSocket = storage
            storage.on(NsSocket.EVENT_CONNECT, onConnectStorage)
            storage.on(NsSocket.EVENT_DISCONNECT, onDisconnectStorage)
            storage.on("create", onDataCreateUpdate)
            storage.on("update", onDataCreateUpdate)
            storage.on("delete", onDataDelete)
            nsClientRepository.addLog("► WS", "do connect storage $reason")
            storage.connect()
            if (preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements) ||
                preferences.get(BooleanKey.NsClientNotificationsFromAlarms)
            ) {
                val alarm = nsSocketFactory.create(urlAlarm)
                if (alarm == null) {
                    shutdownWebsockets()
                    nsClientRepository.addLog("● WS", "Wrong URL syntax")
                    return
                }
                alarmSocket = alarm
                alarm.on(NsSocket.EVENT_CONNECT, onConnectAlarms)
                alarm.on(NsSocket.EVENT_DISCONNECT, onDisconnectAlarm)
                alarm.on("announcement", onAnnouncement)
                alarm.on("alarm", onAlarm)
                alarm.on("urgent_alarm", onUrgentAlarm)
                alarm.on("clear_alarm", onClearAlarm)
                nsClientRepository.addLog("► WS", "do connect alarm $reason")
                alarm.connect()
            }
        } catch (e: RuntimeException) {
            shutdownWebsockets()
            nsClientRepository.addLog("● WS", "RuntimeException: ${e.message}")
        }
    }

    private val onConnectStorage: (String) -> Unit = { nsConnectHandler.onConnectStorage(storageSocket) { ok -> wsConnected = ok } }

    private val onConnectAlarms: (String) -> Unit = { nsConnectHandler.onConnectAlarms(alarmSocket) }

    private val onDisconnectStorage: (String) -> Unit = { reason -> nsConnectHandler.onDisconnectStorage(reason) { wsConnected = false } }

    private val onDisconnectAlarm: (String) -> Unit = { reason -> nsConnectHandler.onDisconnectAlarm(reason) }

    /** Acking an alarm back to Nightscout needs the alarm socket, which this service owns. */
    fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        alarmSocket?.emitAlarmAck(originalAlarm.level, originalAlarm.group, silenceTimeInMilliseconds)
        nsClientRepository.addLog("► ALARMACK ", "${originalAlarm.level} ${originalAlarm.group} $silenceTimeInMilliseconds")
    }

    // Frame handling is shared with every other platform - see NsFrameHandler. These stay as
    // properties with the same names and shapes so the socket wiring above and the characterization
    // tests in NSClientV3ServiceHandlersTest are unchanged: those tests now exercise the shared
    // routing, which is what proves it still does what this service used to do.
    internal val onDataCreateUpdate: (String) -> Unit = { raw -> nsFrameHandler.onDataCreateUpdate(raw) }
    internal val onDataDelete: (String) -> Unit = { raw -> nsFrameHandler.onDataDelete(raw) }
    internal val onAnnouncement: (String) -> Unit = { raw -> nsFrameHandler.onAnnouncement(raw) }
    internal val onAlarm: (String) -> Unit = { raw -> nsFrameHandler.onAlarm(raw) }
    internal val onUrgentAlarm: (String) -> Unit = { raw -> nsFrameHandler.onUrgentAlarm(raw) }
    internal val onClearAlarm: (String) -> Unit = { raw -> nsFrameHandler.onClearAlarm(raw) }
}
