package info.nightscout.androidaps.plugins.general.wear.wearintegration

import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import dagger.android.AndroidInjection
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.toStringShort
import info.nightscout.androidaps.extensions.valueToUnitsString
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.interfaces.Profile.Companion.fromMgdlToUnits
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.GlucoseValueDataPoint
import info.nightscout.androidaps.plugins.general.wear.WearPlugin
import info.nightscout.androidaps.plugins.general.wear.events.EventWearConfirmAction
import info.nightscout.androidaps.plugins.general.wear.events.EventWearInitiateAction
import info.nightscout.androidaps.plugins.general.wear.events.EventWearUpdateGui
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DecimalFormatter.to0Decimal
import info.nightscout.androidaps.utils.DecimalFormatter.to1Decimal
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.TrendCalculator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.wizard.QuickWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.WearConstants
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.inject.Inject
import kotlin.math.abs

class WatchUpdaterService : WearableListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var loop: Loop
    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var config: Config
    @Inject lateinit var nsDeviceStatus: NSDeviceStatus
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var wearConstants: WearConstants

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    //private val nodeClient by lazy { Wearable.getNodeClient(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val disposable = CompositeDisposable()

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        aapsLogger.debug(LTag.WEAR, "onCreate")
        handler.post { updateTranscriptionCapability() }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        super.onCapabilityChanged(p0)
        handler.post { updateTranscriptionCapability() }
        aapsLogger.debug(LTag.WEAR, "onCapabilityChanged:  ${p0.name} ${p0.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        scope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        aapsLogger.debug(LTag.WEAR, "onStartCommand ${intent?.action}")
        if (wearPlugin.isEnabled()) {
            handler.post {
                when (intent?.action) {
                    ACTION_RESEND                           -> resendData()
                    ACTION_OPEN_SETTINGS                    -> sendMessage(wearConstants.M_W_OPEN_SETTINGS, byteArrayOf())
                    ACTION_SEND_STATUS                      -> sendStatus()
                    ACTION_SEND_BASALS                      -> sendBasals()
                    ACTION_SEND_BOLUS_PROGRESS              -> sendBolusProgress(
                        intent.getIntExtra("progresspercent", 0),
                        intent.getStringExtra("progressstatus")
                    )
                    ACTION_SEND_ACTION_CONFIRMATION_REQUEST -> sendActionConfirmationRequest(
                        intent.getStringExtra("title"),
                        intent.getStringExtra("message"),
                        intent.getStringExtra("actionstring")
                    )
                    ACTION_SEND_CHANGE_CONFIRMATION_REQUEST -> sendChangeConfirmationRequest(
                        intent.getStringExtra("title"),
                        intent.getStringExtra("message"),
                        intent.getStringExtra("actionstring")
                    )
                    ACTION_CANCEL_NOTIFICATION              -> sendCancelNotificationRequest(intent.getStringExtra("actionstring"))

                    null                                    -> {}

                    else                                    -> sendData()
                }
            }
        }
        return START_STICKY
    }

    @Suppress("ControlFlowWithEmptyBody", "UNUSED_EXPRESSION")
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        //aapsLogger.debug(LTag.WEAR, "onDataChanged")

        if (wearPlugin.isEnabled()) {
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val path = event.dataItem.uri.path

                    aapsLogger.debug(LTag.WEAR, "onDataChanged: Path: $path, EventDataItem=${event.dataItem}")
                    try {
                        when (path) {
                        }
                    } catch (exception: Exception) {
                        aapsLogger.error(LTag.WEAR, "Message failed", exception)
                    }
                }
            }
        }
        super.onDataChanged(dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        aapsLogger.debug(LTag.WEAR, "onMessageReceived: $messageEvent")

        if (wearPlugin.isEnabled()) {
            when (messageEvent.path) {
                wearConstants.W_M_RESEND_DATA     -> resendData()
                wearConstants.W_M_CANCEL_BOLUS    -> activePlugin.activePump.stopBolusDelivering()

                wearConstants.W_M_INITIATE_ACTION ->
                    String(messageEvent.data).also { actionstring ->
                        aapsLogger.debug(LTag.WEAR, "Initiate action: $actionstring")
                        rxBus.send(EventWearInitiateAction(actionstring))
                    }

                wearConstants.W_M_CONFIRM_ACTION  ->
                    String(messageEvent.data).also { actionstring ->
                        aapsLogger.debug(LTag.WEAR, "Wear confirm action: $actionstring")
                        rxBus.send(EventWearConfirmAction(actionstring))
                    }

                wearConstants.W_M_PONG            -> aapsLogger.debug(LTag.WEAR, "Pong response from ${messageEvent.sourceNodeId}")
            }
        }
    }

    private var transcriptionNodeId: String? = null

    private fun updateTranscriptionCapability() {
        val capabilityInfo: CapabilityInfo = Tasks.await(
            capabilityClient.getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        )
        aapsLogger.debug(LTag.WEAR, "Nodes: ${capabilityInfo.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
        val bestNode = pickBestNodeId(capabilityInfo.nodes)
        transcriptionNodeId = bestNode?.id
        wearPlugin.connectedDevice = bestNode?.displayName ?: "---"
        rxBus.send(EventWearUpdateGui())
        aapsLogger.debug(LTag.WEAR, "Selected node: ${bestNode?.displayName} $transcriptionNodeId")
        sendMessage(wearConstants.M_W_PING, byteArrayOf())
    }

    // Find a nearby node or pick one arbitrarily
    private fun pickBestNodeId(nodes: Set<Node>): Node? =
        nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()

    private fun sendData(path: String, vararg params: DataMap) {
        if (wearPlugin.isEnabled()) {
            scope.launch {
                try {
                    for (dm in params) {
                        val request = PutDataMapRequest.create(path).apply {
                            dataMap.putAll(dm)
                        }
                            .asPutDataRequest()
                            .setUrgent()

                        val result = dataClient.putDataItem(request).await()
                        aapsLogger.debug(LTag.WEAR, "sendData: ${result.uri} ${params.joinToString()}")
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (exception: Exception) {
                    aapsLogger.error(LTag.WEAR, "DataItem failed: $exception")
                }
            }
        }
    }

    private fun sendMessage(path: String, data: ByteArray) {
        if (wearPlugin.isEnabled()) {
            aapsLogger.debug(LTag.WEAR, "sendMessage:  $path")
            transcriptionNodeId?.also { nodeId ->
                messageClient
                    .sendMessage(nodeId, path, data).apply {
                        addOnSuccessListener { }
                        addOnFailureListener {
                            aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure")
                        }
                    }
            }
        }
    }

    private fun sendData() {
        val lastBG = iobCobCalculator.ads.lastBg() ?: return
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        aapsLogger.debug(LTag.WEAR, "Sending bg data to wear")
        sendData(
            wearConstants.M_W_DATA,
            dataMapSingleBG(lastBG, glucoseStatus)
        )
    }

    private fun resendData() {
        sendPreferences()
        sendQuickWizard()
        val startTime = System.currentTimeMillis() - (60000 * 60 * 5.5).toLong()
        val lastBg = iobCobCalculator.ads.lastBg() ?: return
        val graphBgs = repository.compatGetBgReadingsDataFromTime(startTime, true).blockingGet()
        val glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true)
        if (graphBgs.isNotEmpty()) {
            val entries = dataMapSingleBG(lastBg, glucoseStatus)
            val dataMaps = ArrayList<DataMap>(graphBgs.size)
            for (bg in graphBgs) {
                val dataMap: DataMap = dataMapSingleBG(bg, glucoseStatus)
                dataMaps.add(dataMap)
            }
            entries.putDataMapArrayList("entries", dataMaps)
            aapsLogger.debug(LTag.WEAR, "Sending graph bg data to wear")
            sendData(
                wearConstants.M_W_DATA,
                entries
            )
        }
        sendBasals()
        sendStatus()
    }

    private fun sendBasals() {
        val now = System.currentTimeMillis()
        val startTimeWindow = now - (60000 * 60 * 5.5).toLong()
        val basals = java.util.ArrayList<DataMap>()
        val temps = java.util.ArrayList<DataMap>()
        val boluses = java.util.ArrayList<DataMap>()
        val predictions = java.util.ArrayList<DataMap>()
        val profile = profileFunction.getProfile() ?: return
        var beginBasalSegmentTime = startTimeWindow
        var runningTime = startTimeWindow
        var beginBasalValue = profile.getBasal(beginBasalSegmentTime)
        var endBasalValue = beginBasalValue
        var tb1 = iobCobCalculator.getTempBasalIncludingConvertedExtended(runningTime)
        var tb2: TemporaryBasal?
        var tbBefore = beginBasalValue
        var tbAmount = beginBasalValue
        var tbStart = runningTime
        if (tb1 != null) {
            val profileTB = profileFunction.getProfile(runningTime)
            if (profileTB != null) {
                tbAmount = tb1.convertedToAbsolute(runningTime, profileTB)
                tbStart = runningTime
            }
        }
        while (runningTime < now) {
            val profileTB = profileFunction.getProfile(runningTime) ?: return
            //basal rate
            endBasalValue = profile.getBasal(runningTime)
            if (endBasalValue != beginBasalValue) {
                //push the segment we recently left
                basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue))

                //begin new Basal segment
                beginBasalSegmentTime = runningTime
                beginBasalValue = endBasalValue
            }

            //temps
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(runningTime)
            if (tb1 == null && tb2 == null) {
                //no temp stays no temp
            } else if (tb1 != null && tb2 == null) {
                //temp is over -> push it
                temps.add(tempMap(tbStart, tbBefore, runningTime, endBasalValue, tbAmount))
                tb1 = null
            } else if (tb1 == null && tb2 != null) {
                //temp begins
                tb1 = tb2
                tbStart = runningTime
                tbBefore = endBasalValue
                tbAmount = tb1.convertedToAbsolute(runningTime, profileTB)
            } else if (tb1 != null && tb2 != null) {
                val currentAmount = tb2.convertedToAbsolute(runningTime, profileTB)
                if (currentAmount != tbAmount) {
                    temps.add(tempMap(tbStart, tbBefore, runningTime, currentAmount, tbAmount))
                    tbStart = runningTime
                    tbBefore = tbAmount
                    tbAmount = currentAmount
                    tb1 = tb2
                }
            }
            runningTime += (5 * 60 * 1000).toLong()
        }
        if (beginBasalSegmentTime != runningTime) {
            //push the remaining segment
            basals.add(basalMap(beginBasalSegmentTime, runningTime, beginBasalValue))
        }
        if (tb1 != null) {
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(now) //use "now" to express current situation
            if (tb2 == null) {
                //express the cancelled temp by painting it down one minute early
                temps.add(tempMap(tbStart, tbBefore, now - 60 * 1000, endBasalValue, tbAmount))
            } else {
                //express currently running temp by painting it a bit into the future
                val profileNow = profileFunction.getProfile(now)
                val currentAmount = tb2.convertedToAbsolute(now, profileNow!!)
                if (currentAmount != tbAmount) {
                    temps.add(tempMap(tbStart, tbBefore, now, tbAmount, tbAmount))
                    temps.add(tempMap(now, tbAmount, runningTime + 5 * 60 * 1000, currentAmount, currentAmount))
                } else {
                    temps.add(tempMap(tbStart, tbBefore, runningTime + 5 * 60 * 1000, tbAmount, tbAmount))
                }
            }
        } else {
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(now) //use "now" to express current situation
            if (tb2 != null) {
                //onset at the end
                val profileTB = profileFunction.getProfile(runningTime)
                val currentAmount = tb2.convertedToAbsolute(runningTime, profileTB!!)
                temps.add(tempMap(now - 60 * 1000, endBasalValue, runningTime + 5 * 60 * 1000, currentAmount, currentAmount))
            }
        }
        repository.getBolusesIncludingInvalidFromTime(startTimeWindow, true).blockingGet()
            .stream()
            .filter { (_, _, _, _, _, _, _, _, _, type) -> type !== Bolus.Type.PRIMING }
            .forEach { (_, _, _, isValid, _, _, timestamp, _, amount, type) -> boluses.add(treatmentMap(timestamp, amount, 0.0, type === Bolus.Type.SMB, isValid)) }
        repository.getCarbsDataFromTimeExpanded(startTimeWindow, true).blockingGet()
            .forEach(Consumer { (_, _, _, isValid, _, _, timestamp, _, _, amount) -> boluses.add(treatmentMap(timestamp, 0.0, amount, false, isValid)) })
        val finalLastRun = loop.lastRun
        if (sp.getBoolean("wear_predictions", true) && finalLastRun?.request?.hasPredictions == true && finalLastRun.constraintsProcessed != null) {
            val predArray = finalLastRun.constraintsProcessed!!.predictions
                .stream().map { bg: GlucoseValue -> GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, rh) }
                .collect(Collectors.toList())
            if (predArray.isNotEmpty())
                for (bg in predArray) if (bg.data.value > 39) predictions.add(predictionMap(bg.data.timestamp, bg.data.value, bg.color(null)))
        }
        aapsLogger.debug(LTag.WEAR, "Sending basal data to wear")
        sendData(
            wearConstants.M_W_BASAL,
            DataMap().apply {
                putDataMapArrayList("basals", basals)
                putDataMapArrayList("temps", temps)
                putDataMapArrayList("boluses", boluses)
                putDataMapArrayList("predictions", predictions)
            })
    }

    private fun deltaString(deltaMGDL: Double, deltaMMOL: Double, units: GlucoseUnit): String {
        val detailed = sp.getBoolean(R.string.key_wear_detailed_delta, false)
        var deltaString = if (deltaMGDL >= 0) "+" else "-"
        deltaString += if (units == GlucoseUnit.MGDL) {
            if (detailed) to1Decimal(abs(deltaMGDL)) else to0Decimal(abs(deltaMGDL))
        } else {
            if (detailed) to2Decimal(abs(deltaMMOL)) else to1Decimal(abs(deltaMMOL))
        }
        return deltaString
    }

    private fun dataMapSingleBG(lastBG: GlucoseValue, glucoseStatus: GlucoseStatus?): DataMap {
        val units = profileFunction.getUnits()
        val lowLine = Profile.toMgdl(defaultValueHelper.determineLowLine(), units)
        val highLine = Profile.toMgdl(defaultValueHelper.determineHighLine(), units)
        val sgvLevel = if (lastBG.value > highLine) 1L else if (lastBG.value < lowLine) -1L else 0L
        val dataMap = DataMap()
        dataMap.putString("sgvString", lastBG.valueToUnitsString(units))
        dataMap.putString("glucoseUnits", units.asText)
        dataMap.putLong("timestamp", lastBG.timestamp)
        if (glucoseStatus == null) {
            dataMap.putString("slopeArrow", "")
            dataMap.putString("delta", "--")
            dataMap.putString("avgDelta", "--")
        } else {
            dataMap.putString("slopeArrow", trendCalculator.getTrendArrow(lastBG).symbol)
            dataMap.putString("delta", deltaString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units))
            dataMap.putString("avgDelta", deltaString(glucoseStatus.shortAvgDelta, glucoseStatus.shortAvgDelta * Constants.MGDL_TO_MMOLL, units))
        }
        dataMap.putLong("sgvLevel", sgvLevel)
        dataMap.putDouble("sgvDouble", lastBG.value)
        dataMap.putDouble("high", highLine)
        dataMap.putDouble("low", lowLine)
        return dataMap
    }

    private fun tempMap(startTime: Long, startBasal: Double, to: Long, toBasal: Double, amount: Double) =
        DataMap().apply {
            putLong("starttime", startTime)
            putDouble("startBasal", startBasal)
            putLong("endtime", to)
            putDouble("endbasal", toBasal)
            putDouble("amount", amount)
        }

    private fun basalMap(startTime: Long, endTime: Long, amount: Double) =
        DataMap().apply {
            putLong("starttime", startTime)
            putLong("endtime", endTime)
            putDouble("amount", amount)
        }

    private fun treatmentMap(date: Long, bolus: Double, carbs: Double, isSMB: Boolean, isValid: Boolean) =
        DataMap().apply {
            putLong("date", date)
            putDouble("bolus", bolus)
            putDouble("carbs", carbs)
            putBoolean("isSMB", isSMB)
            putBoolean("isValid", isValid)
        }

    private fun predictionMap(timestamp: Long, sgv: Double, color: Int) =
        DataMap().apply {
            putLong("timestamp", timestamp)
            putDouble("sgv", sgv)
            putInt("color", color)
        }

    private fun quickMap(q: QuickWizardEntry) =
        DataMap().apply {
            putString("guid", q.guid())
            putString("button_text", q.buttonText())
            putInt("carbs", q.carbs())
            putInt("from", q.validFrom())
            putInt("to", q.validTo())
        }

    private fun sendBolusProgress(progressPercent: Int?, status: String?) {
        progressPercent ?: return
        aapsLogger.debug(LTag.WEAR, "Sending bolus progress: $progressPercent $status")
        sendData(
            wearConstants.M_W_BOLUS_PROGRESS,
            DataMap().apply {
                putLong("timestamp", System.currentTimeMillis())
                putString("bolusProgress", "bolusProgress")
                putString("progressstatus", status ?: "")
                putInt("progresspercent", progressPercent)
            })
    }

    private fun sendActionConfirmationRequest(title: String?, message: String?, actionstring: String?) {
        title ?: message ?: actionstring ?: return
        aapsLogger.debug(LTag.WEAR, "Requesting confirmation from wear: $actionstring")
        sendData(
            wearConstants.M_W_ACTION_CONFIRMATION_REQUEST,
            DataMap().apply {
                putLong("timestamp", System.currentTimeMillis())
                putString("actionConfirmationRequest", "actionConfirmationRequest")
                putString("title", title)
                putString("message", message)
                putString("actionstring", actionstring)
            })
    }

    private fun sendChangeConfirmationRequest(title: String?, message: String?, actionstring: String?) {
        title ?: message ?: actionstring ?: return
        aapsLogger.debug(LTag.WEAR, "Requesting confirmation from wear: $actionstring")
        sendData(
            wearConstants.M_W_ACTION_CHANGE_CONFIRMATION_REQUEST,
            DataMap().apply {
                putLong("timestamp", System.currentTimeMillis())
                putString("changeConfirmationRequest", "changeConfirmationRequest")
                putString("title", title)
                putString("message", message)
                putString("actionstring", actionstring)
            })
    }

    private fun sendCancelNotificationRequest(actionstring: String?) {
        actionstring ?: return
        aapsLogger.debug(LTag.WEAR, "Canceling notification on wear: $actionstring")
        sendData(
            wearConstants.M_W_ACTION_CANCEL_NOTIFICATION_REQUEST,
            DataMap().apply {
                putLong("timestamp", System.currentTimeMillis())
                putString("cancelNotificationRequest", "cancelNotificationRequest")
                putString("actionstring", actionstring)
            })
    }

    private fun sendStatus() {
        aapsLogger.debug(LTag.WEAR, "Updating status on wear")
        val profile = profileFunction.getProfile()
        var status = rh.gs(R.string.noprofile)
        var iobSum = ""
        var iobDetail = ""
        var cobString = ""
        var currentBasal = ""
        var bgiString = ""
        if (profile != null) {
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            iobSum = to2Decimal(bolusIob.iob + basalIob.basaliob)
            iobDetail = "(" + to2Decimal(bolusIob.iob) + "|" + to2Decimal(basalIob.basaliob) + ")"
            cobString = iobCobCalculator.getCobInfo(false, "WatcherUpdaterService").generateCOBString()
            currentBasal = generateBasalString()

            //bgi
            val bgi = -(bolusIob.activity + basalIob.activity) * 5 * fromMgdlToUnits(profile.getIsfMgdl(), profileFunction.getUnits())
            bgiString = "" + (if (bgi >= 0) "+" else "") + to1Decimal(bgi)
            status = generateStatusString(profile, currentBasal, iobSum, iobDetail, bgiString)
        }

        //batteries
        val phoneBattery = receiverStatusStore.batteryLevel
        val rigBattery = nsDeviceStatus.uploaderStatus.trim { it <= ' ' }
        //OpenAPS status
        val openApsStatus =
            if (config.APS) loop.lastRun?.let { if (it.lastTBREnact != 0L) it.lastTBREnact else -1 } ?: -1
            else nsDeviceStatus.openApsTimestamp

        sendData(
            wearConstants.M_W_STATUS,
            DataMap().apply {
                //unique content
                putString("externalStatusString", status)
                putString("iobSum", iobSum)
                putString("iobDetail", iobDetail)
                putBoolean("detailedIob", sp.getBoolean(R.string.key_wear_detailediob, false))
                putString("cob", cobString)
                putString("currentBasal", currentBasal)
                putString("battery", "" + phoneBattery)
                putString("rigBattery", rigBattery)
                putLong("openApsStatus", openApsStatus)
                putString("bgi", bgiString)
                putBoolean("showBgi", sp.getBoolean(R.string.key_wear_showbgi, false))
                putInt("batteryLevel", if (phoneBattery >= 30) 1 else 0)
            })
    }

    private fun sendPreferences() {
        sendData(
            wearConstants.M_W_PREFERENCES,
            DataMap().apply {
                putLong("timestamp", System.currentTimeMillis())
                putBoolean(rh.gs(R.string.key_wear_control), sp.getBoolean(R.string.key_wear_control, false))
                putBoolean(rh.gs(R.string.key_units_mgdl), profileFunction.getUnits() == GlucoseUnit.MGDL)
                putInt(rh.gs(R.string.key_boluswizard_percentage), sp.getInt(R.string.key_boluswizard_percentage, 100))
                putInt(rh.gs(R.string.key_treatmentssafety_maxcarbs), sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48))
                putDouble(rh.gs(R.string.key_treatmentssafety_maxbolus), sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0))
            })
    }

    private fun sendQuickWizard() {
        val entities = ArrayList<DataMap>()
        for (i in 0 until quickWizard.size()) {
            val q = quickWizard[i]
            if (q.forDevice(QuickWizardEntry.DEVICE_WATCH)) entities.add(quickMap(q))
        }
        sendData(
            wearConstants.M_W_QUICK_WIZARD,
            DataMap().apply {
                putLong("timestamp", System.currentTimeMillis())
                putDataMapArrayList("quick_wizard", entities)
            })
    }

    private fun generateStatusString(profile: Profile?, currentBasal: String, iobSum: String, iobDetail: String, bgiString: String): String {
        var status = ""
        if (profile == null) return rh.gs(R.string.noprofile)
        if (!(loop as PluginBase).isEnabled()) status += rh.gs(R.string.disabledloop) + "\n"

        val iobString =
            if (sp.getBoolean(R.string.key_wear_detailediob, false)) "$iobSum $iobDetail"
            else iobSum + "U"

        status += "$currentBasal $iobString"

        //add BGI if shown, otherwise return
        if (sp.getBoolean(R.string.key_wear_showbgi, false)) status += " $bgiString"
        return status
    }

    private fun generateBasalString(): String {
        val profile: Profile = profileFunction.getProfile() ?: return ""
        return iobCobCalculator.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())?.toStringShort() ?: to2Decimal(profile.getBasal()) + "U/h"
    }

    companion object {

        const val WEAR_CAPABILITY = "androidaps_wear"

        val ACTION_RESEND = WatchUpdaterService::class.java.name + ".Resend"
        val ACTION_OPEN_SETTINGS = WatchUpdaterService::class.java.name + ".OpenSettings"
        val ACTION_SEND_STATUS = WatchUpdaterService::class.java.name + ".SendStatus"
        val ACTION_SEND_BASALS = WatchUpdaterService::class.java.name + ".SendBasals"
        val ACTION_SEND_BOLUS_PROGRESS = WatchUpdaterService::class.java.name + ".BolusProgress"
        val ACTION_SEND_ACTION_CONFIRMATION_REQUEST = WatchUpdaterService::class.java.name + ".ActionConfirmationRequest"
        val ACTION_SEND_CHANGE_CONFIRMATION_REQUEST = WatchUpdaterService::class.java.name + ".ChangeConfirmationRequest"
        val ACTION_CANCEL_NOTIFICATION = WatchUpdaterService::class.java.name + ".CancelNotification"

    }
}