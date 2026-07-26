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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.HasIDs
import app.aaps.core.data.model.SC
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
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
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
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.objects.extensions.freshness
import app.aaps.core.ui.compose.icons.IcPluginNsClient
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlReceiver
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientControlRoundTrip
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PreferencesClientPublisher
import app.aaps.plugins.sync.nsclientV3.compose.NSClientComposeContent
import app.aaps.plugins.sync.nsclientV3.extensions.toNSBolus
import app.aaps.plugins.sync.nsclientV3.extensions.toNSBolusWizard
import app.aaps.plugins.sync.nsclientV3.extensions.toNSCarbs
import app.aaps.plugins.sync.nsclientV3.extensions.toNSDeviceStatus
import app.aaps.plugins.sync.nsclientV3.extensions.toNSEffectiveProfileSwitch
import app.aaps.plugins.sync.nsclientV3.extensions.toNSExtendedBolus
import app.aaps.plugins.sync.nsclientV3.extensions.toNSFood
import app.aaps.plugins.sync.nsclientV3.extensions.toNSMbgV3
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
import app.aaps.plugins.sync.nsclientV3.services.RunningConfigurationPublisher
import app.aaps.plugins.sync.nsclientV3.workers.DataSyncWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadDeviceStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadFoodsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadProfileStoreWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadSettingsWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import app.aaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.json.JSONObject
import java.security.InvalidParameterException
import java.util.concurrent.atomic.AtomicBoolean
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
    private val runningConfigurationPublisher: RunningConfigurationPublisher,
    private val clientControlReceiver: ClientControlReceiver,
    private val clientControlRoundTrip: ClientControlRoundTrip,
    private val orphanDetector: OrphanDetector,
    private val authorizedClientsRepository: AuthorizedClientsRepository,
    private val preferencesClientPublisher: PreferencesClientPublisher,
    private val profileRepository: ProfileRepository,
) : NsClient, Sync, PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .icon(IcPluginNsClient)
        .pluginName(R.string.ns_client_v3_title)
        .shortName(R.string.ns_client_v3_short_name)
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
        private val CLIENT_CONTROL_POLL_MS = T.mins(5).msecs()

        // Rate-limit for requestMasterProbe so screen recompositions / banner flaps / reconnect bursts
        // don't spam pings + settings re-fetches at the master.
        private val PROBE_MIN_INTERVAL_MS = T.secs(5).msecs()
    }

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var runLoop: Runnable
    private var handler: Handler? = null

    // Re-run signaling. When a sync request arrives while a cycle is in flight, we set the
    // appropriate flag instead of dropping the request (old `forceNew=false`) or busy-waiting
    // (old `forceNew=true`). A WorkManager state observer (wired up in onStart) detects when
    // the cycle goes idle and triggers a follow-up run. Loop subsumes upload — if both flags
    // are set at idle, only the loop is re-run.
    private val pendingLoop = AtomicBoolean(false)
    private val pendingUpload = AtomicBoolean(false)
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
            // Process-death / crash teardown skips shutdownWebsockets, so flip the flag here
            // so UI gates don't keep showing "connected" until the service rebinds.
            _wsConnected.value = false
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is connected")
            val localBinder = service as NSClientV3Service.LocalBinder
            nsClientV3Service = localBinder.serviceInstance
            // Covers the case where Android reuses an already-alive service on rebind:
            // onCreate doesn't fire, but onServiceConnected does. The idempotency guard
            // in initializeWebSockets makes this safe when both fire on a fresh bind.
            nsClientV3Service?.initializeWebSockets("serviceConnected")
        }
    }

    override suspend fun onStart() {
        super.onStart()
        handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

        lastLoadedSrvModified = Json.decodeFromString(preferences.get(NsclientStringKey.V3LastModified))

        setClient()

        receiverDelegate.grabReceiversState()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        runningConfigurationPublisher.start(scope)
        preferencesClientPublisher.start(scope)
        // Master-side: fallback poll for inbound client-control envelopes. Primary path is the
        // WS settings-collection listener in NSClientV3Service that calls into
        // [handleClientControlSettingsEvent]; this loop only catches docs that arrived while
        // WS was disconnected. Cadence mirrors the main NS refresh interval.
        // Reactive to the toggle — enabling fires up the loop, disabling cancels it via
        // collectLatest cancelling the previous emission's body.
        scope.launch {
            preferences.observe(BooleanKey.NsClientAllowClientControl)
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest
                    while (isActive) {
                        // Skip the tick when WS is up — the listener has already delivered any
                        // docs in real time. Mirrors the main runLoop pattern in this file.
                        if (!preferences.get(BooleanKey.NsClient3UseWs)) {
                            runCatching { clientControlReceiver.processPending() }
                                .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl poll failed: ${it.message}", it) }
                        }
                        delay(CLIENT_CONTROL_POLL_MS)
                    }
                }
        }
        // Client: probe the master the moment the WS (re)connects — the pong clears the offline banner
        // fast, and the bundled config re-fetch picks up anything missed while disconnected.
        if (config.AAPSCLIENT) {
            scope.launch {
                wsConnectedFlow.collect { connected -> if (connected) requestMasterProbe() }
            }
        }
        rxBus.toFlow(EventAppExit::class.java)
            .collectResilient(scope, aapsLogger, LTag.NSCLIENT) {
                stopService()
                WorkManager.getInstance(context).cancelUniqueWork(JOB_NAME)
            }
        receiverDelegate.connectivityStatusFlow
            .drop(1) // skip initial value
            .collectResilient(scope, aapsLogger, LTag.NSCLIENT) { ev ->
                nsClientRepository.addLog("● CONNECTIVITY", ev.blockingReason)
                if (ev.connected && isAllowed) {
                    val service = nsClientV3Service
                    if (service == null || service.storageSocket == null)
                        setClient() // (re)create client and WS; WS_CONNECT callback will trigger executeLoop
                    executeLoop("CONNECTIVITY")
                    // Trigger upload of data that accumulated while offline.
                    // executeLoop may skip when WS is enabled and initial load is done,
                    // but pending outbound data still needs to be pushed.
                    executeUpload("CONNECTIVITY")
                } else if (ev.connected && !isAllowed) {
                    nsClientV3Service?.let { service ->
                        if (service.storageSocket != null) stopService()
                    }
                }
                nsClientRepository.updateStatus(status)
            }
        val restartOnChange: suspend (Any) -> Unit = {
            stopService()
            nsAndroidClient = null
            setClient()
            nsClientRepository.updateUrl(preferences.get(StringKey.NsClientUrl))
        }
        nsClientRepository.updateUrl(preferences.get(StringKey.NsClientUrl))
        // Re-run watchdog: when WorkManager's unique job goes idle, fire any pending follow-up.
        // Replaces the old `forceNew=true` Thread.sleep(5000) busy-wait. A loop subsumes an
        // upload (DataSyncWorker is the loop's last step), so loop wins if both are pending.
        // Gated on WorkManager.isInitialized() so unit tests that don't bootstrap WM (and call
        // onStart only to exercise unrelated handlers) don't crash here.
        if (WorkManager.isInitialized()) {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(JOB_NAME)
                .map { infos -> infos.any { it.state.isActive() } }
                .distinctUntilChanged()
                .filter { active -> !active }
                .onEach {
                    when {
                        pendingLoop.compareAndSet(true, false)   -> {
                            pendingUpload.set(false)
                            executeLoop("PENDING_RERUN")
                        }

                        pendingUpload.compareAndSet(true, false) -> executeUpload("PENDING_RERUN")
                    }
                }
                .launchIn(scope)
        }

        // Resilient collection (from dev). forceNew args dropped: this branch replaced the
        // forceNew mechanism with the WorkManager re-run watchdog above (see executeUpload).
        preferences.observe(StringKey.NsClientAccessToken).drop(1).collectResilient(scope, aapsLogger, LTag.NSCLIENT, block = restartOnChange)
        preferences.observe(StringKey.NsClientUrl).drop(1).collectResilient(scope, aapsLogger, LTag.NSCLIENT, block = restartOnChange)
        preferences.observe(BooleanKey.NsClient3UseWs).drop(1).collectResilient(scope, aapsLogger, LTag.NSCLIENT, block = restartOnChange)
        preferences.observe(NsclientBooleanKey.NsPaused).drop(1).collectResilient(scope, aapsLogger, LTag.NSCLIENT, block = restartOnChange)
        preferences.observe(BooleanKey.NsClientNotificationsFromAlarms).drop(1).collectResilient(scope, aapsLogger, LTag.NSCLIENT, block = restartOnChange)
        preferences.observe(BooleanKey.NsClientNotificationsFromAnnouncements).drop(1).collectResilient(scope, aapsLogger, LTag.NSCLIENT, block = restartOnChange)
        preferences.observe(LongNonKey.LocalProfileLastChange).drop(1)
            .collectResilient(scope, aapsLogger, LTag.NSCLIENT) { executeUpload("PROFILE_CHANGE") }
        persistenceLayer.observeAnyChange()
            // HR/SC writes come from the watch; this plugin doesn't upload them — skip to avoid reconnect-flush storm.
            .filter { types -> types.any { it != HR::class && it != SC::class } }
            .collectResilient(scope, aapsLogger, LTag.NSCLIENT) { types -> executeUpload("DB_CHANGED(${types.joinToString { it.simpleName ?: "?" }})") }
        profileRepository.profile.drop(1)
            .collectResilient(scope, aapsLogger, LTag.NSCLIENT) { executeUpload("profileRepository.profile changed") }

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
                executeLoop("MAIN_LOOP")
            else
                nsClientRepository.addLog("● TICK", "")
            handler?.postDelayed(runLoop, refreshInterval)
        }
        handler?.postDelayed(runLoop, T.mins(2).msecs())
    }

    /**
     * WS-push entry for client-control envelopes. NSClientV3Service routes settings-collection
     * create/update events here. The receiver verifies every envelope and — when the master toggle is off —
     * rejects cleanly with a signed ACK (one place, also covering the poll fallback) rather than silently
     * dropping it, which would time a client out into a false "master offline" alarm in the toggle race window.
     */
    fun handleClientControlSettingsEvent(identifier: String, doc: JSONObject) {
        scope.launch {
            runCatching { clientControlReceiver.onSettingsDocChanged(identifier, doc) }
                .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl WS dispatch failed for $identifier: ${it.message}", it) }
        }
    }

    /**
     * WS-push entry for a master→client command ACK (client side). NSClientV3Service routes
     * `aaps_clientcontrol_ack_<clientId>` settings events here. Synchronous parse/verify/emit — the
     * round-trip coordinator only re-publishes to an in-process flow, no IO.
     */
    fun handleClientControlAckEvent(doc: JSONObject) {
        runCatching { clientControlRoundTrip.onAckDoc(doc) }
            .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl ACK dispatch failed: ${it.message}", it) }
    }

    /**
     * WS-push entry for a master→client bolus-progress frame (client side). NSClientV3Service routes
     * `aaps_clientcontrol_progress_<clientId>` settings events here; feeds the client's own BolusProgressData.
     */
    fun handleClientControlProgressEvent(doc: JSONObject) {
        runCatching { clientControlRoundTrip.onProgressDoc(doc) }
            .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl progress dispatch failed: ${it.message}", it) }
    }

    @Volatile private var lastProbeAt = 0L

    // See [NsClient.requestMasterProbe]. Client-only, WS-up-only, rate-limited. Fires a ping (its pong
    // bumps the liveness clock → banner clears) and re-fetches the running-config docs (in case a live
    // push was missed while out of contact). Uses [reachableScope] so it's safe to call before/around
    // service restarts.
    override fun requestMasterProbe() {
        if (!config.AAPSCLIENT || !_wsConnected.value) return
        val now = dateUtil.now()
        if (now - lastProbeAt < PROBE_MIN_INTERVAL_MS) return
        lastProbeAt = now
        reachableScope.launch {
            runCatching { clientControlRoundTrip.sendPing() }
                .onFailure { aapsLogger.error(LTag.NSCLIENT, "ClientControl ping failed: ${it.message}") }
        }
        // Pull any config we missed while disconnected (the worker no-ops on master / GETs both docs on client).
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequest.Builder(LoadSettingsWorker::class.java).build())
    }

    fun scheduleIrregularExecution(refreshToken: Boolean = false) {
        if (refreshToken) {
            handler?.post { executeLoop("REFRESH TOKEN") }
            return
        }
        if (config.AAPSCLIENT || nsClientSource.isEnabled()) {
            var origin = "5_MIN_AFTER_BG"
            var toTime = lastLoadedSrvModified.collections.entries + T.mins(5).plus(T.secs(10)).msecs()
            if (toTime < dateUtil.now()) {
                toTime = dateUtil.now() + T.mins(1).plus(T.secs(0)).msecs()
                origin = "1_MIN_OLD_DATA"
            }
            handler?.postDelayed({ executeLoop(origin) }, toTime - dateUtil.now())
            nsClientRepository.addLog("● NEXT", dateUtil.dateAndTimeAndSecondsString(toTime))
        }
    }

    override suspend fun onStop() {
        handler?.removeCallbacksAndMessages(null)
        handler?.looper?.quit()
        handler = null
        runningConfigurationPublisher.stop()
        preferencesClientPublisher.stop()
        scope.cancel()
        stopService()
        WorkManager.getInstance(context).cancelUniqueWork(JOB_NAME)
        super.onStop()
    }

    override val hasWritePermission: Boolean get() = nsAndroidClient?.lastStatus?.apiPermissions?.isFull() == true
    override val connected: Boolean get() = nsAndroidClient?.lastStatus != null

    // Canonical WS-state holder. Lives on the plugin (singleton) so UI subscribers persist
    // across service rebinds — the service writes here via [setWsConnected] / reads via
    // its `wsConnected` pass-through property.
    private val _wsConnected = MutableStateFlow(false)
    override val wsConnectedFlow: StateFlow<Boolean> = _wsConnected.asStateFlow()
    internal fun setWsConnected(value: Boolean) {
        _wsConnected.value = value
    }

    // Heartbeat from master's devicestatus stream. Stays 0L until the first batch arrives; combined
    // with freshness(pristine=false) this FAILS CLOSED at boot — masterReachable stays false until a
    // first master heartbeat positively confirms the master is alive, instead of optimistically
    // enabling. (A 0L seed + pristine=true, or any non-zero seed, would let a client that boots while
    // the master is offline edit for the whole stale window and silently lose those edits; the WS term
    // can't catch that, since WS is client↔NS, not client↔master.) On AAPSCLIENT, NSDeviceStatusHandler
    // bumps this from the newest devicestatus's own created_at — but ONLY for a LIVE WS push (live=true),
    // never the catch-up/initial worker load, so a stale historical devicestatus pulled at boot can't mark
    // a long-offline master alive. So the client waits for the master's first real-time devicestatus.
    private val _lastDevicestatusReceivedAt = MutableStateFlow(0L)
    override val lastDevicestatusReceivedAt: StateFlow<Long> = _lastDevicestatusReceivedAt.asStateFlow()

    // [heartbeatAt] is the master's devicestatus created_at (publish time), not receipt time. Monotonic —
    // keep the newest known heartbeat; a batch may deliver an older record after a newer one.
    internal fun bumpDevicestatusHeartbeat(heartbeatAt: Long) {
        // Atomic CAS: a heartbeat and a pong can bump concurrently from different WS threads; a two-step
        // read-compare-write would lose the later timestamp. update {} retries until the max wins.
        _lastDevicestatusReceivedAt.update { maxOf(it, heartbeatAt) }
        bumpMasterSignal(heartbeatAt)
    }

    // Unified liveness clock: the newest of ANY authenticated, real-time master signal — a devicestatus
    // heartbeat, a verified Client-Control ACK/pong, or a live config republish. [masterReachable]'s
    // freshness term reads THIS (not devicestatus alone), so an active PING-PONG clears the offline
    // banner without waiting for the next devicestatus push. Same fail-closed seed (0L) + pristine=false.
    private val _lastMasterSignalAt = MutableStateFlow(0L)

    /** Bump the liveness clock from a real-time master signal (pong / live republish / heartbeat). Monotonic. */
    internal fun bumpMasterSignal(at: Long) {
        // Atomic CAS (see bumpDevicestatusHeartbeat): concurrent bumps from WS threads must not lose the later one.
        _lastMasterSignalAt.update { maxOf(it, at) }
    }

    /**
     * Force [masterReachable] offline by staling the liveness clock. Called when a client-control action
     * gets no ack (Unconfirmed): we don't actually know the master is alive, so flip offline — that drives
     * the app-level probe to ping + re-pull, reconciling the real state instead of leaving a stale guess.
     * Self-heals: a pong/heartbeat bumps the clock fresh again within seconds if the master is up.
     */
    internal fun markMasterUnreachable() {
        _lastMasterSignalAt.value = 0L
    }

    // Grace before flagging offline on a WS drop — swallows brief flaps (reconnect storms during NS
    // restarts), short enough that a real outage surfaces in seconds.
    private val wsDisconnectGraceMs = 5_000L

    // Heartbeat staleness threshold (~1.8 loop cycles): one missed devicestatus publication of grace.
    private val heartbeatStaleMs = 9 * 60_000L
    private val heartbeatTickMs = 60_000L

    // App-lifetime scope for [masterReachable] — independent of the restartable [scope] so the derived
    // signal (and its freshness ticker) survives service stop/start.
    private val reachableScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // See [NsClient.masterReachable]. Master: always reachable. Client: ALL of — live WS (falling-edge
    // grace), a fresh master devicestatus heartbeat, a current Client-Control pairing, and not being
    // orphaned (master still lists us in its authorizedClients roster). The pairing + authorized terms
    // matter because a client→master edit rides the signed Client-Control channel: an unpaired (but
    // NS-connected) client looks "reachable" yet has every edit dropped (nextSignedEnvelope returns null
    // when unpaired), and a revoked/orphaned client would have its commands rejected by the master.
    // NsClientControlClientId is the canonical paired marker (see ClientPairingRepository.isPaired),
    // observed so pairing/unpairing updates live; OrphanDetector.authorized is the roster signal
    // (optimistic until a doc proves us excluded). The heartbeat term uses pristine=false so a client
    // FAILS CLOSED before its first master heartbeat (disabled until the master is positively confirmed
    // alive — see the _lastDevicestatusReceivedAt seed above) and times out to stale if heartbeats later
    // stop. Single shared flow (WhileSubscribed) — consumers no longer each rebuild the combine on their own scope.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val masterReachable: StateFlow<Boolean> =
        if (!config.AAPSCLIENT) MutableStateFlow(true).asStateFlow()
        else combine(
            wsConnectedFlow.transformLatest { connected ->
                if (connected) emit(true) else {
                    delay(wsDisconnectGraceMs); emit(false)
                }
            },
            _lastMasterSignalAt.freshness(thresholdMs = heartbeatStaleMs, scope = reachableScope, tickMs = heartbeatTickMs, pristine = false, now = dateUtil::now),
            preferences.observe(StringNonKey.NsClientControlClientId).map { it.isNotEmpty() },
            orphanDetector.authorized,
            // Master's "allow client control" switch, synced master→client (effective value — see
            // RunningConfigurationImpl). Off ⇒ master silently drops commands, so block fast here instead.
            preferences.observe(BooleanKey.NsClientAllowClientControl)
        ) { ws, fresh, paired, authorized, controlAllowed ->
            val reachable = ws && fresh && paired && authorized && controlAllowed
            // Diagnostic (lazy — string built only when NSCLIENT logging is on): shows WHICH term gates.
            // heartbeatAgeMs > heartbeatStaleMs (9 min) ⇒ fresh=false.
            aapsLogger.debug(LTag.NSCLIENT) { "masterReachable=$reachable (ws=$ws fresh=$fresh paired=$paired authorized=$authorized controlAllowed=$controlAllowed heartbeatAgeMs=${dateUtil.now() - lastDevicestatusReceivedAt.value})" }
            reachable
        }
            // Seed FALSE (fail-closed): before the combine first computes — and on a cold start / WS
            // resubscribe — the client must not momentarily report the master "reachable" and flash edit
            // controls open. The combine settles to the real value within a tick of subscription.
            .stateIn(reachableScope, SharingStarted.WhileSubscribed(5000), false)

    // See [NsClient.pairedFlow]. STABLE pairing marker (flips only on pair/unpair) — kept separate from
    // the flapping [masterReachable] so persistent chrome (nav tabs, Manage entries, search visibility)
    // can HIDE mutating UI on an unpaired client without churn. Master: always paired. Client: observe the
    // canonical NsClientControlClientId marker (same term masterReachable uses on line ~557). Seeded with
    // the SYNCHRONOUS current value (not false) so a [PreferenceVisibilityContext] read sees the correct
    // .value even before any collector subscribes.
    override val masterOrPairedClientFlow: StateFlow<Boolean> =
        if (!config.AAPSCLIENT) MutableStateFlow(true).asStateFlow()
        else preferences.observe(StringNonKey.NsClientControlClientId)
            .map { it.isNotEmpty() }
            .stateIn(reachableScope, SharingStarted.WhileSubscribed(5000), preferences.get(StringNonKey.NsClientControlClientId).isNotEmpty())

    // See [NsClient.masterControlAllowed]. Master: always true. Client: true unless paired AND the master's
    // synced "allow client control" switch is off — so an UNPAIRED client shows the generic unreachable banner,
    // not this one. UI-only (the offline banner picks its message from this); masterReachable folds the raw
    // synced term in directly. Seed true so the initial/unpaired state never flashes the control-disabled message.
    override val masterControlAllowed: StateFlow<Boolean> =
        if (!config.AAPSCLIENT) MutableStateFlow(true).asStateFlow()
        else combine(
            preferences.observe(StringNonKey.NsClientControlClientId).map { it.isNotEmpty() },
            preferences.observe(BooleanKey.NsClientAllowClientControl)
        ) { paired, control -> !paired || control }
            .stateIn(reachableScope, SharingStarted.WhileSubscribed(5000), true)

    // See [NsClient.pairedClientCountFlow]. Master-side count of ACTIVE paired clients (pending offers
    // excluded), driven off the same roster the Authorized clients screen shows. Always 0 on a client.
    // Seeded with the synchronous current count so the SetupWizard status line renders correctly on first frame.
    override val pairedClientCountFlow: StateFlow<Int> =
        if (config.AAPSCLIENT) MutableStateFlow(0).asStateFlow()
        else authorizedClientsRepository.observe()
            .map { list -> list.count { it.state == ClientState.Active } }
            .stateIn(reachableScope, SharingStarted.WhileSubscribed(5000), authorizedClientsRepository.current(dateUtil.now()).count { it.state == ClientState.Active })

    private fun setClient() {
        if (nsAndroidClient == null)
            nsAndroidClient = NSAndroidClientImpl(
                baseUrl = preferences.get(StringKey.NsClientUrl).lowercase().replace("https://", "").replace(Regex("/$"), ""),
                accessToken = preferences.get(StringKey.NsClientAccessToken),
                context = context,
                logging = l.findByName(LTag.NSCLIENT.tag).enabled && (config.isEngineeringMode() || config.isDev()),
                logger = { msg -> aapsLogger.debug(LTag.HTTP, msg) }
            )
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
            // Tear down sockets synchronously before unbinding so a quick rebind
            // (e.g. via restartOnChange) doesn't race the async service onDestroy.
            nsClientV3Service?.shutdownWebsockets()
            if (nsClientV3Service != null) context.unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        nsClientV3Service = null
    }

    override fun resend(reason: String) {
        // If WS is enabled, download is triggered by changes in NS. Thus, uploadOnly
        // Exception is after reset to full sync (initialLoadFinished == false), where
        // older data must be loaded directly and then continue over WS
        if (preferences.get(BooleanKey.NsClient3UseWs) && initialLoadFinished)
            executeUpload("START $reason")
        else
            executeLoop("START $reason")
    }

    override fun pause(newState: Boolean) {
        // Cancel any in-flight WorkManager job so a stuck worker can't keep
        // workIsRunning() == true after unpause and silently block all uploads
        // (every DB_CHANGED would otherwise just log "Already running").
        if (newState) WorkManager.getInstance(context).cancelUniqueWork(JOB_NAME)
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
            NsClient.Collection.SETTINGS   -> lastLoadedSrvModified.collections.settings == 0L
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

    // --- Bounded retry for transient NS UPDATE failures ---
    // A 404 on an UPDATE previously fell through to `return true`, advancing the per-collection sync
    // cursor and permanently dropping the change. Worst case: NS returns 404 to a PATCH issued ~1-2s
    // after the record's CREATE (NS read-after-write lag — the just-created treatment is briefly not
    // visible to find-by-identifier), so e.g. a SUSPENDED_BY_PUMP duration-cut is lost and every
    // follower stays stuck "Pump suspended". Now a 404 on a real UPDATE keeps the cursor in place and
    // retries on a later sync cycle (by then NS has the record), bounded per nsId so a permanently
    // failing record can't wedge its collection's cursor. Keyed by nsId (globally-unique), not a
    // single slot, because doUpload() runs all collections in one pass and several share a handler.
    // Deliberately NOT retried: 400 (deterministic bad request) and deletions (the NS SDK reports a
    // successful idempotent delete as 404, see NSAndroidClientImpl.updateXxx).
    private val failedUpdateCounts = HashMap<String, Int>()
    private val maxUpdateRetries = 5

    /**
     * @return true  -> retry: caller must `return false` so the sync cursor is NOT advanced
     *         false -> nothing to retry (not an UPDATE / a deletion / no id) or retries exhausted:
     *                  caller proceeds normally and the cursor advances
     */
    private fun retryUpdateLater(operation: Operation, id: String?, isDeletion: Boolean): Boolean {
        if (operation != Operation.UPDATE || isDeletion || id.isNullOrEmpty()) return false
        val count = (failedUpdateCounts[id] ?: 0) + 1
        if (count >= maxUpdateRetries) {
            nsClientRepository.addLog("◄ GIVE_UP", "$id after $count attempts")
            failedUpdateCounts.remove(id)
            return false
        }
        failedUpdateCounts[id] = count
        return true
    }

    /** Clear retry bookkeeping once an UPDATE for [id] finally succeeds. */
    private fun clearUpdateRetry(operation: Operation, id: String?) {
        if (operation == Operation.UPDATE && !id.isNullOrEmpty()) failedUpdateCounts.remove(id)
    }

    suspend fun nsAdd(collection: String, dataPair: DataSyncSelector.DataPair, progress: String, profile: Profile? = null): Boolean =
        dbOperation(collection, dataPair, progress, Operation.CREATE, profile)

    suspend fun nsUpdate(collection: String, dataPair: DataSyncSelector.DataPair, progress: String, profile: Profile? = null): Boolean =
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
                    200  -> {
                        nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                        clearUpdateRetry(operation, id)
                    }

                    201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}")
                    400  -> nsClientRepository.addLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                    404  -> {
                        nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                        if (!config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS) &&
                            retryUpdateLater(operation, id, isDeletion = !dataPair.value.isValid)
                        ) return false
                    }

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

    private suspend fun dbOperationCalibrations(collection: String = "entries", dataPair: DataSyncSelector.PairCalibrationEntry, progress: String, operation: Operation): Boolean {
        val call = when (operation) {
            Operation.CREATE -> nsAndroidClient?.let { return@let it::createCalibration }
            Operation.UPDATE -> nsAndroidClient?.let { return@let it::updateCalibration }
        }
        try {
            val data = dataPair.value.toNSMbgV3()
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
                    200  -> {
                        nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                        clearUpdateRetry(operation, id)
                    }

                    201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}")
                    400  -> nsClientRepository.addLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                    404  -> {
                        nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                        if (!config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS) &&
                            retryUpdateLater(operation, id, isDeletion = !dataPair.value.isValid)
                        ) return false
                    }

                    else -> {
                        nsClientRepository.addLog("◄ ERROR", "${result.errorResponse} ")
                        return config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS)
                    }
                }
                result.identifier?.let {
                    dataPair.value.ids.nightscoutId = it
                    storeDataForDb.addToNsIdCalibrationEntries(dataPair.value)
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
                    200  -> {
                        nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                        clearUpdateRetry(operation, id)
                    }

                    201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}")
                    400  -> nsClientRepository.addLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                    404  -> {
                        nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                        if (!config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS) &&
                            retryUpdateLater(operation, id, isDeletion = !dataPair.value.isValid)
                        ) return false
                    }

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
                        200  -> {
                            nsClientRepository.addLog("◄ UPDATED", "OK ${dataPair.value.javaClass.simpleName}")
                            clearUpdateRetry(operation, id)
                        }

                        201  -> nsClientRepository.addLog("◄ ADDED", "OK ${dataPair.value.javaClass.simpleName}")
                        400  -> nsClientRepository.addLog("◄ FAIL", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                        404  -> {
                            nsClientRepository.addLog("◄ NOT_FOUND", "${dataPair.value.javaClass.simpleName} ${result.errorResponse}")
                            if (!config.isEnabled(ExternalOptions.IGNORE_NS_V3_ERRORS) &&
                                retryUpdateLater(operation, id, isDeletion = (dataPair.value as? HasIDs)?.isValid == false)
                            ) return false
                        }

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
            // entries collection carries both sgv (PairGlucoseValue) and AAPS calibration (PairCalibrationEntry)
            "entries"      -> when (dataPair) {
                is DataSyncSelector.PairCalibrationEntry -> dbOperationCalibrations(dataPair = dataPair, progress = progress, operation = operation)
                else                                     -> dbOperationEntries(dataPair = dataPair as DataSyncSelector.PairGlucoseValue, progress = progress, operation = operation)
            }

            "food"         -> dbOperationFood(dataPair = dataPair as DataSyncSelector.PairFood, progress = progress, operation = operation)
            "treatments"   -> dbOperationTreatments(dataPair = dataPair, progress = progress, operation = operation, profile = profile)
            "settings"     -> dbOperationSettings(dataPair = dataPair, progress = progress, operation = operation)

            else           -> false
        }

    @Suppress("UNUSED_PARAMETER")
    private fun dbOperationSettings(dataPair: DataSyncSelector.DataPair, progress: String, operation: Operation): Boolean {
        // Skeleton stub. Payload/identifier/upload trigger not defined yet — see NS API3 settings collection.
        aapsLogger.debug(LTag.NSCLIENT, "dbOperationSettings: no-op ($operation, $progress)")
        nsClientRepository.addLog("► SETTINGS", "no-op")
        return true
    }

    fun storeLastLoadedSrvModified() {
        preferences.put(NsclientStringKey.V3LastModified, Json.encodeToString(LastModified.serializer(), lastLoadedSrvModified))
    }

    internal fun executeLoop(origin: String) {
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
            // Don't drop: queue a follow-up. Observer in onStart fires it on idle.
            pendingLoop.set(true)
            nsClientRepository.addLog("● RUN", "Already running $origin (queued loop)")
            return
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
            // No-op stub. When filled in, must update lastLoadedSrvModified.collections.settings,
            // otherwise isFirstLoad(SETTINGS) stays true forever.
            .then(OneTimeWorkRequest.Builder(LoadSettingsWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadDeviceStatusWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(DataSyncWorker::class.java).build())
            .enqueue()
    }

    fun endFullSync() {
        synchronized(fullSyncSemaphore) {
            doingFullSync = false
        }
    }

    private fun executeUpload(origin: String) {
        if (preferences.get(NsclientBooleanKey.NsPaused)) {
            nsClientRepository.addLog("● RUN", "paused")
            return
        }
        if (!isAllowed) {
            nsClientRepository.addLog("● RUN", blockingReason)
            return
        }
        if (workIsRunning()) {
            // Don't drop: queue a follow-up. Observer in onStart fires it on idle.
            pendingUpload.set(true)
            nsClientRepository.addLog("● RUN", "Already running $origin (queued upload)")
            return
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
            if (workInfo.state.isActive())
                return true
        return false
    }

    /** Shared with the re-run observer in onStart so both sides agree on "active". */
    private fun WorkInfo.State.isActive(): Boolean =
        this == WorkInfo.State.BLOCKED || this == WorkInfo.State.ENQUEUED || this == WorkInfo.State.RUNNING

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
            // Remote control: the master's allow/stop switch. The rich control lives on the Authorized clients
            // screen (with the paired-client list); this category exposes the same key on the settings screen so it
            // is reachable from search (otherwise the key has no parent screen and search dumps the user in full
            // preferences). Empty on a client — the key is hidden there (showInNsClientMode = false).
            PreferenceSubScreenDef(
                key = "ns_client_remote_control",
                titleResId = R.string.ns_remote_control_options,
                items = listOf(
                    BooleanKey.NsClientAllowClientControl
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