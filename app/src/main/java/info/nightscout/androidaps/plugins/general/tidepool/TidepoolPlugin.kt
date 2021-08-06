package info.nightscout.androidaps.plugins.general.tidepool

import android.content.Context
import android.text.Spanned
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader.ConnectionStatus.CONNECTED
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader.ConnectionStatus.DISCONNECTED
import info.nightscout.androidaps.plugins.general.tidepool.comm.UploadChunk
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolStatus
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolUpdateGUI
import info.nightscout.androidaps.plugins.general.tidepool.utils.RateLimit
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TidepoolPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBusWrapper,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val tidepoolUploader: TidepoolUploader,
    private val uploadChunk: UploadChunk,
    private val sp: SP,
    private val rateLimit: RateLimit,
    private val receiverStatusStore: ReceiverStatusStore
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .pluginName(R.string.tidepool)
    .shortName(R.string.tidepool_shortname)
    .fragmentClass(TidepoolFragment::class.qualifiedName)
    .preferencesId(R.xml.pref_tidepool)
    .description(R.string.description_tidepool),
    aapsLogger, resourceHelper, injector
) {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val listLog = ArrayList<EventTidepoolStatus>()
    var textLog: Spanned = HtmlHelper.fromHtml("")

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
                if (tidepoolUploader.connectionStatus != CONNECTED) {
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
            .filter { it.glucoseValue != null } // better would be optional in API level >24
            .map { it.glucoseValue }
            .subscribe({ bgReading ->
                if (bgReading!!.timestamp  < uploadChunk.getLastEnd())
                    uploadChunk.setLastEnd(bgReading.timestamp )
                if (isEnabled(PluginType.GENERAL)
                    && (!sp.getBoolean(R.string.key_tidepool_only_while_charging, false) || receiverStatusStore.isCharging)
                    && (!sp.getBoolean(R.string.key_tidepool_only_while_unmetered, false) || receiverStatusStore.isWifiConnected)
                    && rateLimit.rateLimit("tidepool-new-data-upload", T.mins(4).secs().toInt()))
                    doUpload()
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                if (event.isChanged(resourceHelper, R.string.key_tidepool_dev_servers)
                    || event.isChanged(resourceHelper, R.string.key_tidepool_username)
                    || event.isChanged(resourceHelper, R.string.key_tidepool_password)
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

        val tidepoolTestLogin: Preference? = preferenceFragment.findPreference(resourceHelper.gs(R.string.key_tidepool_test_login))
        tidepoolTestLogin?.setOnPreferenceClickListener {
            preferenceFragment.context?.let {
                tidepoolUploader.testLogin(it)
            }
            false
        }
    }

    private fun doUpload() =
        when (tidepoolUploader.connectionStatus) {
            DISCONNECTED -> tidepoolUploader.doLogin(true)
            CONNECTED -> tidepoolUploader.doUpload()

            else         -> {
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
            ToastUtils.showToastInUiThread(context, rxBus, "Out of memory!\nStop using this phone !!!", R.raw.error)
        }
    }

}