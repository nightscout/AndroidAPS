package info.nightscout.plugins.sync.tidepool

import android.content.Context
import android.text.Spanned
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.interfaces.sync.Sync
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.tidepool.comm.TidepoolUploader
import info.nightscout.plugins.sync.tidepool.comm.UploadChunk
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolDoUpload
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolResetData
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolStatus
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolUpdateGUI
import info.nightscout.plugins.sync.tidepool.utils.RateLimit
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.rx.events.EventNewBG
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TidepoolPlugin @Inject constructor(
    injector: HasAndroidInjector,
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
    private val receiverStatusStore: ReceiverStatusStore,
    private val uiInteraction: UiInteraction
) : Sync, PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .pluginName(R.string.tidepool)
        .shortName(R.string.tidepool_shortname)
        .fragmentClass(TidepoolFragment::class.qualifiedName)
        .preferencesId(R.xml.pref_tidepool)
        .description(R.string.description_tidepool),
    aapsLogger, rh, injector
) {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val listLog = ArrayList<EventTidepoolStatus>()
    var textLog: Spanned = HtmlHelper.fromHtml("")

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    override fun onStart() {
        super.onStart()
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
            .subscribe({ event -> addToLog(event) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .filter { it.glucoseValueTimestamp != null } // better would be optional in API level >24
            .map { it.glucoseValueTimestamp!! }
            .subscribe({ bgReadingTimestamp ->
                           if (bgReadingTimestamp < uploadChunk.getLastEnd())
                               uploadChunk.setLastEnd(bgReadingTimestamp)
                           if (isEnabled()
                               && (!sp.getBoolean(R.string.key_tidepool_only_while_charging, false) || receiverStatusStore.isCharging)
                               && (!sp.getBoolean(R.string.key_tidepool_only_while_unmetered, false) || receiverStatusStore.isWifiConnected)
                               && rateLimit.rateLimit("tidepool-new-data-upload", T.mins(4).secs().toInt())
                           )
                               doUpload()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(rh.gs(R.string.key_tidepool_dev_servers))
                               || event.isChanged(rh.gs(R.string.key_tidepool_username))
                               || event.isChanged(rh.gs(R.string.key_tidepool_password))
                           )
                               tidepoolUploader.resetInstance()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({}, fabricPrivacy::logException) // TODO start upload on wifi connect

    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)

        val tidepoolTestLogin: Preference? = preferenceFragment.findPreference(rh.gs(R.string.key_tidepool_test_login))
        tidepoolTestLogin?.setOnPreferenceClickListener {
            preferenceFragment.context?.let {
                tidepoolUploader.testLogin(it)
            }
            false
        }
    }

    private fun doUpload() =
        when (tidepoolUploader.connectionStatus) {
            TidepoolUploader.ConnectionStatus.DISCONNECTED -> tidepoolUploader.doLogin(true)
            TidepoolUploader.ConnectionStatus.CONNECTED    -> tidepoolUploader.doUpload()

            else                                           -> {
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
        } catch (e: OutOfMemoryError) {
            uiInteraction.showToastAndNotification(context, "Out of memory!\nStop using this phone !!!", R.raw.error)
        }
    }

    override val status: String
        get() = tidepoolUploader.connectionStatus.name
    override val hasWritePermission: Boolean
        get() = tidepoolUploader.connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTED
    override val connected: Boolean
        get() = tidepoolUploader.connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTED
}