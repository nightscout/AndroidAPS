package app.aaps.plugins.sync.nsclientV3

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.data.model.HasIDs
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventProfileStoreChanged
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.ui.compose.icons.IcPluginNsClient
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.compose.NSClientComposeContent
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
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.plugins.sync.nsclientV3.keys.NsclientLongKey
import app.aaps.plugins.sync.nsclientV3.keys.NsclientStringKey
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.security.InvalidParameterException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientV3Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val rxBus: RxBus,
    private val context: Context,
    private val receiverDelegate: ReceiverDelegate,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val dataSyncSelectorV3: DataSyncSelectorV3,
    private val persistenceLayer: PersistenceLayer,
    private val nsClientSource: NSClientSource,
    private val storeDataForDb: StoreDataForDb,
    private val decimalFormatter: DecimalFormatter,
    private val l: L,
    private val nsClientRepository: NSClientRepository,
    private val uel: UserEntryLogger,
    private val activePlugin: ActivePlugin,
) : NsClient, Sync, PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_nightscout_syncs)
        .icon(IcPluginNsClient)
        .pluginName(R.string.ns_client_v3_title)
        .shortName(R.string.ns_client_v3_short_name)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_ns_client_v3)
        .composeContent { plugin ->
            NSClientComposeContent(
                dateUtil = dateUtil,
                aapsLogger = aapsLogger,
                persistenceLayer = persistenceLayer,
                uel = uel,
                nsClientRepository = nsClientRepository,
                nsClient = plugin as NsClient,
                title = rh.gs(R.string.ns_client_v3_title)
            )
        },
    ownPreferences = listOf(NsclientBooleanKey::class.java, NsclientStringKey::class.java, NsclientLongKey::class.java),
    aapsLogger, rh, preferences
) {

    @Suppress("PrivatePropertyName")
    private val JOB_NAME: String = this::class.java.simpleName

    companion object {

        const val RECORDS_TO_LOAD = 500
    }

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var runLoop: Runnable
    private var handler: Handler? = null
    override val dataSyncSelector: DataSyncSelector get() = dataSyncSelectorV3
    override val status
        get() =
            when {
                preferences.get(NsclientBooleanKey.NsPaused)                                          -> rh.gs(app.aaps.core.ui.R.string.paused)
                isAllowed.not()                                                                       -> blockingReason
                preferences.get(BooleanKey.NsClient3UseWs) && nsClientV3Service?.wsConnected == true  -> "WS: " + rh.gs(app.aaps.core.interfaces.R.string.connected)
                preferences.get(BooleanKey.NsClient3UseWs) && nsClientV3Service?.wsConnected == false -> "WS: " + rh.gs(R.string.not_connected)
                lastOperationError != null                                                            -> rh.gs(app.aaps.core.ui.R.string.error)
                nsAndroidClient?.lastStatus == null                                                   -> rh.gs(R.string.not_connected)
                workIsRunning()                                                                       -> rh.gs(R.string.working)
                nsAndroidClient?.lastStatus?.apiPermissions?.isFull() == true                         -> rh.gs(app.aaps.core.interfaces.R.string.authorized)
                nsAndroidClient?.lastStatus?.apiPermissions?.isRead() == true                         -> rh.gs(R.string.read_only)
                else                                                                                  -> rh.gs(app.aaps.core.ui.R.string.unknown)
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

    private val fullSyncSemaphore = Any()

    /**
     * Set to true if full sync is requested from fragment.
     * In this case we must enable accepting all data from NS even when disabled in preferences
     */
    @VisibleForTesting var fullSyncRequested: Boolean = false

    /**
     * Full sync is performed right now
     */
    var doingFullSync = false
        @VisibleForTesting set

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
        handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

        lastLoadedSrvModified = Json.decodeFromString(preferences.get(NsclientStringKey.V3LastModified))

        setClient()

        receiverDelegate.grabReceiversState()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        rxBus.toFlow(EventAppExit::class.java)
            .onEach {
                stopService()
                WorkManager.getInstance(context).cancelUniqueWork(JOB_NAME)
            }.launchIn(scope)
        receiverDelegate.connectivityStatusFlow
            .drop(1) // skip initial value
            .onEach { ev ->
                nsClientRepository.addLog("● CONNECTIVITY", ev.blockingReason)
                if (ev.connected && isAllowed) {
                    val service = nsClientV3Service
                    if (service == null || service.storageSocket == null)
                        setClient() // (re)create client and WS; WS_CONNECT callback will trigger executeLoop
                    executeLoop("CONNECTIVITY", forceNew = false)
                    // Trigger upload of data that accumulated while offline.
                    // executeLoop may skip when WS is enabled and initial load is done,
                    // but pending outbound data still needs to be pushed.
                    executeUpload("CONNECTIVITY", forceNew = false)
                } else if (ev.connected && !isAllowed) {
                    nsClientV3Service?.let { service ->
                        if (service.storageSocket != null) stopService()
                    }
                }
                nsClientRepository.updateStatus(status)
            }.launchIn(scope)
        val restartOnChange: suspend (Any) -> Unit = {
            stopService()
            nsAndroidClient = null
            setClient()
            nsClientRepository.updateUrl(preferences.get(StringKey.NsClientUrl))
        }
        preferences.observe(StringKey.NsClientAccessToken).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(StringKey.NsClientUrl).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClient3UseWs).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(NsclientBooleanKey.NsPaused).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientNotificationsFromAlarms).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(BooleanKey.NsClientNotificationsFromAnnouncements).drop(1).onEach(restartOnChange).launchIn(scope)
        preferences.observe(LongNonKey.LocalProfileLastChange).drop(1)
            .onEach { executeUpload("PROFILE_CHANGE", forceNew = true) }.launchIn(scope)
        persistenceLayer.observeAnyChange()
            .onEach { types -> executeUpload("DB_CHANGED(${types.joinToString { it.simpleName ?: "?" }})", forceNew = false) }.launchIn(scope)
        rxBus.toFlow(EventProfileStoreChanged::class.java)
            .onEach { executeUpload("EventProfileStoreChanged", forceNew = false) }.launchIn(scope)

        runLoop = Runnable {
            var refreshInterval = T.mins(5).msecs()
            if (nsClientSource.isEnabled())
                runBlocking { persistenceLayer.getLastGlucoseValue() }?.let {
                    // if last value is older than 5 min or there is no bg
                    if (it.timestamp < dateUtil.now() - T.mins(5).plus(T.secs(20)).msecs()) {
                        refreshInterval = T.mins(1).msecs()
                    }
                }
            if (!preferences.get(BooleanKey.NsClient3UseWs))
                executeLoop("MAIN_LOOP", forceNew = true)
            else
                nsClientRepository.addLog("● TICK", "")
            handler?.postDelayed(runLoop, refreshInterval)
        }
        handler?.postDelayed(runLoop, T.mins(2).msecs())
    }

    fun scheduleIrregularExecution(refreshToken: Boolean = false) {
        if (refreshToken) {
            handler?.post { executeLoop("REFRESH TOKEN", forceNew = true) }
            return
        }
        if (config.AAPSCLIENT || nsClientSource.isEnabled()) {
            var origin = "5_MIN_AFTER_BG"
            var forceNew = true
            var toTime = lastLoadedSrvModified.collections.entries + T.mins(5).plus(T.secs(10)).msecs()
            if (toTime < dateUtil.now()) {
                toTime = dateUtil.now() + T.mins(1).plus(T.secs(0)).msecs()
                origin = "1_MIN_OLD_DATA"
                forceNew = false
            }
            handler?.postDelayed({ executeLoop(origin, forceNew = forceNew) }, toTime - dateUtil.now())
            nsClientRepository.addLog("● NEXT", dateUtil.dateAndTimeAndSecondsString(toTime))
        }
    }

    override fun onStop() {
        handler?.removeCallbacksAndMessages(null)
        handler?.looper?.quit()
        handler = null
        scope.cancel()
        stopService()
        super.onStop()
    }

    override val hasWritePermission: Boolean get() = nsAndroidClient?.lastStatus?.apiPermissions?.isFull() == true
    override val connected: Boolean get() = nsAndroidClient?.lastStatus != null

    private fun setClient() {
        if (nsAndroidClient == null)
            nsAndroidClient = NSAndroidClientImpl(
                baseUrl = preferences.get(StringKey.NsClientUrl).lowercase().replace("https://", "").replace(Regex("/$"), ""),
                accessToken = preferences.get(StringKey.NsClientAccessToken),
                context = context,
                logging = l.findByName(LTag.NSCLIENT.tag).enabled && (config.isEngineeringMode() || config.isDev()),
                logger = { msg -> aapsLogger.debug(LTag.HTTP, msg) }
            )
        SystemClock.sleep(2000)
        if (preferences.get(BooleanKey.NsClient3UseWs)) {
            if (nsClientV3Service == null) startService()
            else nsClientV3Service?.initializeWebSockets("setClient")
        }
        rxBus.send(EventSWSyncStatus(status))
    }

    private fun startService() {
        if (preferences.get(BooleanKey.NsClient3UseWs)) {
            context.bindService(Intent(context, NSClientV3Service::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopService() {
        try {
            if (nsClientV3Service != null) context.unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        nsClientV3Service = null
    }

    override fun resend(reason: String) {
        // If WS is enabled, download is triggered by changes in NS. Thus uploadOnly
        // Exception is after reset to full sync (initialLoadFinished == false), where
        // older data must be loaded directly and then continue over WS
        if (preferences.get(BooleanKey.NsClient3UseWs) && initialLoadFinished)
            executeUpload("START $reason", forceNew = true)
        else
            executeLoop("START $reason", forceNew = true)
    }

    override fun pause(newState: Boolean) {
        preferences.put(NsclientBooleanKey.NsPaused, newState)
    }

    override fun detectedNsVersion(): String? = nsAndroidClient?.lastStatus?.version

    override val address: String get() = preferences.get(StringKey.NsClientUrl)

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

    override suspend fun resetToFullSync() {
        firstLoadContinueTimestamp = LastModified(LastModified.Collections())
        lastLoadedSrvModified = LastModified(LastModified.Collections())
        initialLoadFinished = false
        storeLastLoadedSrvModified()
        dataSyncSelectorV3.resetToNextFullSync()
        synchronized(fullSyncSemaphore) {
            fullSyncRequested = true
        }
    }

    override fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        if (!isEnabled()) return
        if (!preferences.get(BooleanKey.NsClientUploadData)) {
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

    private fun slowDown() {
        if (preferences.get(BooleanKey.NsClientSlowSync)) SystemClock.sleep(250)
        else SystemClock.sleep(10)
    }

    private suspend fun dbOperationProfileStore(collection: String = "profile", dataPair: DataSyncSelector.DataPair, progress: String): Boolean {
        val data = (dataPair as DataSyncSelector.PairProfileStore).value
        try {
            nsClientRepository.addLog("► ADD $collection", "Sent ${dataPair.javaClass.simpleName} $progress", data)
            nsAndroidClient?.createProfileStore(data)?.let { result ->
                when (result.response) {
                    200  -> nsClientRepository.addLog("◄ UPDATED", "OK ProfileStore")
                    201  -> nsClientRepository.addLog("◄ ADDED", "OK ProfileStore")
                    404  -> nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")

                    else -> {
                        nsClientRepository.addLog("◄ ERROR", "${result.errorResponse}")
                        return config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS)
                    }
                }
                slowDown()
                return true
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
        }
        return false
    }

    private suspend fun dbOperationDeviceStatus(collection: String = "devicestatus", dataPair: DataSyncSelector.PairDeviceStatus, progress: String): Boolean {
        try {
            val data = dataPair.value.toNSDeviceStatus()
            nsClientRepository.addLog("► ADD $collection", "Sent ${dataPair.javaClass.simpleName} $progress", Json {}.encodeToJsonElement(data))
            nsAndroidClient?.createDeviceStatus(data)?.let { result ->
                when (result.response) {
                    200  -> nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                    201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName} ${result.identifier}")
                    404  -> nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")

                    else -> {
                        nsClientRepository.addLog("◄ ERROR", "${result.errorResponse} ")
                        return config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS)
                    }
                }
                result.identifier?.let {
                    dataPair.value.ids.nightscoutId = it
                    storeDataForDb.addToNsIdDeviceStatuses(dataPair.value)
                    preferences.put(BooleanNonKey.ObjectivesPumpStatusIsAvailableInNS, true)
                }
                slowDown()
                return true
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
        }
        return false
    }

    private suspend fun dbOperationEntries(collection: String = "entries", dataPair: DataSyncSelector.PairGlucoseValue, progress: String, operation: Operation): Boolean {
        val call = when (operation) {
            Operation.CREATE -> nsAndroidClient?.let { return@let it::createSgv }
            Operation.UPDATE -> nsAndroidClient?.let { return@let it::updateSvg }
        }
        try {
            val data = dataPair.value.toNSSvgV3()
            val id = dataPair.value.ids.nightscoutId
            nsClientRepository.addLog(
                when (operation) {
                    Operation.CREATE -> "► ADD $collection"
                    Operation.UPDATE -> "► UPDATE $collection"
                },
                when (operation) {
                    Operation.CREATE -> "Sent ${dataPair.javaClass.simpleName} $progress"
                    Operation.UPDATE -> "Sent ${dataPair.javaClass.simpleName} $id $progress"
                },
                Json {}.encodeToJsonElement(data)
            )
            call?.let { it(data) }?.let { result ->
                when (result.response) {
                    200  -> nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                    201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}")
                    400  -> nsClientRepository.addLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                    404  -> nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")

                    else -> {
                        nsClientRepository.addLog("◄ ERROR", "${result.errorResponse} ")
                        return config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS)
                    }
                }
                result.identifier?.let {
                    dataPair.value.ids.nightscoutId = it
                    storeDataForDb.addToNsIdGlucoseValues(dataPair.value)
                }
                slowDown()
                return true
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
        }
        return false
    }

    private suspend fun dbOperationFood(collection: String = "food", dataPair: DataSyncSelector.PairFood, progress: String, operation: Operation): Boolean {
        val call = when (operation) {
            Operation.CREATE -> nsAndroidClient?.let { return@let it::createFood }
            Operation.UPDATE -> nsAndroidClient?.let { return@let it::updateFood }
        }
        try {
            val data = dataPair.value.toNSFood()
            val id = dataPair.value.ids.nightscoutId
            nsClientRepository.addLog(
                when (operation) {
                    Operation.CREATE -> "► ADD $collection"
                    Operation.UPDATE -> "► UPDATE $collection"
                },
                when (operation) {
                    Operation.CREATE -> "Sent ${dataPair.javaClass.simpleName} $progress"
                    Operation.UPDATE -> "Sent ${dataPair.javaClass.simpleName} $id $progress"
                },
                Json {}.encodeToJsonElement(data)
            )
            call?.let { it(data) }?.let { result ->
                when (result.response) {
                    200  -> nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                    201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}")
                    400  -> nsClientRepository.addLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                    404  -> nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")

                    else -> {
                        nsClientRepository.addLog("◄ ERROR", "${result.errorResponse}")
                        return config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS)
                    }
                }
                result.identifier?.let {
                    dataPair.value.ids.nightscoutId = it
                    storeDataForDb.addToNsIdFoods(dataPair.value)
                }
                slowDown()
                return true
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
            return false
        }
        return false
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
            is DataSyncSelector.PairRunningMode            -> dataPair.value.toNSOfflineEvent()
            else                                           -> null
        }?.let { data ->
            try {
                val id = if (dataPair.value is HasIDs) (dataPair.value as HasIDs).ids.nightscoutId else ""
                nsClientRepository.addLog(
                    when (operation) {
                        Operation.CREATE -> "► ADD $collection"
                        Operation.UPDATE -> "► UPDATE $collection"
                    },
                    when (operation) {
                        Operation.CREATE -> "Sent ${dataPair.javaClass.simpleName} $progress"
                        Operation.UPDATE -> "Sent ${dataPair.javaClass.simpleName} $id $progress"
                    },
                    Json.encodeToJsonElement(data)
                )
                call?.let { it(data) }?.let { result ->
                    when (result.response) {
                        200  -> nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                        201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}")
                        400  -> nsClientRepository.addLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                        404  -> nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")

                        else -> {
                            nsClientRepository.addLog("◄ ERROR", "${result.errorResponse} ")
                            return config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS)
                        }
                    }
                    result.identifier?.let {
                        when (dataPair) {
                            is DataSyncSelector.PairBolus                  -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdBoluses(dataPair.value)
                            }

                            is DataSyncSelector.PairCarbs                  -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdCarbs(dataPair.value)
                            }

                            is DataSyncSelector.PairBolusCalculatorResult  -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdBolusCalculatorResults(dataPair.value)
                            }

                            is DataSyncSelector.PairTemporaryTarget        -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdTemporaryTargets(dataPair.value)
                            }

                            is DataSyncSelector.PairTherapyEvent           -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdTherapyEvents(dataPair.value)
                            }

                            is DataSyncSelector.PairTemporaryBasal         -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdTemporaryBasals(dataPair.value)
                            }

                            is DataSyncSelector.PairExtendedBolus          -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdExtendedBoluses(dataPair.value)
                            }

                            is DataSyncSelector.PairProfileSwitch          -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdProfileSwitches(dataPair.value)
                            }

                            is DataSyncSelector.PairEffectiveProfileSwitch -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdEffectiveProfileSwitches(dataPair.value)
                            }

                            is DataSyncSelector.PairRunningMode            -> {
                                dataPair.value.ids.nightscoutId = it
                                storeDataForDb.addToNsIdRunningModes(dataPair.value)
                            }

                            else                                           -> {
                                throw InvalidParameterException()
                            }
                        }
                    }
                    slowDown()
                    return true
                }
            } catch (e: Exception) {
                nsClientRepository.addLog("◄ ERROR", e.localizedMessage)
                aapsLogger.error(LTag.NSCLIENT, "Upload exception", e)
                return false
            }
        }
        return false
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
        preferences.put(NsclientStringKey.V3LastModified, Json.encodeToString(LastModified.serializer(), lastLoadedSrvModified))
    }

    internal fun executeLoop(origin: String, forceNew: Boolean) {
        if (preferences.get(BooleanKey.NsClient3UseWs) && initialLoadFinished) return
        if (preferences.get(NsclientBooleanKey.NsPaused)) {
            nsClientRepository.addLog("● RUN", "paused  $origin")
            return
        }
        if (!isAllowed) {
            nsClientRepository.addLog("● RUN", "$blockingReason $origin")
            return
        }
        if (workIsRunning()) {
            nsClientRepository.addLog("● RUN", "Already running $origin")
            if (!forceNew) return
            // Wait for end and start new cycle
            while (workIsRunning()) Thread.sleep(5000)
        }
        nsClientRepository.addLog("● RUN", "Starting next round $origin")
        synchronized(fullSyncSemaphore) {
            if (fullSyncRequested) {
                fullSyncRequested = false
                doingFullSync = true
                nsClientRepository.addLog("● RUN", "Full sync is requested")
            }
        }
        nsClientRepository.updateStatus(status)
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

    fun endFullSync() {
        synchronized(fullSyncSemaphore) {
            doingFullSync = false
        }
    }

    private fun executeUpload(origin: String, forceNew: Boolean) {
        if (preferences.get(NsclientBooleanKey.NsPaused)) {
            nsClientRepository.addLog("● RUN", "paused")
            return
        }
        if (!isAllowed) {
            nsClientRepository.addLog("● RUN", blockingReason)
            return
        }
        if (workIsRunning()) {
            nsClientRepository.addLog("● RUN", "Already running $origin")
            if (!forceNew) return
            // Wait for end and start new cycle
            while (workIsRunning()) Thread.sleep(5000)
        }
        nsClientRepository.addLog("● RUN", "Starting upload $origin")
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

    // TODO: Remove after full migration to Compose preferences (getPreferenceScreenContent)
    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "ns_client_synchronization" && requiredKey != "ns_client_alarm_options" && requiredKey != "ns_client_connection_options" && requiredKey != "ns_client_advanced") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "ns_client_settings"
            title = rh.gs(R.string.ns_client_v3_title)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = StringKey.NsClientUrl, dialogMessage = app.aaps.core.keys.R.string.ns_client_url_summary, title = app.aaps.core.keys.R.string.ns_client_url_title,
                    validatorParams = DefaultEditTextValidator.Parameters(testType = EditTextValidator.TEST_HTTPS_URL)
                )
            )
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = StringKey.NsClientAccessToken, dialogMessage = app.aaps.core.keys.R.string.nsclient_token_summary, title = app.aaps.core.keys.R.string.nsclient_token_title,
                    validatorParams = DefaultEditTextValidator.Parameters(testType = EditTextValidator.TEST_MIN_LENGTH, minLength = 17)
                )
            )
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClient3UseWs, summary = R.string.ns_use_ws_summary, title = R.string.ns_use_ws_title))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "ns_client_synchronization"
                title = rh.gs(R.string.ns_sync_options)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUploadData, summary = R.string.ns_upload_summary, title = R.string.ns_upload))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.BgSourceUploadToNs, title = app.aaps.core.ui.R.string.do_ns_upload_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptCgmData, summary = R.string.ns_receive_cgm_summary, title = R.string.ns_receive_cgm))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptProfileStore, summary = R.string.ns_receive_profile_store_summary, title = R.string.ns_receive_profile_store))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptTempTarget, summary = R.string.ns_receive_temp_target_summary, title = R.string.ns_receive_temp_target))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptProfileSwitch, summary = R.string.ns_receive_profile_switch_summary, title = R.string.ns_receive_profile_switch))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptInsulin, summary = R.string.ns_receive_insulin_summary, title = R.string.ns_receive_insulin))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptCarbs, summary = R.string.ns_receive_carbs_summary, title = R.string.ns_receive_carbs))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptTherapyEvent, summary = R.string.ns_receive_therapy_events_summary, title = R.string.ns_receive_therapy_events))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptRunningMode, summary = R.string.ns_receive_running_mode_summary, title = R.string.ns_receive_running_mode))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientAcceptTbrEb, summary = R.string.ns_receive_tbr_eb_summary, title = R.string.ns_receive_tbr_eb))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "ns_client_alarm_options"
                title = rh.gs(R.string.ns_alarm_options)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientNotificationsFromAlarms, title = R.string.ns_alarms))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientNotificationsFromAnnouncements, title = R.string.ns_announcements))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.NsClientAlarmStaleData, title = R.string.ns_alarm_stale_data_value_label))
                addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.NsClientUrgentAlarmStaleData, title = R.string.ns_alarm_urgent_stale_data_value_label))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "ns_client_connection_options"
                title = rh.gs(R.string.connection_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseCellular, title = R.string.ns_cellular))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseRoaming, title = R.string.ns_allow_roaming))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseWifi, title = R.string.ns_wifi))
                addPreference(
                    AdaptiveStringPreference(
                        ctx = context, stringKey = StringKey.NsClientWifiSsids, dialogMessage = app.aaps.core.keys.R.string.ns_wifi_ssids_summary, title = app.aaps.core.keys.R.string.ns_wifi_ssids,
                        validatorParams = DefaultEditTextValidator.Parameters(emptyAllowed = true)
                    )
                )
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseOnBattery, title = R.string.ns_battery))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseOnCharging, title = R.string.ns_charging))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "ns_client_advanced"
                title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientLogAppStart, title = R.string.ns_log_app_started_event))
                addPreference(
                    AdaptiveSwitchPreference(
                        ctx = context,
                        booleanKey = BooleanKey.NsClientCreateAnnouncementsFromErrors,
                        summary = R.string.ns_create_announcements_from_errors_summary,
                        title = R.string.ns_create_announcements_from_errors_title
                    )
                )
                addPreference(
                    AdaptiveSwitchPreference(
                        ctx = context,
                        booleanKey = BooleanKey.NsClientCreateAnnouncementsFromCarbsReq,
                        summary = R.string.ns_create_announcements_from_carbs_req_summary,
                        title = R.string.ns_create_announcements_from_carbs_req_title
                    )
                )
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientSlowSync, title = R.string.ns_sync_slow))
            })
        }
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "ns_client_v3_settings",
        titleResId = R.string.ns_client_v3_title,
        items = listOf(
            StringKey.NsClientUrl,
            StringKey.NsClientAccessToken,
            BooleanKey.NsClient3UseWs,
            PreferenceSubScreenDef(
                key = "ns_client_synchronization",
                titleResId = R.string.ns_sync_options,
                items = listOf(
                    BooleanKey.NsClientUploadData,
                    BooleanKey.BgSourceUploadToNs,
                    BooleanKey.NsClientAcceptCgmData,
                    BooleanKey.NsClientAcceptProfileStore,
                    BooleanKey.NsClientAcceptTempTarget,
                    BooleanKey.NsClientAcceptProfileSwitch,
                    BooleanKey.NsClientAcceptInsulin,
                    BooleanKey.NsClientAcceptCarbs,
                    BooleanKey.NsClientAcceptTherapyEvent,
                    BooleanKey.NsClientAcceptRunningMode,
                    BooleanKey.NsClientAcceptTbrEb
                )
            ),
            PreferenceSubScreenDef(
                key = "ns_client_alarm_options",
                titleResId = R.string.ns_alarm_options,
                items = listOf(
                    BooleanKey.NsClientNotificationsFromAlarms,
                    BooleanKey.NsClientNotificationsFromAnnouncements,
                    IntKey.NsClientAlarmStaleData,
                    IntKey.NsClientUrgentAlarmStaleData
                )
            ),
            PreferenceSubScreenDef(
                key = "ns_client_connection_options",
                titleResId = R.string.connection_settings_title,
                items = listOf(
                    BooleanKey.NsClientUseCellular,
                    BooleanKey.NsClientUseRoaming,
                    BooleanKey.NsClientUseWifi,
                    StringKey.NsClientWifiSsids,
                    BooleanKey.NsClientUseOnBattery,
                    BooleanKey.NsClientUseOnCharging
                )
            ),
            PreferenceSubScreenDef(
                key = "ns_client_advanced",
                titleResId = app.aaps.core.ui.R.string.advanced_settings_title,
                items = listOf(
                    BooleanKey.NsClientLogAppStart,
                    BooleanKey.NsClientCreateAnnouncementsFromErrors,
                    BooleanKey.NsClientCreateAnnouncementsFromCarbsReq,
                    BooleanKey.NsClientSlowSync
                )
            )
        ),
        icon = pluginDescription.icon
    )
}