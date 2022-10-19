package info.nightscout.androidaps.plugins.sync.nsclientV3

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.text.Spanned
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.interfaces.Sync
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
import info.nightscout.androidaps.plugins.sync.nsclient.NSClientFragment
import info.nightscout.androidaps.plugins.sync.nsclient.NsClientReceiverDelegate
import info.nightscout.androidaps.plugins.sync.nsclient.data.AlarmAck
import info.nightscout.androidaps.plugins.sync.nsclient.data.NSAlarm
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientUpdateGUI
import info.nightscout.androidaps.plugins.sync.nsclient.services.NSClientService
import info.nightscout.androidaps.plugins.sync.nsclientV3.events.EventNSClientV3Resend
import info.nightscout.androidaps.plugins.sync.nsclientV3.events.EventNSClientV3Status
import info.nightscout.androidaps.plugins.sync.nsclientV3.workers.LoadBgWorker
import info.nightscout.androidaps.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import info.nightscout.androidaps.plugins.sync.nsclientV3.workers.LoadStatusWorker
import info.nightscout.androidaps.plugins.sync.nsclientV3.workers.LoadTreatmentsWorker
import info.nightscout.androidaps.plugins.sync.nsclientV3.workers.ProcessTreatmentsWorker
import info.nightscout.androidaps.receivers.DataWorkerStorage
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper.fromHtml
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.workflow.LoadBgDataWorker
import info.nightscout.sdk.NSAndroidClientImpl
import info.nightscout.sdk.NSAndroidRxClientImpl
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.interfaces.NSAndroidRxClient
import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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
    private val nsClientReceiverDelegate: NsClientReceiverDelegate,
    private val config: Config,
    private val buildHelper: BuildHelper,
    private val dataWorkerStorage: DataWorkerStorage,
    private val dateUtil: DateUtil
) : NsClient, Sync, PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(NSClientFragment::class.java.name)
        .pluginIcon(R.drawable.ic_nightscout_syncs)
        .pluginName(R.string.nsclientv3)
        .shortName(R.string.nsclientv3_shortname)
        .preferencesId(R.xml.pref_nsclientinternal)
        .description(R.string.description_ns_client_v3),
    aapsLogger, rh, injector
) {

    private val disposable = CompositeDisposable()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val listLog: MutableList<EventNSClientNewLog> = ArrayList()
    override var status = ""
    override var nsClientService: NSClientService? = null

    internal lateinit var nsAndroidClient: NSAndroidClient
//    private lateinit var nsAndroidRxClient: NSAndroidRxClient

    val isAllowed: Boolean
        get() = nsClientReceiverDelegate.allowed
    val blockingReason: String
        get() = nsClientReceiverDelegate.blockingReason

    private val maxAge = T.days(77).msecs()
    internal var lastModified: LastModified? = null // timestamp of last modification for every collection
    internal var lastFetched =
        LastModified(
            LastModified.Collections(
                dateUtil.now() - maxAge,
                dateUtil.now() - maxAge,
                dateUtil.now() - maxAge,
                dateUtil.now() - maxAge
            )
        ) // timestamp of last fetched data for every collection

    override fun onStart() {
//        context.bindService(Intent(context, NSClientService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        super.onStart()

        lastFetched = Json.decodeFromString(
            sp.getString(
                R.string.key_nsclientv2_lastmodified,
                Json.encodeToString(
                    LastModified.serializer(),
                    LastModified(LastModified.Collections(dateUtil.now() - maxAge, dateUtil.now() - maxAge, dateUtil.now() - maxAge, dateUtil.now() - maxAge))
                )
            )
        )
        lastFetched.collections.entries = max(dateUtil.now() - maxAge, lastFetched.collections.entries)
        lastFetched.collections.treatments = max(dateUtil.now() - maxAge, lastFetched.collections.treatments)
        lastFetched.collections.profile = max(dateUtil.now() - maxAge, lastFetched.collections.profile)
        lastFetched.collections.devicestatus = max(dateUtil.now() - maxAge, lastFetched.collections.devicestatus)

        nsAndroidClient = NSAndroidClientImpl(
            baseUrl = sp.getString(R.string.key_nsclientinternal_url, "").lowercase().replace("https://", ""),
            accessToken = sp.getString(R.string.key_nsclient_token, ""),
            context = context,
            logging = true
        )

        nsClientReceiverDelegate.grabReceiversState()
        disposable += rxBus
            .toObservable(EventNSClientV3Status::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventNSClientV3Status ->
                           status = event.getStatus(rh)
                           rxBus.send(EventNSClientUpdateGUI())
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        // disposable += rxBus
        //     .toObservable(EventAppExit::class.java)
        //     .observeOn(aapsSchedulers.io)
        //     .subscribe({ if (nsClientService != null) context.unbindService(mConnection) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientNewLog::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.version != NsClient.Version.V3) return@subscribe
                           addToLog(event)
                           aapsLogger.debug(LTag.NSCLIENT, event.action + " " + event.logText)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientV3Resend::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> resend(event.reason) }, fabricPrivacy::logException)
    }

    override fun onStop() {
        // context.applicationContext.unbindService(mConnection)
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            preferenceFragment.findPreference<PreferenceScreen>(rh.gs(R.string.ns_sync_options))?.isVisible = false

            preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_create_announcements_from_errors))?.isVisible = false
            preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_create_announcements_from_carbs_req))?.isVisible = false
        }
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_receive_tbr_eb))?.isVisible = buildHelper.isEngineeringMode()
    }

    override val hasWritePermission: Boolean get() = nsClientService?.hasWriteAuth ?: false
    override val connected: Boolean get() = nsClientService?.isConnected ?: false

    override fun clearLog() {
        handler.post {
            synchronized(listLog) { listLog.clear() }
            rxBus.send(EventNSClientUpdateGUI())
        }
    }

    private fun addToLog(ev: EventNSClientNewLog) {
        handler.post {
            synchronized(listLog) {
                listLog.add(ev)
                // remove the first line if log is too large
                if (listLog.size >= Constants.MAX_LOG_LINES) {
                    listLog.removeAt(0)
                }
            }
            rxBus.send(EventNSClientUpdateGUI())
        }
    }

    override fun textLog(): Spanned {
        try {
            val newTextLog = StringBuilder()
            synchronized(listLog) {
                for (log in listLog) newTextLog.append(log.toPreparedHtml())
            }
            return fromHtml(newTextLog.toString())
        } catch (e: OutOfMemoryError) {
            ToastUtils.showToastInUiThread(context, rxBus, "Out of memory!\nStop using this phone !!!", R.raw.error)
        }
        return fromHtml("")
    }

    override fun resend(reason: String) {
        nsClientService?.resend(reason)
    }

    override fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_nsclientinternal_paused, newState)
        rxBus.send(EventPreferenceChange(rh, R.string.key_nsclientinternal_paused))
    }

    override val version: NsClient.Version
        get() = NsClient.Version.V3

    override val address: String get() = sp.getString(R.string.key_nsclientinternal_url, "")

    fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        if (!isEnabled()) return
        if (!sp.getBoolean(R.string.key_ns_upload, true)) {
            aapsLogger.debug(LTag.NSCLIENT, "Upload disabled. Message dropped")
            return
        }
        nsClientService?.sendAlarmAck(
            AlarmAck().also { ack ->
                ack.level = originalAlarm.level()
                ack.group = originalAlarm.group()
                ack.silenceTime = silenceTimeInMilliseconds
            })
    }

    override fun updateLatestBgReceivedIfNewer(latestReceived: Long) {
        if (latestReceived > lastFetched.collections.entries) {
            lastFetched.collections.entries = latestReceived
            storeLastFetched()
        }
    }

    override fun updateLatestTreatmentReceivedIfNewer(latestReceived: Long) {
        lastFetched.collections.treatments = latestReceived
        storeLastFetched()
    }

    override fun resetToFullSync() {
        lastFetched = LastModified(
            LastModified.Collections(
                dateUtil.now() - maxAge,
                dateUtil.now() - maxAge,
                dateUtil.now() - maxAge,
                dateUtil.now() - maxAge
            )
        )
    }

    private fun storeLastFetched() {
        sp.putString(R.string.key_nsclientv2_lastmodified, Json.encodeToString(LastModified.serializer(), lastFetched))
    }

    fun test() {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                "NSCv3Load",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(LoadStatusWorker::class.java).build()
            )
            .then(OneTimeWorkRequest.Builder(LoadLastModificationWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadBgWorker::class.java).build())
            .then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
            .enqueue()

    }
}