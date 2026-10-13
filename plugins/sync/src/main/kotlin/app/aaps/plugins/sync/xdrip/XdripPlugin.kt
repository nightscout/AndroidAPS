package app.aaps.plugins.sync.xdrip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Spanned
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.Sync
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.generateCOBString
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.extensions.toStringShort
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclient.extensions.toJson
import app.aaps.plugins.sync.xdrip.events.EventXdripNewLog
import app.aaps.plugins.sync.xdrip.events.EventXdripUpdateGUI
import app.aaps.plugins.sync.xdrip.extensions.toXdripJson
import app.aaps.plugins.sync.xdrip.keys.XdripLongKey
import app.aaps.plugins.sync.xdrip.workers.XdripDataSyncWorker
import app.aaps.shared.impl.extensions.safeQueryBroadcastReceivers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XdripPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val aapsSchedulers: AapsSchedulers,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val rxBus: RxBus,
    private val uiInteraction: UiInteraction,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val decimalFormatter: DecimalFormatter,
    private val glucoseStatusProvider: GlucoseStatusProvider
) : XDripBroadcast, Sync, PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(XdripFragment::class.java.name)
        .pluginIcon((app.aaps.core.objects.R.drawable.ic_blooddrop_48))
        .pluginName(R.string.xdrip)
        .shortName(R.string.xdrip_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_xdrip),
    ownPreferences = listOf(XdripLongKey::class.java),
    aapsLogger, rh, preferences
) {

    @Suppress("PrivatePropertyName")
    private val XDRIP_JOB_NAME: String = this::class.java.simpleName

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val disposable = CompositeDisposable()
    private var handler: Handler? = null
    private val listLog: MutableList<EventXdripNewLog> = ArrayList()

    // Not used Sync interface members
    override val hasWritePermission: Boolean = true
    override val connected: Boolean = true
    override val status: String = ""

    override fun onStart() {
        super.onStart()
        handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
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
        eventWorker = Executors.newSingleThreadScheduledExecutor()
    }

    override fun onStop() {
        super.onStop()
        handler?.looper?.quitSafely()
        handler?.removeCallbacksAndMessages(null)
        handler = null
        eventWorker?.shutdown()
        eventWorker = null
        disposable.clear()
    }

    fun clearLog() {
        handler?.post {
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
        } catch (_: OutOfMemoryError) {
            uiInteraction.showToastAndNotification(context, "Out of memory!\nStop using this phone !!!", app.aaps.core.ui.R.raw.error)
        }
        return HtmlHelper.fromHtml("")
    }

    private fun sendStatusLine() {
        if (preferences.get(BooleanKey.XdripSendStatus)) {
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

    private var eventWorker: ScheduledExecutorService? = null
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
        scheduledEventPost = eventWorker?.schedule(task, 10, TimeUnit.SECONDS)
    }

    private fun buildStatusLine(profile: Profile): String {
        val status = StringBuilder()
        if (!loop.runningMode.isLoopRunning() && config.APS)
            status.append(rh.gs(R.string.disabled_loop)).append("\n")

        //Temp basal
        processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())?.let {
            status.append(it.toStringShort(rh)).append(" ")
        }
        //IOB
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        status.append(decimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob)).append(rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname))
        if (preferences.get(BooleanKey.XdripSendDetailedIob))
            status.append("(")
                .append(decimalFormatter.to2Decimal(bolusIob.iob))
                .append("|")
                .append(decimalFormatter.to2Decimal(basalIob.basaliob))
                .append(")")
        if (preferences.get(BooleanKey.XdripSendBgi) && glucoseStatusProvider.glucoseStatusData != null) {
            val bgi = -(bolusIob.activity + basalIob.activity) * 5 * profileUtil.fromMgdlToUnits(profile.getIsfMgdl("XdripPlugin"))
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
            ToastUtils.infoToast(context, R.string.calibration_sent)
            aapsLogger.debug(rh.gs(R.string.calibration_sent))
            true
        }
    }

    override fun sendToXdrip(collection: String, dataPair: DataSyncSelector.DataPair, progress: String) {
        scope.launch {
            when (collection) {
                "profile"      -> sendProfileStore(dataPair = dataPair, progress = progress)
                "devicestatus" -> sendDeviceStatus(dataPair = dataPair, progress = progress)
                else           -> error("Invalid collection")
            }
        }
    }

    override fun sendToXdrip(collection: String, dataPairs: List<DataSyncSelector.DataPair>, progress: String) {
        scope.launch {
            when (collection) {
                "entries"    -> sendEntries(dataPairs = dataPairs, progress = progress)
                "food"       -> sendFood(dataPairs = dataPairs, progress = progress)
                "treatments" -> sendTreatments(dataPairs = dataPairs, progress = progress)
                else         -> error("Invalid collection")
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
                is DataSyncSelector.PairRunningMode            -> dataPair.value.toJson(true, dateUtil)
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

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "xdrip_advanced") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "xdrip_settings"
            title = rh.gs(R.string.xdrip)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveIntentPreference(ctx = context, intentKey = IntentKey.XdripInfo, summary = R.string.xdrip_local_broadcasts_summary, title = R.string.xdrip_local_broadcasts_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.XdripSendStatus, title = R.string.xdrip_send_status_title))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "xdrip_advanced"
                title = rh.gs(R.string.xdrip_status_settings)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.XdripSendDetailedIob, summary = R.string.xdrip_status_detailed_iob_summary, title = R.string.xdrip_status_detailed_iob_title))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.XdripSendBgi, summary = R.string.xdrip_status_show_bgi_summary, title = R.string.xdrip_status_show_bgi_title))
            })
        }
    }
}