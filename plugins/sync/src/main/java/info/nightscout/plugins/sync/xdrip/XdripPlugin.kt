package info.nightscout.plugins.sync.xdrip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Spanned
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.toStringShort
import info.nightscout.core.iob.generateCOBString
import info.nightscout.core.iob.round
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.receivers.Intents
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.sync.Sync
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsclient.extensions.toJson
import info.nightscout.plugins.sync.xdrip.events.EventXdripUpdateGUI
import info.nightscout.plugins.sync.xdrip.extensions.toXdripJson
import info.nightscout.plugins.sync.xdrip.workers.XdripDataSyncWorker
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventAppInitialized
import info.nightscout.rx.events.EventAutosensCalculationFinished
import info.nightscout.rx.events.EventNewBG
import info.nightscout.rx.events.EventNewHistoryData
import info.nightscout.rx.events.EventXdripNewLog
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.safeQueryBroadcastReceivers
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val rxBus: RxBus,
    private val uiInteraction: UiInteraction,
    private val dateUtil: DateUtil,
    aapsLogger: AAPSLogger,
    private val decimalFormatter: DecimalFormatter
) : XDripBroadcast, Sync, PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(XdripFragment::class.java.name)
        .pluginIcon((info.nightscout.core.main.R.drawable.ic_blooddrop_48))
        .pluginName(R.string.xdrip)
        .shortName(R.string.xdrip_shortname)
        .preferencesId(R.xml.pref_xdrip)
        .description(R.string.description_xdrip),
    aapsLogger, rh, injector
) {

    @Suppress("PrivatePropertyName")
    private val XDRIP_JOB_NAME: String = this::class.java.simpleName

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val disposable = CompositeDisposable()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val listLog: MutableList<EventXdripNewLog> = ArrayList()
    private var lastLoopStatus = false

    // Not used Sync interface members
    override val hasWritePermission: Boolean = true
    override val connected: Boolean = true
    override val status: String = ""

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ WorkManager.getInstance(context).cancelUniqueWork(XDRIP_JOB_NAME) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventXdripNewLog::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           addToLog(event)
                           aapsLogger.debug(LTag.XDRIP, event.action + " " + event.logText)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           sendStatusLine()
                           delayAndScheduleExecution("NEW_BG")
                       }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventNewHistoryData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           sendStatusLine()
                           delayAndScheduleExecution("NEW_DATA")
                       }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatusLine() }, fabricPrivacy::logException)
        disposable += rxBus.toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendStatusLine() }, fabricPrivacy::logException)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        disposable.clear()
    }

    fun clearLog() {
        handler.post {
            synchronized(listLog) { listLog.clear() }
            rxBus.send(EventXdripUpdateGUI())
        }
    }

    private fun addToLog(ev: EventXdripNewLog) {
        synchronized(listLog) {
            listLog.add(ev)
            // remove the first line if log is too large
            if (listLog.size >= Constants.MAX_LOG_LINES) {
                listLog.removeAt(0)
            }
        }
        rxBus.send(EventXdripUpdateGUI())
    }

    fun textLog(): Spanned {
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

    private fun sendStatusLine() {
        if (sp.getBoolean(R.string.key_xdrip_send_status, false)) {
            val status = profileFunction.getProfile()?.let { buildStatusLine(it) } ?: ""
            context.sendBroadcast(
                Intent(Intents.ACTION_NEW_EXTERNAL_STATUSLINE).also {
                    it.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    it.putExtras(Bundle().apply { putString(Intents.EXTRA_STATUSLINE, status) })
                }
            )
        }
    }

    private fun executeLoop(origin: String) {
        if (workIsRunning(arrayOf(XDRIP_JOB_NAME))) {
            rxBus.send(EventXdripNewLog("RUN", "Already running $origin"))
            return
        }
        rxBus.send(EventXdripNewLog("RUN", "Starting next round $origin"))
        WorkManager.getInstance(context)
            .beginUniqueWork(
                XDRIP_JOB_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(XdripDataSyncWorker::class.java).build()
            )
            .enqueue()
    }

    private fun workIsRunning(workNames: Array<String>): Boolean {
        for (workName in workNames)
            for (workInfo in WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get())
                if (workInfo.state == WorkInfo.State.BLOCKED || workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING)
                    return true
        return false
    }

    private val eventWorker = Executors.newSingleThreadScheduledExecutor()
    private var scheduledEventPost: ScheduledFuture<*>? = null
    private fun delayAndScheduleExecution(origin: String) {
        class PostRunnable : Runnable {

            override fun run() {
                scheduledEventPost = null
                executeLoop(origin)
            }
        }
        // cancel waiting task to prevent sending multiple posts
        scheduledEventPost?.cancel(false)
        val task: Runnable = PostRunnable()
        scheduledEventPost = eventWorker.schedule(task, 10, TimeUnit.SECONDS)
    }

    private fun buildStatusLine(profile: Profile): String {
        val status = StringBuilder()
        @Suppress("LiftReturnOrAssignment")
        if (!loop.isEnabled()) {
            status.append(rh.gs(R.string.disabled_loop)).append("\n")
            lastLoopStatus = false
        } else lastLoopStatus = true

        //Temp basal
        iobCobCalculator.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())?.let {
            status.append(it.toStringShort(decimalFormatter)).append(" ")
        }
        //IOB
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        status.append(decimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob)).append(rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname))
        if (sp.getBoolean(R.string.key_xdrip_status_detailed_iob, true))
            status.append("(")
                .append(decimalFormatter.to2Decimal(bolusIob.iob))
                .append("|")
                .append(decimalFormatter.to2Decimal(basalIob.basaliob))
                .append(")")
        if (sp.getBoolean(R.string.key_xdrip_status_show_bgi, true)) {
            val bgi = -(bolusIob.activity + basalIob.activity) * 5 * profileUtil.fromMgdlToUnits(profile.getIsfMgdl())
            status.append(" ")
                .append(if (bgi >= 0) "+" else "")
                .append(decimalFormatter.to2Decimal(bgi))
        }
        // COB
        status.append(" ").append(iobCobCalculator.getCobInfo("StatusLinePlugin").generateCOBString(decimalFormatter))
        return status.toString()
    }

    override fun sendCalibration(bg: Double): Boolean {
        val bundle = Bundle()
        bundle.putDouble("glucose_number", bg)
        bundle.putString("units", if (profileFunction.getUnits() == GlucoseUnit.MGDL) "mgdl" else "mmol")
        bundle.putLong("timestamp", System.currentTimeMillis())
        val intent = Intent(Intents.ACTION_REMOTE_CALIBRATION)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        context.sendBroadcast(intent)
        val q = context.packageManager.safeQueryBroadcastReceivers(intent, 0)
        return if (q.isEmpty()) {
            ToastUtils.errorToast(context, R.string.xdrip_not_installed)
            aapsLogger.debug(rh.gs(R.string.xdrip_not_installed))
            false
        } else {
            ToastUtils.errorToast(context, R.string.calibration_sent)
            aapsLogger.debug(rh.gs(R.string.calibration_sent))
            true
        }
    }

    /*
        // sent in 640G mode
        // com.eveningoutpost.dexdrip.NSEmulatorReceiver
        override fun sendIn640gMode(glucoseValue: GlucoseValue) {
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_dexcomg5_xdripupload, false)) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                try {
                    val entriesBody = JSONArray()
                    val json = JSONObject()
                    json.put("sgv", glucoseValue.value)
                    json.put("direction", glucoseValue.trendArrow.text)
                    json.put("device", "G5")
                    json.put("type", "sgv")
                    json.put("date", glucoseValue.timestamp)
                    json.put("dateString", format.format(glucoseValue.timestamp))
                    entriesBody.put(json)
                    val bundle = Bundle()
                    bundle.putString("action", "add")
                    bundle.putString("collection", "entries")
                    bundle.putString("data", entriesBody.toString())
                    val intent = Intent(Intents.XDRIP_PLUS_NS_EMULATOR)
                    intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    context.sendBroadcast(intent)
                    val receivers = context.packageManager.safeQueryBroadcastReceivers(intent, 0)
                    if (receivers.isEmpty()) {
                        //NSUpload.log.debug("No xDrip receivers found. ")
                        aapsLogger.debug(LTag.BGSOURCE, "No xDrip receivers found.")
                    } else {
                        aapsLogger.debug(LTag.BGSOURCE, "${receivers.size} xDrip receivers")
                    }
                } catch (e: JSONException) {
                    aapsLogger.error(LTag.BGSOURCE, "Unhandled exception", e)
                }
            }
        }
    */

    override fun sendToXdrip(collection: String, dataPair: DataSyncSelector.DataPair, progress: String) {
        scope.launch {
            when (collection) {
                "profile"      -> sendProfileStore(dataPair = dataPair, progress = progress)
                "devicestatus" -> sendDeviceStatus(dataPair = dataPair, progress = progress)
                else           -> throw IllegalStateException()
            }
        }
    }

    override fun sendToXdrip(collection: String, dataPairs: List<DataSyncSelector.DataPair>, progress: String) {
        scope.launch {
            when (collection) {
                "entries"    -> sendEntries(dataPairs = dataPairs, progress = progress)
                "food"       -> sendFood(dataPairs = dataPairs, progress = progress)
                "treatments" -> sendTreatments(dataPairs = dataPairs, progress = progress)
                else         -> throw IllegalStateException()
            }
        }
    }

    private fun sendProfileStore(dataPair: DataSyncSelector.DataPair, progress: String) {
        val data = (dataPair as DataSyncSelector.PairProfileStore).value
        rxBus.send(EventXdripNewLog("SENDING", "Sent 1 PROFILE ($progress)"))
        broadcast(
            Intent(Intents.ACTION_NEW_PROFILE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(Bundle().apply { putString("profile", data.toString()) })
        )
    }

    private fun sendDeviceStatus(dataPair: DataSyncSelector.DataPair, progress: String) {
        val data = (dataPair as DataSyncSelector.PairDeviceStatus).value.toJson(dateUtil)
        rxBus.send(EventXdripNewLog("SENDING", "Sent 1 DEVICESTATUS ($progress)"))
        broadcast(
            Intent(Intents.ACTION_NEW_DEVICE_STATUS)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(Bundle().apply { putString("devicestatus", data.toString()) })
        )
    }

    private fun sendEntries(dataPairs: List<DataSyncSelector.DataPair>, progress: String) {
        val array = JSONArray()
        for (dataPair in dataPairs)
            (dataPair as DataSyncSelector.PairGlucoseValue?)?.value?.toXdripJson()?.also { gv -> array.put(gv) }
        rxBus.send(EventXdripNewLog("SENDING", "Sent ${array.length()} BGs ($progress)"))
        broadcast(
            Intent(Intents.ACTION_NEW_SGV)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(Bundle().apply { putString("sgvs", array.toString()) })
        )
    }

    private fun sendFood(dataPairs: List<DataSyncSelector.DataPair>, progress: String) {
        val array = JSONArray()
        for (dataPair in dataPairs) {
            val data = (dataPair as DataSyncSelector.PairFood).value.toJson(true)
            array.put(data)
        }
        rxBus.send(EventXdripNewLog("SENDING", "Sent ${array.length()} FOODs ($progress)"))
        broadcast(
            Intent(Intents.ACTION_NEW_FOOD)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(Bundle().apply { putString("foods", array.toString()) })
        )
    }

    private fun sendTreatments(dataPairs: List<DataSyncSelector.DataPair>, progress: String) {
        val array = JSONArray()
        for (dataPair in dataPairs) {
            when (dataPair) {
                is DataSyncSelector.PairBolus                  -> dataPair.value.toJson(true, dateUtil)
                is DataSyncSelector.PairCarbs                  -> dataPair.value.toJson(true, dateUtil)
                is DataSyncSelector.PairBolusCalculatorResult  -> dataPair.value.toJson(true, dateUtil, profileUtil)
                is DataSyncSelector.PairTemporaryTarget        -> dataPair.value.toJson(true, dateUtil, profileUtil)
                is DataSyncSelector.PairTherapyEvent           -> dataPair.value.toJson(true, dateUtil)

                is DataSyncSelector.PairTemporaryBasal         -> {
                    val profile = profileFunction.getProfile(dataPair.value.timestamp) ?: return
                    dataPair.value.toJson(true, profile, dateUtil)
                }

                is DataSyncSelector.PairExtendedBolus          -> {
                    val profile = profileFunction.getProfile(dataPair.value.timestamp) ?: return
                    dataPair.value.toJson(true, profile, dateUtil)
                }

                is DataSyncSelector.PairProfileSwitch          -> dataPair.value.toJson(true, dateUtil, decimalFormatter)
                is DataSyncSelector.PairEffectiveProfileSwitch -> dataPair.value.toJson(true, dateUtil)
                is DataSyncSelector.PairOfflineEvent           -> dataPair.value.toJson(true, dateUtil)
                else                                           -> null
            }?.let {
                array.put(it)
            }
        }
        rxBus.send(EventXdripNewLog("SENDING", "Sent ${array.length()} TRs ($progress)"))
        broadcast(
            Intent(Intents.ACTION_NEW_FOOD)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(Bundle().apply { putString("treatments", array.toString()) })
        )
    }

    private fun broadcast(intent: Intent) {
        context.packageManager.safeQueryBroadcastReceivers(intent, 0).forEach { resolveInfo ->
            resolveInfo.activityInfo.packageName?.let {
                intent.setPackage(it)
                context.sendBroadcast(intent)
                aapsLogger.debug(LTag.XDRIP, "Sending broadcast " + intent.action + " to: " + it)
                rxBus.send(EventXdripNewLog("RECIPIENT", it))
                rxBus.send(EventXdripUpdateGUI())
                Thread.sleep(100)
            }
        }
    }
}