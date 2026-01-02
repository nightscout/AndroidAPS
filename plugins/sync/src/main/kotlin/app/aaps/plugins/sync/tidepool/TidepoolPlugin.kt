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

    private fun doUpload(from: String?) {
        //aapsLogger.debug(LTag.TIDEPOOL, "doUpload from $from")
        return when (authFlowOut.connectionStatus) {
            AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN       -> tidepoolUploader.doLogin(true, "doUpload $from NOT_LOGGED_IN")
            AuthFlowOut.ConnectionStatus.FAILED              -> tidepoolUploader.doLogin(true, "doUpload $from FAILED")
            AuthFlowOut.ConnectionStatus.NO_SESSION          -> tidepoolUploader.doLogin(true, "doUpload $from NO_SESSION")
            AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED -> tidepoolUploader.doUpload(from)

            else                                             -> aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from do nothing ${authFlowOut.connectionStatus}")
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

    override val status: String
        get() = authFlowOut.connectionStatus.name
    override val hasWritePermission: Boolean
        get() = authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED
    override val connected: Boolean
        get() = authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "tidepool_connection_options" && requiredKey != "tidepool_advanced") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "tidepool_settings"
            title = rh.gs(R.string.tidepool)
            initialExpandedChildrenCount = 0
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "tidepool_connection_options"
                title = rh.gs(R.string.connection_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseCellular, title = R.string.ns_cellular))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseRoaming, title = R.string.ns_allow_roaming))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseWifi, title = R.string.ns_wifi))
                addPreference(AdaptiveStringPreference(ctx = context, stringKey = StringKey.NsClientWifiSsids, dialogMessage = R.string.ns_wifi_allowed_ssids, title = R.string.ns_wifi_ssids))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseOnBattery, title = R.string.ns_battery))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.NsClientUseOnCharging, title = R.string.ns_charging))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "tidepool_advanced"
                title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = TidepoolBooleanKey.UseTestServers, summary = R.string.summary_tidepool_dev_servers, title = R.string.title_tidepool_dev_servers))
            })
        }
    }
}