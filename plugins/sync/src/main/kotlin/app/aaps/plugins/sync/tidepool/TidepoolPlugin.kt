package app.aaps.plugins.sync.tidepool

import app.aaps.core.data.model.GV
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.sync.Tidepool
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginTidepool
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.comm.UploadChunk
import app.aaps.plugins.sync.tidepool.compose.TidepoolComposeContent
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.events.EventTidepoolDoUpload
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolLongNonKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TidepoolPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val fabricPrivacy: FabricPrivacy,
    private val tidepoolUploader: TidepoolUploader,
    private val uploadChunk: UploadChunk,
    private val rateLimit: RateLimit,
    private val receiverDelegate: ReceiverDelegate,
    private val authFlowOut: AuthFlowOut,
    private val tidepoolRepository: TidepoolRepository,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
) : Sync, Tidepool, PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .pluginName(R.string.tidepool)
        .shortName(R.string.tidepool_shortname)
        .icon(IcPluginTidepool)
        .composeContent {
            TidepoolComposeContent(
                dateUtil = dateUtil,
                onLogin = { authFlowOut.doTidePoolInitialLogin("menu") },
                onLogout = {
                    authFlowOut.clearAllSavedData()
                    tidepoolUploader.resetInstance()
                },
                onUploadNow = { rxBus.send(EventTidepoolDoUpload()) },
                onFullSync = { preferences.put(TidepoolLongNonKey.LastEnd, 0) },
                onClearLog = { tidepoolRepository.clearLog() }
            )
        }
        .description(R.string.description_tidepool),
    ownPreferences = listOf(
        TidepoolBooleanKey::class.java, TidepoolLongNonKey::class.java,
        TidepoolStringNonKey::class.java
    ),
    aapsLogger, rh, preferences
) {

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val isAllowed get() = receiverDelegate.allowed

    override fun onStart() {
        super.onStart()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        receiverDelegate.connectivityStatusFlow
            .drop(1) // skip initial value
            .onEach { ev ->
                rxBus.send(EventTidepoolStatus("● CONNECTIVITY ${ev.blockingReason}"))
                tidepoolUploader.resetInstance()
                if (isAllowed) doUpload("CONNECTIVITY")
            }.launchIn(scope)
        disposable += rxBus
            .toObservable(EventTidepoolDoUpload::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ doUpload(EventTidepoolDoUpload::class.simpleName) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTidepoolStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           tidepoolRepository.addLog(event.status)
                           tidepoolRepository.updateConnectionStatus(authFlowOut.connectionStatus)
                           // Pass to setup wizard
                           rxBus.send(EventSWSyncStatus(event.status))
                       }, fabricPrivacy::logException)
        persistenceLayer.observeChanges(GV::class.java)
            .onEach { gvList ->
                gvList.maxByOrNull { it.timestamp }?.let { gv ->
                    if (gv.timestamp < uploadChunk.getLastEnd())
                        uploadChunk.setLastEnd(gv.timestamp)
                    if (isAllowed && rateLimit.rateLimit("tidepool-new-data-upload", T.mins(4).secs().toInt()))
                        doUpload("GlucoseValue")
                }
            }.launchIn(scope)
        preferences.observe(TidepoolBooleanKey.UseTestServers).drop(1).onEach {
            authFlowOut.clearAllSavedData()
            tidepoolUploader.resetInstance()
        }.launchIn(scope)
        authFlowOut.initAuthState()
    }

    override fun onStop() {
        scope.cancel()
        disposable.clear()
        super.onStop()
    }

    /**
     * Check if connectivity settings allow upload
     * Separates connectivity constraints from auth state management
     */
    private fun isConnectivityAllowed(): Boolean = isAllowed

    /**
     * Initiate upload with improved state management
     *
     * Key improvement: Check connectivity BEFORE auth state
     * - If blocked by connectivity: skip silently without changing auth state
     * - If allowed: handle auth state transitions normally
     * - Auth state no longer gets "stuck" in BLOCKED when connectivity is restored
     */
    private fun doUpload(from: String?) {
        aapsLogger.debug(LTag.TIDEPOOL, "doUpload from=$from isAllowed=${isConnectivityAllowed()} status=${authFlowOut.connectionStatus}")

        // IMPROVEMENT: Check connectivity first, before examining auth state
        // This prevents mixing connectivity constraints with auth state
        if (!isConnectivityAllowed()) {
            aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from: Blocked by connectivity settings")
            // Send status event so user knows why upload is blocked (rate limited to avoid spam)
            if (rateLimit.rateLimit("tidepool-connectivity-blocked-notification", T.mins(5).secs().toInt())) {
                rxBus.send(EventTidepoolStatus("Upload blocked by connectivity settings (check WiFi/cellular/battery restrictions)"))
            }
            // Don't change auth state - just skip this upload attempt
            // When connectivity is restored, next doUpload() will proceed normally
            return
        }

        // IMPROVEMENT: Clean auth state machine without BLOCKED
        // Connectivity is handled above, so we only deal with authentication states here
        when (authFlowOut.connectionStatus) {
            // Authentication needed
            AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN -> tidepoolUploader.doLogin(true, "doUpload $from NOT_LOGGED_IN")
            AuthFlowOut.ConnectionStatus.FAILED -> tidepoolUploader.doLogin(true, "doUpload $from FAILED")
            AuthFlowOut.ConnectionStatus.NO_SESSION -> tidepoolUploader.doLogin(true, "doUpload $from NO_SESSION")

            // Ready to upload
            AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED -> scope.launch { tidepoolUploader.doUpload(from) }

            // Transient states - wait for completion
            AuthFlowOut.ConnectionStatus.FETCHING_TOKEN -> aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from: Already fetching token")

            // REMOVED: BLOCKED case - connectivity is now checked separately above
            // This prevents the state machine from getting stuck when connectivity changes

            else -> aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from: Unhandled state ${authFlowOut.connectionStatus}")
        }
    }

    override val status: String
        get() = if (!isConnectivityAllowed()) {
            "BLOCKED (connectivity)"
        } else {
            authFlowOut.connectionStatus.name
        }

    /**
     * IMPROVED: Write permission requires both connectivity AND auth
     */
    override val hasWritePermission: Boolean
        get() = isConnectivityAllowed() &&
            authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED

    /**
     * IMPROVED: Connected requires both connectivity AND auth
     */
    override val connected: Boolean
        get() = isConnectivityAllowed() &&
            authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "tidepool_settings",
        titleResId = R.string.tidepool,
        items = listOf(
            PreferenceSubScreenDef(
                key = "tidepool_connection_options",
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
                key = "tidepool_advanced",
                titleResId = app.aaps.core.ui.R.string.advanced_settings_title,
                items = listOf(
                    TidepoolBooleanKey.UseTestServers
                )
            )
        ),
        icon = pluginDescription.icon
    )

}