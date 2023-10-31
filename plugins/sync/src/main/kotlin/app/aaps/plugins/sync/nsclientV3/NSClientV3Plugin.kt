package app.aaps.plugins.sync.nsclientV3

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
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
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.T
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.interfaces.TraceableDBEntry
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.NSClientFragment
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiData
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.nsclientV3.extensions.toNSBolus
import app.aaps.plugins.sync.nsclientV3.extensions.toNSBolusWizard
import app.aaps.plugins.sync.nsclientV3.extensions.toNSCarbs
import app.aaps.plugins.sync.nsclientV3.extensions.toNSDeviceStatus
import app.aaps.plugins.sync.nsclientV3.extensions.toNSEffectiveProfileSwitch
import app.aaps.plugins.sync.nsclientV3.extensions.toNSExtendedBolus
import app.aaps.plugins.sync.nsclientV3.extensions.toNSFood
import app.aaps.plugins.sync.nsclientV3.extensions.toNSOfflineEvent
import app.aaps.plugins.sync.nsclientV3.extensions.toNSProfileSwitch
import app.aaps.plugins.sync.nsclientV3.extensions.toNSSvgV3
import app.aaps.plugins.sync.nsclientV3.extensions.toNSTemporaryBasal
import app.aaps.plugins.sync.nsclientV3.extensions.toNSTemporaryTarget
import app.aaps.plugins.sync.nsclientV3.extensions.toNSTherapyEvent
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.serialization.json.Json
import java.security.InvalidParameterException
import javax.inject.Inject
import javax.inject.Singleton

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
    private val dataSyncSelectorV3: DataSyncSelectorV3,
    private val persistenceLayer: PersistenceLayer,
    private val nsClientSource: NSClientSource,
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
                sp.getBoolean(R.string.key_ns_paused, false)                                                               -> rh.gs(app.aaps.core.ui.R.string.paused)
                isAllowed.not()                                                                                            -> blockingReason
                sp.getBoolean(app.aaps.core.utils.R.string.key_ns_use_ws, true) && nsClientV3Service?.wsConnected == true  -> "WS: " + rh.gs(app.aaps.core.interfaces.R.string.connected)
                sp.getBoolean(app.aaps.core.utils.R.string.key_ns_use_ws, true) && nsClientV3Service?.wsConnected == false -> "WS: " + rh.gs(R.string.not_connected)
                lastOperationError != null                                                                                 -> rh.gs(app.aaps.core.ui.R.string.error)
                nsAndroidClient?.lastStatus == null                                                                        -> rh.gs(R.string.not_connected)
                workIsRunning()                                                                                            -> rh.gs(R.string.working)
                nsAndroidClient?.lastStatus?.apiPermissions?.isFull() == true                                              -> rh.gs(app.aaps.core.interfaces.R.string.connected)
                nsAndroidClient?.lastStatus?.apiPermissions?.isRead() == true                                              -> rh.gs(R.string.read_only)
                else                                                                                                       -> rh.gs(app.aaps.core.ui.R.string.unknown)
            }
    var lastOperationError: String? = null

    internal var nsAndroidClient: NSAndroidClient? = null
    internal var nsClientV3Service: NSClientV3Service? = null

    internal val isAllowed get() = receiverDelegate.allowed
    internal val blockingReason get() = receiverDelegate.blockingReason

    val maxAge = T.days(100).msecs()
    internal var newestDataOnServer: LastModified? = null // timestamp of last modification for every collection provided by server
    internal var lastLoadedSrvModified = LastModified(LastModified.Collections()) // max srvLastModified timestamp of last fetched data for every collection
    internal var firstLoadContinueTimestamp = LastModified(LastModified.Collections()) // timestamp of last fetched data for every collection during initial load
    internal var initialLoadFinished = false

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is disconnected")
            nsClientV3Service = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is connected")
            val localBinder = service as NSClientV3Service.LocalBinder
            nsClientV3Service = localBinder.serviceInstance
        }
    }

    override fun onStart() {
        super.onStart()

        lastLoadedSrvModified = Json.decodeFromString(
            sp.getString(
                R.string.key_ns_client_v3_last_modified,
                Json.encodeToString(LastModified.serializer(), LastModified(LastModified.Collections()))
            )
        )

        setClient()

        receiverDelegate.grabReceiversState()
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ stopService() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventConnectivityOptionChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev ->
                           rxBus.send(EventNSClientNewLog("● CONNECTIVITY", ev.blockingReason))
                           assert(nsClientV3Service != null)
                           if (ev.connected) {
                               when {
                                   isAllowed && nsClientV3Service?.storageSocket == null  -> setClient() // socket must be created
                                   !isAllowed && nsClientV3Service?.storageSocket != null -> stopService()
                               }
                               if (isAllowed) executeLoop("CONNECTIVITY", forceNew = false)
                           }
                           rxBus.send(EventNSClientUpdateGuiStatus())
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev ->
                           if (ev.isChanged(rh.gs(R.string.key_ns_client_token)) ||
                               ev.isChanged(rh.gs(app.aaps.core.utils.R.string.key_nsclientinternal_url)) ||
                               ev.isChanged(rh.gs(app.aaps.core.utils.R.string.key_ns_use_ws)) ||
                               ev.isChanged(rh.gs(R.string.key_ns_paused)) ||
                               ev.isChanged(rh.gs(app.aaps.core.utils.R.string.key_ns_alarms)) ||
                               ev.isChanged(rh.gs(app.aaps.core.utils.R.string.key_ns_announcements))
                           ) {
                               stopService()
                               nsAndroidClient = null
                               setClient()
                           }
                           if (ev.isChanged(rh.gs(app.aaps.core.utils.R.string.key_local_profile_last_change)))
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
                persistenceLayer.getLastGlucoseValue().blockingGet().let {
                    // if last value is older than 5 min or there is no bg
                    if (it is ValueWrapper.Existing) {
                        if (it.value.timestamp < dateUtil.now() - T.mins(5).plus(T.secs(20)).msecs()) {
                            refreshInterval = T.mins(1).msecs()
                        }
                    }
                }
            if (!sp.getBoolean(app.aaps.core.utils.R.string.key_ns_use_ws, true))
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
        stopService()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            preferenceFragment.findPreference<PreferenceScreen>(rh.gs(R.string.ns_sync_options))?.isVisible = false

            preferenceFragment.findPreference<SwitchPreference>(rh.gs(app.aaps.core.utils.R.string.key_ns_create_announcements_from_errors))?.isVisible = false
            preferenceFragment.findPreference<SwitchPreference>(rh.gs(app.aaps.core.utils.R.string.key_ns_create_announcements_from_carbs_req))?.isVisible = false
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

    private fun setClient() {
        if (nsAndroidClient == null)
            nsAndroidClient = NSAndroidClientImpl(
                baseUrl = sp.getString(app.aaps.core.utils.R.string.key_nsclientinternal_url, "").lowercase().replace("https://", "").replace(Regex("/$"), ""),
                accessToken = sp.getString(R.string.key_ns_client_token, ""),
                context = context,
                logging = config.isEngineeringMode() || config.isDev(),
                logger = { msg -> aapsLogger.debug(LTag.HTTP, msg) }
            )
        SystemClock.sleep(2000)
        startService()
        rxBus.send(EventSWSyncStatus(status))
    }

    private fun startService() {
        if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_use_ws, true)) {
            context.bindService(Intent(context, NSClientV3Service::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopService() {
        try {
            if (nsClientV3Service != null) context.unbindService(serviceConnection)
        } catch (e: Exception) {
            nsClientV3Service = null
        }
    }

    override fun resend(reason: String) {
        // If WS is enabled, download is triggered by changes in NS. Thus uploadOnly
        // Exception is after reset to full sync (initialLoadFinished == false), where
        // older data must be loaded directly and then continue over WS
        if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_use_ws, true) && initialLoadFinished)
            executeUpload("START $reason", forceNew = true)
        else
            executeLoop("START $reason", forceNew = true)
    }

    override fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_ns_paused, newState)
        rxBus.send(EventPreferenceChange(rh.gs(R.string.key_ns_paused)))
    }

    override fun detectedNsVersion(): String? = nsAndroidClient?.lastStatus?.version

    override val address: String get() = sp.getString(app.aaps.core.utils.R.string.key_nsclientinternal_url, "")

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

    override fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        if (!isEnabled()) return
        if (!sp.getBoolean(R.string.key_ns_upload, true)) {
            aapsLogger.debug(LTag.NSCLIENT, "Upload disabled. Message dropped")
            return
        }
        nsClientV3Service?.handleClearAlarm(originalAlarm, silenceTimeInMilliseconds)
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
                    sp.putBoolean(app.aaps.core.utils.R.string.key_objectives_pump_status_is_available_in_ns, true)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
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

    internal fun executeLoop(origin: String, forceNew: Boolean) {
        if (sp.getBoolean(app.aaps.core.utils.R.string.key_ns_use_ws, true) && initialLoadFinished) return
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