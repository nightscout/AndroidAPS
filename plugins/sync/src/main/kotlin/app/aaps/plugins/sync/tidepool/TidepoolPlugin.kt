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
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.sync.Tidepool
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.utils.HtmlHelper
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveClickPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsShared.events.EventConnectivityOptionChanged
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.comm.UploadChunk
import app.aaps.plugins.sync.tidepool.events.EventTidepoolDoUpload
import app.aaps.plugins.sync.tidepool.events.EventTidepoolResetData
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.events.EventTidepoolUpdateGUI
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TidepoolPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val tidepoolUploader: TidepoolUploader,
    private val uploadChunk: UploadChunk,
    private val sp: SP,
    private val rateLimit: RateLimit,
    private val receiverDelegate: ReceiverDelegate,
    private val uiInteraction: UiInteraction
) : Sync, Tidepool, PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .pluginName(R.string.tidepool)
        .shortName(R.string.tidepool_shortname)
        .fragmentClass(TidepoolFragment::class.qualifiedName)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_tidepool),
    aapsLogger, rh
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
                           if (isAllowed) doUpload()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTidepoolDoUpload::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ doUpload() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTidepoolResetData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (tidepoolUploader.connectionStatus != TidepoolUploader.ConnectionStatus.CONNECTED) {
                               aapsLogger.debug(LTag.TIDEPOOL, "Not connected for delete Dataset")
                           } else {
                               tidepoolUploader.deleteDataSet()
                               sp.putLong(R.string.key_tidepool_last_end, 0)
                               tidepoolUploader.doLogin()
                           }
                       }, fabricPrivacy::logException)
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
                                   doUpload()
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(BooleanKey.TidepoolUseTestServers.key)
                               || event.isChanged(StringKey.TidepoolUsername.key)
                               || event.isChanged(StringKey.TidepoolPassword.key)
                           )
                               tidepoolUploader.resetInstance()
                       }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    private fun doUpload() =
        when (tidepoolUploader.connectionStatus) {
            TidepoolUploader.ConnectionStatus.DISCONNECTED -> tidepoolUploader.doLogin(true)
            TidepoolUploader.ConnectionStatus.CONNECTED    -> tidepoolUploader.doUpload()

            else                                           -> {}
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
        } catch (e: OutOfMemoryError) {
            uiInteraction.showToastAndNotification(context, "Out of memory!\nStop using this phone !!!", app.aaps.core.ui.R.raw.error)
        }
    }

    override val status: String
        get() = tidepoolUploader.connectionStatus.name
    override val hasWritePermission: Boolean
        get() = tidepoolUploader.connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTED
    override val connected: Boolean
        get() = tidepoolUploader.connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTED

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "tidepool_connection_options" && requiredKey != "tidepool_advanced") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "tidepool_settings"
            title = rh.gs(R.string.tidepool)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = StringKey.TidepoolUsername, summary = R.string.summary_tidepool_username, title = R.string.title_tidepool_username,
                    validatorParams = DefaultEditTextValidator.Parameters(testType = EditTextValidator.TEST_EMAIL)
                )
            )
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = StringKey.TidepoolPassword, dialogMessage = R.string.summary_tidepool_password, title = R.string.title_tidepool_password,
                    validatorParams = DefaultEditTextValidator.Parameters(testType = EditTextValidator.TEST_MIN_LENGTH, minLength = 1)
                )
            )
            addPreference(
                AdaptiveClickPreference(ctx = context, stringKey = StringKey.TidepoolTestLogin, title = R.string.title_tidepool_test_login,
                                        onPreferenceClickListener = {
                                            tidepoolUploader.testLogin(preferenceManager.context)
                                            true
                                        })
            )
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
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.TidepoolUseTestServers, summary = R.string.summary_tidepool_dev_servers, title = R.string.title_tidepool_dev_servers))
            })
        }
    }
}