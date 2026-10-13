package app.aaps.plugins.sync.tidepool

import android.content.Context
import android.text.Spanned
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.sync.Tidepool
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.HtmlHelper
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.comm.UploadChunk
import app.aaps.plugins.sync.tidepool.events.EventTidepoolDoUpload
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.events.EventTidepoolUpdateGUI
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolLongNonKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TidepoolPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val tidepoolUploader: TidepoolUploader,
    private val uploadChunk: UploadChunk,
    private val rateLimit: RateLimit,
    private val receiverDelegate: ReceiverDelegate,
    private val uiInteraction: UiInteraction,
    private val authFlowOut: AuthFlowOut,
) : Sync, Tidepool, PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .pluginName(R.string.tidepool)
        .shortName(R.string.tidepool_shortname)
        .fragmentClass(TidepoolFragment::class.qualifiedName)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_tidepool),
    ownPreferences = listOf(
        TidepoolBooleanKey::class.java, TidepoolLongNonKey::class.java,
        TidepoolStringNonKey::class.java
    ),
    aapsLogger, rh, preferences
) {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val listLog = ArrayList<EventTidepoolStatus>()
    var textLog: Spanned = HtmlHelper.fromHtml("")
    private val isAllowed get() = receiverDelegate.allowed

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventConnectivityOptionChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev ->
                           rxBus.send(EventNSClientNewLog("● CONNECTIVITY", ev.blockingReason))
                           tidepoolUploader.resetInstance()
                           if (isAllowed) doUpload(EventConnectivityOptionChanged::class.simpleName)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTidepoolDoUpload::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ doUpload(EventTidepoolDoUpload::class.simpleName) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTidepoolStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           addToLog(event)
                           // Pass to setup wizard
                           rxBus.send(EventSWSyncStatus(event.status))
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           it.glucoseValueTimestamp?.let { bgReadingTimestamp ->
                               if (bgReadingTimestamp < uploadChunk.getLastEnd())
                                   uploadChunk.setLastEnd(bgReadingTimestamp)
                               if (isAllowed && rateLimit.rateLimit("tidepool-new-data-upload", T.mins(4).secs().toInt()))
                                   doUpload(EventNewBG::class.simpleName)
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(TidepoolBooleanKey.UseTestServers.key)) {
                               authFlowOut.clearAllSavedData()
                               tidepoolUploader.resetInstance()
                           }
                       }, fabricPrivacy::logException)
        authFlowOut.initAuthState()
    }

    override fun onStop() {
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
        return when (authFlowOut.connectionStatus) {
            // Authentication needed
            AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN       -> tidepoolUploader.doLogin(true, "doUpload $from NOT_LOGGED_IN")
            AuthFlowOut.ConnectionStatus.FAILED              -> tidepoolUploader.doLogin(true, "doUpload $from FAILED")
            AuthFlowOut.ConnectionStatus.NO_SESSION          -> tidepoolUploader.doLogin(true, "doUpload $from NO_SESSION")

            // Ready to upload
            AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED -> tidepoolUploader.doUpload(from)

            // Transient states - wait for completion
            AuthFlowOut.ConnectionStatus.FETCHING_TOKEN      -> aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from: Already fetching token")

            // REMOVED: BLOCKED case - connectivity is now checked separately above
            // This prevents the state machine from getting stuck when connectivity changes

            else                                             -> aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from: Unhandled state ${authFlowOut.connectionStatus}")
        }
    }

    @Synchronized
    private fun addToLog(ev: EventTidepoolStatus) {
        synchronized(listLog) {
            listLog.add(ev)
            // remove the first line if log is too large
            if (listLog.size >= Constants.MAX_LOG_LINES) {
                listLog.removeAt(0)
            }
        }
        rxBus.send(EventTidepoolUpdateGUI())
    }

    @Synchronized
    fun updateLog() {
        try {
            val newTextLog = StringBuilder()
            synchronized(listLog) {
                for (log in listLog) {
                    newTextLog.append(log.toPreparedHtml())
                }
            }
            textLog = HtmlHelper.fromHtml(newTextLog.toString())
        } catch (_: OutOfMemoryError) {
            uiInteraction.showToastAndNotification(context, "Out of memory!\nStop using this phone !!!", app.aaps.core.ui.R.raw.error)
        }
    }

    /**
     * IMPROVED: Status shows connectivity state for better UX
     * If blocked by connectivity settings, shows "BLOCKED (connectivity)"
     * Otherwise shows actual auth state
     */
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

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "tidepool_connection_options") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "tidepool_settings"
            title = rh.gs(R.string.tidepool)
            initialExpandedChildrenCount = 0
            // Add direct preference to make category expandable (like NSClient pattern)
            // Without this, category with only nested PreferenceScreens is not clickable
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = TidepoolBooleanKey.UseTestServers, summary = R.string.summary_tidepool_dev_servers, title = R.string.title_tidepool_dev_servers))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "tidepool_connection_options"
                title = rh.gs(R.string.connection_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseCellular, title = R.string.ns_cellular))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseRoaming, title = R.string.ns_allow_roaming))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseWifi, title = R.string.ns_wifi))
                addPreference(AdaptiveStringPreference(ctx = context, stringKey = StringKey.NsClientWifiSsids, dialogMessage = R.string.ns_wifi_allowed_ssids, title = R.string.ns_wifi_ssids, validatorParams = DefaultEditTextValidator.Parameters(emptyAllowed = true)))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseOnBattery, title = R.string.ns_battery))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseOnCharging, title = R.string.ns_charging))
            })
            // Advanced screen removed - UseTestServers moved to top level
        }
    }
}