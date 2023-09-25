package info.nightscout.plugins.sync.nsclientV3

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventDeviceStatusChange
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventOfflineChange
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.mapper.toNSFood
import app.aaps.core.nssdk.mapper.toNSSgvV3
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.interfaces.TraceableDBEntry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.android.HasAndroidInjector
import info.nightscout.database.impl.AppRepository
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsShared.NSAlarmObject
import info.nightscout.plugins.sync.nsShared.NSClientFragment
import info.nightscout.plugins.sync.nsShared.NsIncomingDataProcessor
import info.nightscout.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiData
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import info.nightscout.plugins.sync.nsclient.ReceiverDelegate
import info.nightscout.plugins.sync.nsclient.data.NSDeviceStatusHandler
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSBolus
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSBolusWizard
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSCarbs
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSDeviceStatus
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSEffectiveProfileSwitch
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSExtendedBolus
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSFood
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSOfflineEvent
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSProfileSwitch
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSSvgV3
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSTemporaryBasal
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSTemporaryTarget
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSTherapyEvent
import info.nightscout.plugins.sync.nsclientV3.workers.DataSyncWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadBgWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadStatusWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException
import java.security.InvalidParameterException
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@OpenForTesting
@Singleton
class NSClientV3Plugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val sp: SP,
    private val receiverDelegate: ReceiverDelegate,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val dataSyncSelectorV3: DataSyncSelectorV3,
    private val repository: AppRepository,
    private val nsDeviceStatusHandler: NSDeviceStatusHandler,
    private val nsClientSource: NSClientSource,
    private val nsIncomingDataProcessor: NsIncomingDataProcessor,
    private val storeDataForDb: StoreDataForDb,
    private val decimalFormatter: DecimalFormatter
) : NsClient, Sync, PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(NSClientFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_nightscout_syncs)
        .pluginName(R.string.ns_client_v3)
        .shortName(R.string.ns_client_v3_short_name)
        .preferencesId(R.xml.pref_ns_client_v3)
        .description(R.string.description_ns_client_v3),
    aapsLogger, rh, injector
) {

    @Suppress("PropertyName")
    val JOB_NAME: String = this::class.java.simpleName

    companion object {

        const val RECORDS_TO_LOAD = 500
    }

    private val disposable = CompositeDisposable()
    private lateinit var runLoop: Runnable
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    override val listLog: MutableList<EventNSClientNewLog> = ArrayList()
    override val dataSyncSelector: DataSyncSelector get() = dataSyncSelectorV3
    override val status
        get() =
            when {
                sp.getBoolean(R.string.key_ns_paused, false)                                           -> rh.gs(app.aaps.core.ui.R.string.paused)
                isAllowed.not()                                                                        -> blockingReason
                sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_use_ws, true) && wsConnected  -> "WS: " + rh.gs(app.aaps.core.interfaces.R.string.connected)
                sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_use_ws, true) && !wsConnected -> "WS: " + rh.gs(R.string.not_connected)
                lastOperationError != null                                                             -> rh.gs(app.aaps.core.ui.R.string.error)
                nsAndroidClient?.lastStatus == null                                                    -> rh.gs(R.string.not_connected)
                workIsRunning()                                                                        -> rh.gs(R.string.working)
                nsAndroidClient?.lastStatus?.apiPermissions?.isFull() == true                          -> rh.gs(app.aaps.core.interfaces.R.string.connected)
                nsAndroidClient?.lastStatus?.apiPermissions?.isRead() == true                          -> rh.gs(R.string.read_only)
                else                                                                                   -> rh.gs(app.aaps.core.ui.R.string.unknown)
            }
    var lastOperationError: String? = null

    internal var nsAndroidClient: NSAndroidClient? = null

    private val isAllowed get() = receiverDelegate.allowed
    private val blockingReason get() = receiverDelegate.blockingReason

    val maxAge = T.days(100).msecs()
    internal var newestDataOnServer: LastModified? = null // timestamp of last modification for every collection provided by server
    internal var lastLoadedSrvModified = LastModified(LastModified.Collections()) // max srvLastModified timestamp of last fetched data for every collection
    internal var firstLoadContinueTimestamp = LastModified(LastModified.Collections()) // timestamp of last fetched data for every collection during initial load

    override fun onStart() {
        super.onStart()

        lastLoadedSrvModified = Json.decodeFromString(
            sp.getString(
                R.string.key_ns_client_v3_last_modified,
                Json.encodeToString(LastModified.serializer(), LastModified(LastModified.Collections()))
            )
        )

        setClient("START")

        receiverDelegate.grabReceiversState()
        disposable += rxBus
            .toObservable(EventConnectivityOptionChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev ->
                           rxBus.send(EventNSClientNewLog("● CONNECTIVITY", ev.blockingReason))
                           setClient("CONNECTIVITY")
                           if (isAllowed) executeLoop("CONNECTIVITY", forceNew = false)
                           rxBus.send(EventNSClientUpdateGuiStatus())
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev ->
                           if (ev.isChanged(rh.gs(R.string.key_ns_client_token)) ||
                               ev.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_nsclientinternal_url)) ||
                               ev.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_ns_use_ws)) ||
                               ev.isChanged(rh.gs(R.string.key_ns_paused)) ||
                               ev.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_ns_alarms)) ||
                               ev.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_ns_announcements))
                           )
                               setClient("SETTING CHANGE")
                           if (ev.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_local_profile_last_change)))
                               executeUpload("PROFILE_CHANGE", forceNew = true)

                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ WorkManager.getInstance(context).cancelUniqueWork(JOB_NAME) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientNewLog::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           addToLog(event)
                           aapsLogger.debug(LTag.NSCLIENT, event.action + " " + event.logText)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ executeUpload("NEW_DATA", forceNew = false) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ executeUpload("EventTempTargetChange", forceNew = false) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ executeUpload("EventProfileSwitchChanged", forceNew = false) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ executeUpload("EventDeviceStatusChange", forceNew = false) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ executeUpload("EventTherapyEventChange", forceNew = false) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOfflineChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ executeUpload("EventOfflineChange", forceNew = false) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventProfileStoreChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ executeUpload("EventProfileStoreChanged", forceNew = false) }, fabricPrivacy::logException)

        runLoop = Runnable {
            var refreshInterval = T.mins(5).msecs()
            if (nsClientSource.isEnabled())
                repository.getLastGlucoseValueWrapped().blockingGet().let {
                    // if last value is older than 5 min or there is no bg
                    if (it is ValueWrapper.Existing) {
                        if (it.value.timestamp < dateUtil.now() - T.mins(5).plus(T.secs(20)).msecs()) {
                            refreshInterval = T.mins(1).msecs()
                        }
                    }
                }
            if (!sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_use_ws, true))
                executeLoop("MAIN_LOOP", forceNew = true)
            else
                rxBus.send(EventNSClientNewLog("● TICK", ""))
            handler.postDelayed(runLoop, refreshInterval)
        }
        handler.postDelayed(runLoop, T.mins(2).msecs())
    }

    fun scheduleIrregularExecution(refreshToken: Boolean = false) {
        if (refreshToken) {
            handler.post { executeLoop("REFRESH TOKEN", forceNew = true) }
            return
        }
        if (config.NSCLIENT || nsClientSource.isEnabled()) {
            var origin = "5_MIN_AFTER_BG"
            var forceNew = true
            var toTime = lastLoadedSrvModified.collections.entries + T.mins(5).plus(T.secs(10)).msecs()
            if (toTime < dateUtil.now()) {
                toTime = dateUtil.now() + T.mins(1).plus(T.secs(0)).msecs()
                origin = "1_MIN_OLD_DATA"
                forceNew = false
            }
            handler.postDelayed({ executeLoop(origin, forceNew = forceNew) }, toTime - dateUtil.now())
            rxBus.send(EventNSClientNewLog("● NEXT", dateUtil.dateAndTimeAndSecondsString(toTime)))
        }
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        disposable.clear()
        storageSocket?.disconnect()
        alarmSocket?.disconnect()
        storageSocket = null
        alarmSocket = null
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            preferenceFragment.findPreference<PreferenceScreen>(rh.gs(R.string.ns_sync_options))?.isVisible = false

            preferenceFragment.findPreference<SwitchPreference>(rh.gs(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_errors))?.isVisible = false
            preferenceFragment.findPreference<SwitchPreference>(rh.gs(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_carbs_req))?.isVisible = false
        }
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_receive_tbr_eb))?.isVisible = config.isEngineeringMode()
    }

    override val hasWritePermission: Boolean get() = nsAndroidClient?.lastStatus?.apiPermissions?.isFull() ?: false
    override val connected: Boolean get() = nsAndroidClient?.lastStatus != null
    private fun addToLog(ev: EventNSClientNewLog) {
        synchronized(listLog) {
            listLog.add(0, ev)
            // remove the first line if log is too large
            if (listLog.size >= Constants.MAX_LOG_LINES) {
                listLog.removeAt(listLog.size - 1)
            }
            rxBus.send(EventNSClientUpdateGuiData())
        }
    }

    private fun setClient(reason: String) {
        nsAndroidClient = NSAndroidClientImpl(
            baseUrl = sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "").lowercase().replace("https://", "").replace(Regex("/$"), ""),
            accessToken = sp.getString(R.string.key_ns_client_token, ""),
            context = context,
            logging = true,
            logger = { msg -> aapsLogger.debug(LTag.HTTP, msg) }
        )
        if (wsConnected) {
            storageSocket?.disconnect()
            alarmSocket?.disconnect()
            storageSocket = null
            alarmSocket = null
        }
        SystemClock.sleep(2000)
        initializeWebSockets(reason)
        rxBus.send(EventSWSyncStatus(status))
    }

    /**********************
    WS code
     **********************/
    private var storageSocket: Socket? = null
    private var alarmSocket: Socket? = null
    var wsConnected = false
    internal var initialLoadFinished = false
    private fun initializeWebSockets(reason: String) {
        if (!sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_use_ws, true)) return
        if (sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "").isEmpty()) return
        val urlStorage = sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "").lowercase().replace(Regex("/$"), "") + "/storage"
        val urlAlarm = sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "").lowercase().replace(Regex("/$"), "") + "/alarm"
        if (!isAllowed) {
            rxBus.send(EventNSClientNewLog("● WS", blockingReason))
        } else if (sp.getBoolean(R.string.key_ns_paused, false)) {
            rxBus.send(EventNSClientNewLog("● WS", "paused"))
        } else {
            try {
                // java io.client doesn't support multiplexing. create 2 sockets
                storageSocket = IO.socket(urlStorage).also { socket ->
                    socket.on(Socket.EVENT_CONNECT, onConnectStorage)
                    socket.on(Socket.EVENT_DISCONNECT, onDisconnectStorage)
                    rxBus.send(EventNSClientNewLog("► WS", "do connect storage $reason"))
                    socket.connect()
                    socket.on("create", onDataCreateUpdate)
                    socket.on("update", onDataCreateUpdate)
                    socket.on("delete", onDataDelete)
                }
                if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_announcements, config.NSCLIENT) ||
                    sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_alarms, config.NSCLIENT)
                )
                    alarmSocket = IO.socket(urlAlarm).also { socket ->
                        socket.on(Socket.EVENT_CONNECT, onConnectAlarms)
                        socket.on(Socket.EVENT_DISCONNECT, onDisconnectAlarm)
                        rxBus.send(EventNSClientNewLog("► WS", "do connect alarm $reason"))
                        socket.connect()
                        socket.on("announcement", onAnnouncement)
                        socket.on("alarm", onAlarm)
                        socket.on("urgent_alarm", onUrgentAlarm)
                        socket.on("clear_alarm", onClearAlarm)
                    }
            } catch (e: URISyntaxException) {
                rxBus.send(EventNSClientNewLog("● WS", "Wrong URL syntax"))
            } catch (e: RuntimeException) {
                rxBus.send(EventNSClientNewLog("● WS", "RuntimeException"))
            }
        }
    }

    private val onConnectStorage = Emitter.Listener {
        val socketId = storageSocket?.id() ?: "NULL"
        rxBus.send(EventNSClientNewLog("◄ WS", "connected storage ID: $socketId"))
        if (storageSocket != null) {
            val authMessage = JSONObject().also {
                it.put("accessToken", sp.getString(R.string.key_ns_client_token, ""))
                it.put("collections", JSONArray(arrayOf("devicestatus", "entries", "profile", "treatments", "foods", "settings")))
            }
            rxBus.send(EventNSClientNewLog("► WS", "requesting auth for storage"))
            storageSocket?.emit("subscribe", authMessage, Ack { args ->
                val response = args[0] as JSONObject
                wsConnected = if (response.optBoolean("success")) {
                    rxBus.send(EventNSClientNewLog("◄ WS", "Subscribed for: ${response.optString("collections")}"))
                    // during disconnection updated data is not received
                    // thus run non WS load to get missing data
                    executeLoop("WS_CONNECT", forceNew = false)
                    true
                } else {
                    rxBus.send(EventNSClientNewLog("◄ WS", "Auth failed"))
                    false
                }
                rxBus.send(EventNSClientUpdateGuiStatus())
            })
        }
    }

    private val onConnectAlarms = Emitter.Listener {
        val socket = alarmSocket
        val socketId = socket?.id() ?: "NULL"
        rxBus.send(EventNSClientNewLog("◄ WS", "connected alarms ID: $socketId"))
        if (socket != null) {
            val authMessage = JSONObject().also {
                it.put("accessToken", sp.getString(R.string.key_ns_client_token, ""))
            }
            rxBus.send(EventNSClientNewLog("► WS", "requesting auth for alarms"))
            socket.emit("subscribe", authMessage, Ack { args ->
                val response = args[0] as JSONObject
                wsConnected = if (response.optBoolean("success")) {
                    rxBus.send(EventNSClientNewLog("◄ WS", response.optString("message")))
                    true
                } else {
                    rxBus.send(EventNSClientNewLog("◄ WS", "Auth failed"))
                    false
                }
            })
        }
    }

    private val onDisconnectStorage = Emitter.Listener { args ->
        aapsLogger.debug(LTag.NSCLIENT, "disconnect storage reason: ${args[0]}")
        rxBus.send(EventNSClientNewLog("◄ WS", "disconnect storage event"))
        wsConnected = false
        initialLoadFinished = false
        rxBus.send(EventNSClientUpdateGuiStatus())
    }

    private val onDisconnectAlarm = Emitter.Listener { args ->
        aapsLogger.debug(LTag.NSCLIENT, "disconnect alarm reason: ${args[0]}")
        rxBus.send(EventNSClientNewLog("◄ WS", "disconnect alarm event"))
    }

    private val onDataCreateUpdate = Emitter.Listener { args ->
        val response = args[0] as JSONObject
        aapsLogger.debug(LTag.NSCLIENT, "onDataCreateUpdate: $response")
        val collection = response.getString("colName")
        val docJson = response.getJSONObject("doc")
        val docString = response.getString("doc")
        rxBus.send(EventNSClientNewLog("◄ WS CREATE/UPDATE", "$collection <i>$docString</i>"))
        val srvModified = docJson.getLong("srvModified")
        lastLoadedSrvModified.set(collection, srvModified)
        storeLastLoadedSrvModified()
        when (collection) {
            "devicestatus" -> docString.toNSDeviceStatus().let { nsDeviceStatusHandler.handleNewData(arrayOf(it)) }
            "entries"      -> docString.toNSSgvV3()?.let {
                nsIncomingDataProcessor.processSgvs(listOf(it))
                storeDataForDb.storeGlucoseValuesToDb()
            }

            "profile"      ->
                nsIncomingDataProcessor.processProfile(docJson)

            "treatments"   -> docString.toNSTreatment()?.let {
                nsIncomingDataProcessor.processTreatments(listOf(it))
                storeDataForDb.storeTreatmentsToDb()
            }

            "foods"        -> docString.toNSFood()?.let {
                nsIncomingDataProcessor.processFood(listOf(it))
                storeDataForDb.storeFoodsToDb()
            }

            "settings"     -> {}
        }
    }

    private val onDataDelete = Emitter.Listener { args ->
        val response = args[0] as JSONObject
        aapsLogger.debug(LTag.NSCLIENT, "onDataDelete: $response")
        val collection = response.optString("colName") ?: return@Listener
        val identifier = response.optString("identifier") ?: return@Listener
        rxBus.send(EventNSClientNewLog("◄ WS DELETE", "$collection $identifier"))
        if (collection == "treatments") {
            storeDataForDb.deleteTreatment.add(identifier)
            storeDataForDb.updateDeletedTreatmentsInDb()
        }
        if (collection == "entries") {
            storeDataForDb.deleteGlucoseValue.add(identifier)
            storeDataForDb.updateDeletedGlucoseValuesInDb()
        }
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
        val data = args[0] as JSONObject
        rxBus.send(EventNSClientNewLog("◄ ANNOUNCEMENT", data.optString("message")))
        aapsLogger.debug(LTag.NSCLIENT, data.toString())
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_announcements, config.NSCLIENT))
            uiInteraction.addNotificationWithAction(injector, NSAlarmObject(data))
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
        val data = args[0] as JSONObject
        rxBus.send(EventNSClientNewLog("◄ ALARM", data.optString("message")))
        aapsLogger.debug(LTag.NSCLIENT, data.toString())
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_alarms, config.NSCLIENT)) {
            val snoozedTo = sp.getLong(rh.gs(info.nightscout.core.utils.R.string.key_snoozed_to) + data.optString("level"), 0L)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo)
                uiInteraction.addNotificationWithAction(injector, NSAlarmObject(data))
        }
    }

    private val onUrgentAlarm = Emitter.Listener { args: Array<Any> ->
        val data = args[0] as JSONObject
        rxBus.send(EventNSClientNewLog("◄ URGENT ALARM", data.optString("message")))
        aapsLogger.debug(LTag.NSCLIENT, data.toString())
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_alarms, config.NSCLIENT)) {
            val snoozedTo = sp.getLong(rh.gs(info.nightscout.core.utils.R.string.key_snoozed_to) + data.optString("level"), 0L)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo)
                uiInteraction.addNotificationWithAction(injector, NSAlarmObject(data))
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
        val data = args[0] as JSONObject
        rxBus.send(EventNSClientNewLog("◄ CLEARALARM", data.optString("title")))
        aapsLogger.debug(LTag.NSCLIENT, data.toString())
        rxBus.send(EventDismissNotification(Notification.NS_ALARM))
        rxBus.send(EventDismissNotification(Notification.NS_URGENT_ALARM))
    }

    override fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        if (!isEnabled()) return
        if (!sp.getBoolean(R.string.key_ns_upload, true)) {
            aapsLogger.debug(LTag.NSCLIENT, "Upload disabled. Message dropped")
            return
        }
        alarmSocket?.emit("ack", originalAlarm.level(), originalAlarm.group(), silenceTimeInMilliseconds)
        rxBus.send(EventNSClientNewLog("► ALARMACK ", "${originalAlarm.level()} ${originalAlarm.group()} $silenceTimeInMilliseconds"))
    }

    /**********************
    WS code end
     **********************/

    override fun resend(reason: String) {
        // If WS is enabled, download is triggered by changes in NS. Thus uploadOnly
        // Exception is after reset to full sync (initialLoadFinished == false), where
        // older data must be loaded directly and then continue over WS
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_use_ws, true) && initialLoadFinished)
            executeUpload("START $reason", forceNew = true)
        else
            executeLoop("START $reason", forceNew = true)
    }

    override fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_ns_paused, newState)
        rxBus.send(EventPreferenceChange(rh.gs(R.string.key_ns_paused)))
    }

    override fun detectedNsVersion(): String? = nsAndroidClient?.lastStatus?.version

    override val address: String get() = sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "")

    override fun isFirstLoad(collection: NsClient.Collection) =
        when (collection) {
            NsClient.Collection.ENTRIES    -> lastLoadedSrvModified.collections.entries == 0L
            NsClient.Collection.TREATMENTS -> lastLoadedSrvModified.collections.treatments == 0L
            NsClient.Collection.FOODS      -> lastLoadedSrvModified.collections.foods == 0L
            NsClient.Collection.PROFILE    -> lastLoadedSrvModified.collections.profile == 0L
        }

    override fun updateLatestBgReceivedIfNewer(latestReceived: Long) {
        if (isFirstLoad(NsClient.Collection.ENTRIES)) firstLoadContinueTimestamp.collections.entries = latestReceived
    }

    override fun updateLatestTreatmentReceivedIfNewer(latestReceived: Long) {
        if (isFirstLoad(NsClient.Collection.TREATMENTS)) firstLoadContinueTimestamp.collections.treatments = latestReceived
    }

    override fun resetToFullSync() {
        firstLoadContinueTimestamp = LastModified(LastModified.Collections())
        lastLoadedSrvModified = LastModified(LastModified.Collections())
        initialLoadFinished = false
        storeLastLoadedSrvModified()
        dataSyncSelectorV3.resetToNextFullSync()
    }

    override suspend fun nsAdd(collection: String, dataPair: DataSyncSelector.DataPair, progress: String, profile: Profile?): Boolean =
        dbOperation(collection, dataPair, progress, Operation.CREATE, profile)

    override suspend fun nsUpdate(collection: String, dataPair: DataSyncSelector.DataPair, progress: String, profile: Profile?): Boolean =
        dbOperation(collection, dataPair, progress, Operation.UPDATE, profile)

    enum class Operation { CREATE, UPDATE }

    private val gson: Gson = GsonBuilder().create()

    private suspend fun slowDown() {
        if (sp.getBoolean(R.string.key_ns_sync_slow, false)) SystemClock.sleep(250)
        else SystemClock.sleep(10)
    }

    private suspend fun dbOperationProfileStore(collection: String = "profile", dataPair: DataSyncSelector.DataPair, progress: String): Boolean {
        val data = (dataPair as DataSyncSelector.PairProfileStore).value
        try {
            rxBus.send(EventNSClientNewLog("► ADD $collection", "Sent ${dataPair.javaClass.simpleName} <i>$data</i> $progress"))
            nsAndroidClient?.createProfileStore(data)?.let { result ->
                when (result.response) {
                    200  -> rxBus.send(EventNSClientNewLog("◄ UPDATED", "OK ProfileStore"))
                    201  -> rxBus.send(EventNSClientNewLog("◄ ADDED", "OK ProfileStore"))
                    404  -> rxBus.send(EventNSClientNewLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))

                    else -> {
                        rxBus.send(EventNSClientNewLog("◄ ERROR", "${result.errorResponse}"))
                        return true
                    }
                }
                slowDown()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
        }
        return true
    }

    private suspend fun dbOperationDeviceStatus(collection: String = "devicestatus", dataPair: DataSyncSelector.PairDeviceStatus, progress: String): Boolean {
        try {
            val data = dataPair.value.toNSDeviceStatus()
            rxBus.send(EventNSClientNewLog("► ADD $collection", "Sent ${dataPair.javaClass.simpleName} <i>${gson.toJson(data)}</i> $progress"))
            nsAndroidClient?.createDeviceStatus(data)?.let { result ->
                when (result.response) {
                    200  -> rxBus.send(EventNSClientNewLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}"))
                    201  -> rxBus.send(EventNSClientNewLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName} ${result.identifier}"))
                    404  -> rxBus.send(EventNSClientNewLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))

                    else -> {
                        rxBus.send(EventNSClientNewLog("◄ ERROR", "${result.errorResponse} "))
                        return true
                    }
                }
                result.identifier?.let {
                    dataPair.value.interfaceIDs.nightscoutId = it
                    storeDataForDb.nsIdDeviceStatuses.add(dataPair.value)
                    sp.putBoolean(info.nightscout.core.utils.R.string.key_objectives_pump_status_is_available_in_ns, true)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
        }
        return true
    }

    private suspend fun dbOperationEntries(collection: String = "entries", dataPair: DataSyncSelector.PairGlucoseValue, progress: String, operation: Operation): Boolean {
        val call = when (operation) {
            Operation.CREATE -> nsAndroidClient?.let { return@let it::createSgv }
            Operation.UPDATE -> nsAndroidClient?.let { return@let it::updateSvg }
        }
        try {
            val data = dataPair.value.toNSSvgV3()
            val id = dataPair.value.interfaceIDs.nightscoutId
            rxBus.send(
                EventNSClientNewLog(
                    when (operation) {
                        Operation.CREATE -> "► ADD $collection"
                        Operation.UPDATE -> "► UPDATE $collection"
                    },
                    when (operation) {
                        Operation.CREATE -> "Sent ${dataPair.javaClass.simpleName} <i>${gson.toJson(data)}</i> $progress"
                        Operation.UPDATE -> "Sent ${dataPair.javaClass.simpleName} $id <i>${gson.toJson(data)}</i> $progress"
                    }
                )
            )
            call?.let { it(data) }?.let { result ->
                when (result.response) {
                    200  -> rxBus.send(EventNSClientNewLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}"))
                    201  -> rxBus.send(EventNSClientNewLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}"))
                    400  -> rxBus.send(EventNSClientNewLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))
                    404  -> rxBus.send(EventNSClientNewLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))

                    else -> {
                        rxBus.send(EventNSClientNewLog("◄ ERROR", "${result.errorResponse} "))
                        return true
                    }
                }
                result.identifier?.let {
                    dataPair.value.interfaceIDs.nightscoutId = it
                    storeDataForDb.nsIdGlucoseValues.add(dataPair.value)
                }
                slowDown()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
        }
        return true
    }

    private suspend fun dbOperationFood(collection: String = "food", dataPair: DataSyncSelector.PairFood, progress: String, operation: Operation): Boolean {
        val call = when (operation) {
            Operation.CREATE -> nsAndroidClient?.let { return@let it::createFood }
            Operation.UPDATE -> nsAndroidClient?.let { return@let it::updateFood }
        }
        try {
            val data = dataPair.value.toNSFood()
            val id = dataPair.value.interfaceIDs.nightscoutId
            rxBus.send(
                EventNSClientNewLog(
                    when (operation) {
                        Operation.CREATE -> "► ADD $collection"
                        Operation.UPDATE -> "► UPDATE $collection"
                    },
                    when (operation) {
                        Operation.CREATE -> "Sent ${dataPair.javaClass.simpleName} <i>${gson.toJson(data)}</i> $progress"
                        Operation.UPDATE -> "Sent ${dataPair.javaClass.simpleName} $id <i>${gson.toJson(data)}</i> $progress"
                    }
                )
            )
            call?.let { it(data) }?.let { result ->
                when (result.response) {
                    200  -> rxBus.send(EventNSClientNewLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}"))
                    201  -> rxBus.send(EventNSClientNewLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}"))
                    400  -> rxBus.send(EventNSClientNewLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))
                    404  -> rxBus.send(EventNSClientNewLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))

                    else -> {
                        rxBus.send(EventNSClientNewLog("◄ ERROR", "${result.errorResponse} "))
                        return true
                    }
                }
                result.identifier?.let {
                    dataPair.value.interfaceIDs.nightscoutId = it
                    storeDataForDb.nsIdFoods.add(dataPair.value)
                }
                slowDown()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
        }
        return true
    }

    private suspend fun dbOperationTreatments(collection: String = "treatments", dataPair: DataSyncSelector.DataPair, progress: String, operation: Operation, profile: Profile?): Boolean {
        val call = when (operation) {
            Operation.CREATE -> nsAndroidClient?.let { return@let it::createTreatment }
            Operation.UPDATE -> nsAndroidClient?.let { return@let it::updateTreatment }
        }
        when (dataPair) {
            is DataSyncSelector.PairBolus                  -> dataPair.value.toNSBolus()
            is DataSyncSelector.PairCarbs                  -> dataPair.value.toNSCarbs()
            is DataSyncSelector.PairBolusCalculatorResult  -> dataPair.value.toNSBolusWizard()
            is DataSyncSelector.PairTemporaryTarget        -> dataPair.value.toNSTemporaryTarget()
            is DataSyncSelector.PairTherapyEvent           -> dataPair.value.toNSTherapyEvent()

            is DataSyncSelector.PairTemporaryBasal         -> {
                profile ?: return true
                dataPair.value.toNSTemporaryBasal(profile)
            }

            is DataSyncSelector.PairExtendedBolus          -> {
                profile ?: return true
                dataPair.value.toNSExtendedBolus(profile)
            }

            is DataSyncSelector.PairProfileSwitch          -> dataPair.value.toNSProfileSwitch(dateUtil, decimalFormatter)
            is DataSyncSelector.PairEffectiveProfileSwitch -> dataPair.value.toNSEffectiveProfileSwitch(dateUtil)
            is DataSyncSelector.PairOfflineEvent           -> dataPair.value.toNSOfflineEvent()
            else                                           -> null
        }?.let { data ->
            try {
                val id = if (dataPair.value is TraceableDBEntry) (dataPair.value as TraceableDBEntry).interfaceIDs.nightscoutId else ""
                rxBus.send(
                    EventNSClientNewLog(
                        when (operation) {
                            Operation.CREATE -> "► ADD $collection"
                            Operation.UPDATE -> "► UPDATE $collection"
                        },
                        when (operation) {
                            Operation.CREATE -> "Sent ${dataPair.javaClass.simpleName} <i>${gson.toJson(data)}</i> $progress"
                            Operation.UPDATE -> "Sent ${dataPair.javaClass.simpleName} $id <i>${gson.toJson(data)}</i> $progress"
                        }
                    )
                )
                call?.let { it(data) }?.let { result ->
                    when (result.response) {
                        200  -> rxBus.send(EventNSClientNewLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}"))
                        201  -> rxBus.send(EventNSClientNewLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}"))
                        400  -> rxBus.send(EventNSClientNewLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))
                        404  -> rxBus.send(EventNSClientNewLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}"))

                        else -> {
                            rxBus.send(EventNSClientNewLog("◄ ERROR", "${result.errorResponse} "))
                            return true
                        }
                    }
                    result.identifier?.let {
                        when (dataPair) {
                            is DataSyncSelector.PairBolus                  -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdBoluses.add(dataPair.value)
                            }

                            is DataSyncSelector.PairCarbs                  -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdCarbs.add(dataPair.value)
                            }

                            is DataSyncSelector.PairBolusCalculatorResult  -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdBolusCalculatorResults.add(dataPair.value)
                            }

                            is DataSyncSelector.PairTemporaryTarget        -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdTemporaryTargets.add(dataPair.value)
                            }

                            is DataSyncSelector.PairTherapyEvent           -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdTherapyEvents.add(dataPair.value)
                            }

                            is DataSyncSelector.PairTemporaryBasal         -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdTemporaryBasals.add(dataPair.value)
                            }

                            is DataSyncSelector.PairExtendedBolus          -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdExtendedBoluses.add(dataPair.value)
                            }

                            is DataSyncSelector.PairProfileSwitch          -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdProfileSwitches.add(dataPair.value)
                            }

                            is DataSyncSelector.PairEffectiveProfileSwitch -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdEffectiveProfileSwitches.add(dataPair.value)
                            }

                            is DataSyncSelector.PairOfflineEvent           -> {
                                dataPair.value.interfaceIDs.nightscoutId = it
                                storeDataForDb.nsIdOfflineEvents.add(dataPair.value)
                            }

                            else                                           -> {
                                throw InvalidParameterException()
                            }
                        }
                    }
                    slowDown()
                }
            } catch (e: Exception) {
                rxBus.send(EventNSClientNewLog("◄ ERROR", e.localizedMessage))
                aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
                return false
            }
        }
        return true
    }

    private suspend fun dbOperation(collection: String, dataPair: DataSyncSelector.DataPair, progress: String, operation: Operation, profile: Profile?): Boolean =
        when (collection) {
            "profile"      -> dbOperationProfileStore(dataPair = dataPair, progress = progress)
            "devicestatus" -> dbOperationDeviceStatus(dataPair = dataPair as DataSyncSelector.PairDeviceStatus, progress = progress)
            "entries"      -> dbOperationEntries(dataPair = dataPair as DataSyncSelector.PairGlucoseValue, progress = progress, operation = operation)
            "food"         -> dbOperationFood(dataPair = dataPair as DataSyncSelector.PairFood, progress = progress, operation = operation)
            "treatments"   -> dbOperationTreatments(dataPair = dataPair, progress = progress, operation = operation, profile = profile)

            else           -> false
        }

    fun storeLastLoadedSrvModified() {
        sp.putString(R.string.key_ns_client_v3_last_modified, Json.encodeToString(LastModified.serializer(), lastLoadedSrvModified))
    }

    private fun executeLoop(origin: String, forceNew: Boolean) {
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_use_ws, true) && initialLoadFinished) return
        if (sp.getBoolean(R.string.key_ns_paused, false)) {
            rxBus.send(EventNSClientNewLog("● RUN", "paused  $origin"))
            return
        }
        if (!isAllowed) {
            rxBus.send(EventNSClientNewLog("● RUN", "$blockingReason $origin"))
            return
        }
        if (workIsRunning()) {
            rxBus.send(EventNSClientNewLog("● RUN", "Already running $origin"))
            if (!forceNew) return
            // Wait for end and start new cycle
            while (workIsRunning()) Thread.sleep(5000)
        }
        rxBus.send(EventNSClientNewLog("● RUN", "Starting next round $origin"))
        rxBus.send(EventNSClientUpdateGuiStatus())
        WorkManager.getInstance(context)
            .beginUniqueWork(
                JOB_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(LoadStatusWorker::class.java).build()
            )
            .then(OneTimeWorkRequest.Builder(LoadLastModificationWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadBgWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadFoodsWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadProfileStoreWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(DataSyncWorker::class.java).build())
            .enqueue()
    }

    private fun executeUpload(origin: String, forceNew: Boolean) {
        if (sp.getBoolean(R.string.key_ns_paused, false)) {
            rxBus.send(EventNSClientNewLog("● RUN", "paused"))
            return
        }
        if (!isAllowed) {
            rxBus.send(EventNSClientNewLog("● RUN", blockingReason))
            return
        }
        if (workIsRunning()) {
            rxBus.send(EventNSClientNewLog("● RUN", "Already running $origin"))
            if (!forceNew) return
            // Wait for end and start new cycle
            while (workIsRunning()) Thread.sleep(5000)
        }
        rxBus.send(EventNSClientNewLog("● RUN", "Starting upload $origin"))
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                JOB_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(DataSyncWorker::class.java).build()
            )
    }

    private fun workIsRunning(workName: String = JOB_NAME): Boolean {
        for (workInfo in WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get())
            if (workInfo.state == WorkInfo.State.BLOCKED || workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING)
                return true
        return false
    }
}