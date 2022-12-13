package info.nightscout.plugins.sync.nsclientV3

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.text.Spanned
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.nsclient.NSAlarm
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.interfaces.sync.Sync
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsShared.NSClientFragment
import info.nightscout.plugins.sync.nsShared.events.EventNSClientResend
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGUI
import info.nightscout.plugins.sync.nsclient.NsClientReceiverDelegate
import info.nightscout.plugins.sync.nsclientV3.extensions.toNSBolus
import info.nightscout.plugins.sync.nsclientV3.workers.LoadBgWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadLastModificationWorker
import info.nightscout.plugins.sync.nsclientV3.workers.LoadStatusWorker
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventSWSyncStatus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.sdk.NSAndroidClientImpl
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.runBlocking
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
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction
) : NsClient, Sync, PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(NSClientFragment::class.java.name)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_nightscout_syncs)
        .pluginName(R.string.ns_client_v3)
        .shortName(R.string.ns_client_v3_short_name)
        .preferencesId(R.xml.pref_ns_client)
        .description(R.string.description_ns_client_v3),
    aapsLogger, rh, injector
) {

    companion object {

        val JOB_NAME: String = this::class.java.simpleName
        val REFRESH_INTERVAL = T.mins(5).msecs()
    }

    private val disposable = CompositeDisposable()
    private lateinit var runLoop: Runnable
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val listLog: MutableList<EventNSClientNewLog> = ArrayList()
    override val status
        get() =
            when {
                sp.getBoolean(R.string.key_ns_client_paused, false)          -> rh.gs(info.nightscout.core.ui.R.string.paused)
                isAllowed.not()                                              -> blockingReason
                nsAndroidClient.lastStatus == null                           -> rh.gs(R.string.not_connected)
                workIsRunning(arrayOf(JOB_NAME))                             -> rh.gs(R.string.working)
                nsAndroidClient.lastStatus?.apiPermissions?.isFull() == true -> rh.gs(info.nightscout.shared.R.string.connected)
                nsAndroidClient.lastStatus?.apiPermissions?.isRead() == true -> rh.gs(R.string.read_only)
                else                                                         -> rh.gs(info.nightscout.core.ui.R.string.unknown)
            }

    internal lateinit var nsAndroidClient: NSAndroidClient
//    private lateinit var nsAndroidRxClient: NSAndroidRxClient

    val isAllowed get() = nsClientReceiverDelegate.allowed
    val blockingReason get() = nsClientReceiverDelegate.blockingReason

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
                R.string.key_ns_client_v3_last_modified,
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

        setClient()

        nsClientReceiverDelegate.grabReceiversState()
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev ->
                           nsClientReceiverDelegate.onStatusEvent(ev)
                           setClient()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev ->
                           nsClientReceiverDelegate.onStatusEvent(ev)
                           if (ev.isChanged(rh.gs(R.string.key_ns_client_token)) || ev.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_nsclientinternal_url)))
                               setClient()
                       }, fabricPrivacy::logException)
        // disposable += rxBus
        //     .toObservable(EventAppExit::class.java)
        //     .observeOn(aapsSchedulers.io)
        //     .subscribe({ if (nsClientService != null) context.unbindService(mConnection) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientNewLog::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           addToLog(event)
                           aapsLogger.debug(LTag.NSCLIENT, event.action + " " + event.logText)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientResend::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> resend(event.reason) }, fabricPrivacy::logException)

        runLoop = Runnable {
            executeLoop()
            handler.postDelayed(runLoop, REFRESH_INTERVAL)
        }
        handler.postDelayed(runLoop, REFRESH_INTERVAL)
        executeLoop()
    }

    override fun onStop() {
        // context.applicationContext.unbindService(mConnection)
        handler.removeCallbacksAndMessages(null)
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            preferenceFragment.findPreference<PreferenceScreen>(rh.gs(R.string.ns_sync_options))?.isVisible = false

            preferenceFragment.findPreference<SwitchPreference>(rh.gs(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_errors))?.isVisible = false
            preferenceFragment.findPreference<SwitchPreference>(rh.gs(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_carbs_req))?.isVisible = false
        }
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_receive_tbr_eb))?.isVisible = config.isEngineeringMode()
    }

    override val hasWritePermission: Boolean get() = nsAndroidClient.lastStatus?.apiPermissions?.isFull() ?: false
    override val connected: Boolean get() = nsAndroidClient.lastStatus != null

    override fun clearLog() {
        handler.post {
            synchronized(listLog) { listLog.clear() }
            rxBus.send(EventNSClientUpdateGUI())
        }
    }

    private fun setClient() {
        nsAndroidClient = NSAndroidClientImpl(
            baseUrl = sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "").lowercase().replace("https://", ""),
            accessToken = sp.getString(R.string.key_ns_client_token, ""),
            context = context,
            logging = true
        )
        rxBus.send(EventSWSyncStatus(status))
    }

    private fun addToLog(ev: EventNSClientNewLog) {
        synchronized(listLog) {
            listLog.add(ev)
            // remove the first line if log is too large
            if (listLog.size >= Constants.MAX_LOG_LINES) {
                listLog.removeAt(0)
            }
        }
        rxBus.send(EventNSClientUpdateGUI())
    }

    override fun textLog(): Spanned {
        try {
            val newTextLog = StringBuilder()
            synchronized(listLog) {
                for (log in listLog) newTextLog.append(log.toPreparedHtml())
            }
            return HtmlHelper.fromHtml(newTextLog.toString())
        } catch (e: OutOfMemoryError) {
            uiInteraction.showToastAndNotification(context, "Out of memory!\nStop using this phone !!!", info.nightscout.core.ui.R.raw.error)
        }
        return HtmlHelper.fromHtml("")
    }

    override fun resend(reason: String) {
//        nsClientService?.resend(reason)
    }

    override fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_ns_client_paused, newState)
        rxBus.send(EventPreferenceChange(rh.gs(R.string.key_ns_client_paused)))
    }

    override val version: NsClient.Version get() = NsClient.Version.V3

    override val address: String get() = sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "")

    override fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        if (!isEnabled()) return
        if (!sp.getBoolean(R.string.key_ns_upload, true)) {
            aapsLogger.debug(LTag.NSCLIENT, "Upload disabled. Message dropped")
            return
        }
        // nsClientService?.sendAlarmAck(
        //     AlarmAck().also { ack ->
        //         ack.level = originalAlarm.level()
        //         ack.group = originalAlarm.group()
        //         ack.silenceTime = silenceTimeInMilliseconds
        //     })
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
        storeLastFetched()
    }

    override fun dbAdd(collection: String, dataPair: DataSyncSelector.DataPair, progress: String) {
        when (dataPair) {
            is DataSyncSelector.PairBolus -> dataPair.value.toNSBolus()
            // is DataSyncSelector.PairCarbs                  -> dataPair.value.toJson(false, dateUtil)
            // is DataSyncSelector.PairBolusCalculatorResult  -> dataPair.value.toJson(false, dateUtil, profileFunction)
            // is DataSyncSelector.PairTemporaryTarget        -> dataPair.value.toJson(false, profileFunction.getUnits(), dateUtil)
            // is DataSyncSelector.PairFood                   -> dataPair.value.toJson(false)
            // is DataSyncSelector.PairGlucoseValue           -> dataPair.value.toJson(false, dateUtil)
            // is DataSyncSelector.PairTherapyEvent           -> dataPair.value.toJson(false, dateUtil)
            // is DataSyncSelector.PairTemporaryBasal         -> dataPair.value.toJson(false, profileFunction.getProfile(dataPair.value.timestamp), dateUtil)
            // is DataSyncSelector.PairExtendedBolus          -> dataPair.value.toJson(false, profileFunction.getProfile(dataPair.value.timestamp), dateUtil)
            // is DataSyncSelector.PairProfileSwitch          -> dataPair.value.toJson(false, dateUtil)
            // is DataSyncSelector.PairEffectiveProfileSwitch -> dataPair.value.toJson(false, dateUtil)
            // is DataSyncSelector.PairOfflineEvent           -> dataPair.value.toJson(false, dateUtil)
            else                          -> null
        }?.let { data ->
            runBlocking {
                if (collection == "treatments") {
                    val result = nsAndroidClient.createTreatment(data)
                }
            }
        }
    }

    override fun dbUpdate(collection: String, dataPair: DataSyncSelector.DataPair, progress: String) {
        TODO("Not yet implemented")
    }

    private fun storeLastFetched() {
        sp.putString(R.string.key_ns_client_v3_last_modified, Json.encodeToString(LastModified.serializer(), lastFetched))
    }

    fun test() {
        executeLoop()
    }

    fun scheduleNewExecution() {
        val toTime = lastFetched.collections.entries + T.mins(6).plus(T.secs(0)).msecs()
        if (toTime > dateUtil.now()) {
            handler.postDelayed({ executeLoop() }, toTime - dateUtil.now())
            rxBus.send(EventNSClientNewLog("NEXT", dateUtil.dateAndTimeAndSecondsString(toTime)))
        }
    }

    private fun executeLoop() {
        if (sp.getBoolean(R.string.key_ns_client_paused, false)) {
            rxBus.send(EventNSClientNewLog("NSCLIENT", "paused"))
            return
        }
        if (!isAllowed) {
            rxBus.send(EventNSClientNewLog("NSCLIENT", blockingReason))
            return
        }
        if (workIsRunning(arrayOf(JOB_NAME)))
            rxBus.send(EventNSClientNewLog("RUN", "Already running"))
        else {
            rxBus.send(EventNSClientNewLog("RUN", "Starting next round"))
            WorkManager.getInstance(context)
                .beginUniqueWork(
                    "NSCv3Load",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequest.Builder(LoadStatusWorker::class.java).build()
                )
                .then(OneTimeWorkRequest.Builder(LoadLastModificationWorker::class.java).build())
                .then(OneTimeWorkRequest.Builder(LoadBgWorker::class.java).build())
                // LoadTreatmentsWorker is enqueued after BG finish
                //.then(OneTimeWorkRequest.Builder(LoadTreatmentsWorker::class.java).build())
                .enqueue()
        }
    }

    private fun workIsRunning(workNames: Array<String>): Boolean {
        for (workName in workNames)
            for (workInfo in WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get())
                if (workInfo.state == WorkInfo.State.BLOCKED || workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING)
                    return true
        return false
    }
}