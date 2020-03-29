package info.nightscout.androidaps.plugins.pump.danaRS.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRNewStatus
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.plugins.pump.danaRS.comm.*
import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

class DanaRSService : DaggerService() {
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var context: Context
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var danaRPump: DanaRPump
    @Inject lateinit var danaRSMessageHashTable: DanaRSMessageHashTable
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var bleComm: BLEComm
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()
    private var bolusingTreatment: Treatment? = null
    private var lastHistoryFetched: Long = 0
    private var lastApproachingDailyLimit: Long = 0

    override fun onCreate() {
        super.onCreate()
        disposable.add(rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ stopSelf() }) { fabricPrivacy.logException(it) }
        )
    }

    override fun onDestroy() {
        disposable.clear()
        super.onDestroy()
    }

    val isConnected: Boolean
        get() = bleComm.isConnected

    val isConnecting: Boolean
        get() = bleComm.isConnecting

    fun connect(from: String, address: String): Boolean {
        return bleComm.connect(from, address)
    }

    fun stopConnecting() {
        bleComm.stopConnecting()
    }

    fun disconnect(from: String) {
        bleComm.disconnect(from)
    }

    fun sendMessage(message: DanaRS_Packet) {
        bleComm.sendMessage(message)
    }

    fun readPumpStatus() {
        try {
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpstatus)))
            sendMessage(DanaRS_Packet_General_Initial_Screen_Information(aapsLogger, danaRPump))
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingextendedbolusstatus)))
            sendMessage(DanaRS_Packet_Bolus_Get_Extended_Bolus_State(aapsLogger, danaRPump))
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingbolusstatus)))
            sendMessage(DanaRS_Packet_Bolus_Get_Step_Bolus_Information(aapsLogger, danaRPump)) // last bolus, bolusStep, maxBolus
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingtempbasalstatus)))
            sendMessage(DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump))
            danaRPump.lastConnection = System.currentTimeMillis()
            val profile = profileFunction.getProfile()
            val pump = activePlugin.activePump
            if (profile != null && abs(danaRPump.currentBasal - profile.basal) >= pump.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)))
                sendMessage(DanaRS_Packet_Basal_Get_Basal_Rate(aapsLogger, rxBus, resourceHelper, danaRPump)) // basal profile, basalStep, maxBasal
                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileNeedsUpdate())
                }
            }
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumptime)))
            sendMessage(DanaRS_Packet_Option_Get_Pump_Time(aapsLogger, danaRPump))
            var timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L
            if (danaRPump.pumpTime == 0L) {
                // initial handshake was not successful
                // de-initialize pump
                danaRPump.reset()
                rxBus.send(EventDanaRNewStatus())
                rxBus.send(EventInitializationChanged())
                return
            }
            val now = System.currentTimeMillis()
            if (danaRPump.lastSettingsRead + 60 * 60 * 1000L < now || !pump.isInitialized) {
                rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingpumpsettings)))
                sendMessage(DanaRS_Packet_General_Get_Shipping_Information(aapsLogger, danaRPump)) // serial no
                sendMessage(DanaRS_Packet_General_Get_Pump_Check(aapsLogger, danaRPump, rxBus, resourceHelper)) // firmware
                sendMessage(DanaRS_Packet_Basal_Get_Profile_Number(aapsLogger, danaRPump))
                sendMessage(DanaRS_Packet_Bolus_Get_Bolus_Option(aapsLogger, rxBus, resourceHelper, danaRPump)) // isExtendedEnabled
                sendMessage(DanaRS_Packet_Basal_Get_Basal_Rate(aapsLogger, rxBus, resourceHelper, danaRPump)) // basal profile, basalStep, maxBasal
                sendMessage(DanaRS_Packet_Bolus_Get_Calculation_Information(aapsLogger, danaRPump)) // target
                sendMessage(DanaRS_Packet_Bolus_Get_CIR_CF_Array(aapsLogger, danaRPump))
                sendMessage(DanaRS_Packet_Option_Get_User_Option(aapsLogger, danaRPump)) // Getting user options
                danaRPump.lastSettingsRead = now
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds")
            if (abs(timeDiff) > 3) {
                if (abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds - large difference")
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    val i = Intent(context, ErrorHelperActivity::class.java)
                    i.putExtra("soundid", R.raw.error)
                    i.putExtra("status", resourceHelper.gs(R.string.largetimediff))
                    i.putExtra("title", resourceHelper.gs(R.string.largetimedifftitle))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)

                    //de-initialize pump
                    danaRPump.reset()
                    rxBus.send(EventDanaRNewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                } else {
                    if (danaRPump.protocol >= 6) {
                        sendMessage(DanaRS_Packet_Option_Set_Pump_Time(aapsLogger, DateUtil.now()))
                    } else {
                        waitForWholeMinute() // Dana can set only whole minute
                        // add 10sec to be sure we are over minute (will be cut off anyway)
                        sendMessage(DanaRS_Packet_Option_Set_Pump_Time(aapsLogger, DateUtil.now() + T.secs(10).msecs()))
                    }
                    sendMessage(DanaRS_Packet_Option_Get_Pump_Time(aapsLogger, danaRPump))
                    timeDiff = (danaRPump.pumpTime - System.currentTimeMillis()) / 1000L
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds")
                }
            }
            loadEvents()
            rxBus.send(EventDanaRNewStatus())
            rxBus.send(EventInitializationChanged())
            //NSUpload.uploadDeviceStatus();
            if (danaRPump.dailyTotalUnits > danaRPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMPCOMM, "Approaching daily limit: " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits)
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    val reportFail = Notification(Notification.APPROACHING_DAILY_LIMIT, resourceHelper.gs(R.string.approachingdailylimit), Notification.URGENT)
                    rxBus.send(EventNewNotification(reportFail))
                    NSUpload.uploadError(resourceHelper.gs(R.string.approachingdailylimit) + ": " + danaRPump.dailyTotalUnits + "/" + danaRPump.maxDailyTotalUnits + "U")
                    lastApproachingDailyLimit = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "Unhandled exception", e)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump status loaded")
    }

    fun loadEvents(): PumpEnactResult {
        if (!danaRSPlugin.isInitialized) {
            val result = PumpEnactResult(injector).success(false)
            result.comment = "pump not initialized"
            return result
        }
        SystemClock.sleep(1000)
        val msg: DanaRS_Packet_APS_History_Events
        if (lastHistoryFetched == 0L) {
            msg = DanaRS_Packet_APS_History_Events(aapsLogger, rxBus, resourceHelper, activePlugin, danaRPump, detailedBolusInfoStorage, 0)
            aapsLogger.debug(LTag.PUMPCOMM, "Loading complete event history")
        } else {
            msg = DanaRS_Packet_APS_History_Events(aapsLogger, rxBus, resourceHelper, activePlugin, danaRPump, detailedBolusInfoStorage, lastHistoryFetched)
            aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + DateUtil.dateAndTimeString(lastHistoryFetched))
        }
        sendMessage(msg)
        while (!danaRPump.historyDoneReceived && bleComm.isConnected) {
            SystemClock.sleep(100)
        }
        lastHistoryFetched = if (danaRPump.lastEventTimeLoaded != 0L) danaRPump.lastEventTimeLoaded - T.mins(1).msecs() else 0
        aapsLogger.debug(LTag.PUMPCOMM, "Events loaded")
        danaRPump.lastConnection = System.currentTimeMillis()
        return PumpEnactResult(injector).success(true)
    }

    fun setUserSettings(): PumpEnactResult {
        sendMessage(DanaRS_Packet_Option_Get_User_Option(aapsLogger, danaRPump))
        return PumpEnactResult(injector).success(true)
    }

    fun bolus(insulin: Double, carbs: Int, carbTime: Long, t: Treatment): Boolean {
        if (!isConnected) return false
        if (BolusProgressDialog.stopPressed) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.startingbolus)))
        bolusingTreatment = t
        val preferencesSpeed = sp.getInt(R.string.key_danars_bolusspeed, 0)
        danaRPump.bolusingTreatment = t
        danaRPump.bolusAmountToBeDelivered = insulin
        danaRPump.bolusStopped = false
        danaRPump.bolusStopForced = false
        danaRPump.bolusProgressLastTimeStamp = DateUtil.now()
        val start = DanaRS_Packet_Bolus_Set_Step_Bolus_Start(aapsLogger, danaRPump, constraintChecker, insulin, preferencesSpeed)
        if (carbs > 0) {
//            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(carbTime, carbs); ####
//            sendMessage(msg);
            val msgSetHistoryEntryV2 = DanaRS_Packet_APS_Set_Event_History(aapsLogger, DanaRPump.CARBS, carbTime, carbs, 0)
            sendMessage(msgSetHistoryEntryV2)
            lastHistoryFetched = min(lastHistoryFetched, carbTime - T.mins(1).msecs())
        }
        val bolusStart = System.currentTimeMillis()
        if (insulin > 0) {
            if (!danaRPump.bolusStopped) {
                sendMessage(start)
            } else {
                t.insulin = 0.0
                return false
            }
            while (!danaRPump.bolusStopped && !start.failed && !danaRPump.bolusDone) {
                SystemClock.sleep(100)
                if (System.currentTimeMillis() - danaRPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if i didn't receive status for more than 20 sec expecting broken comm
                    danaRPump.bolusStopped = true
                    danaRPump.bolusStopForced = true
                    aapsLogger.debug(LTag.PUMPCOMM, "Communication stopped")
                }
            }
        }
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.t = t
        bolusingEvent.percent = 99
        bolusingTreatment = null
        var speed = 12
        when (preferencesSpeed) {
            0 -> speed = 12
            1 -> speed = 30
            2 -> speed = 60
        }
        val bolusDurationInMSec = (insulin * speed * 1000).toLong()
        val expectedEnd = bolusStart + bolusDurationInMSec + 2000
        while (System.currentTimeMillis() < expectedEnd) {
            val waitTime = expectedEnd - System.currentTimeMillis()
            bolusingEvent.status = String.format(resourceHelper.gs(R.string.waitingforestimatedbolusend), waitTime / 1000)
            rxBus.send(bolusingEvent)
            SystemClock.sleep(1000)
        }
        // do not call loadEvents() directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                // reread bolus status
                rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.gettingbolusstatus)))
                sendMessage(DanaRS_Packet_Bolus_Get_Step_Bolus_Information(aapsLogger, danaRPump)) // last bolus
                bolusingEvent.percent = 100
                rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.disconnecting)))
            }
        })
        return !start.failed
    }

    fun bolusStop() {
        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop >>>>> @ " + if (bolusingTreatment == null) "" else bolusingTreatment?.insulin)
        val stop = DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(aapsLogger, rxBus, resourceHelper, danaRPump)
        danaRPump.bolusStopForced = true
        if (isConnected) {
            sendMessage(stop)
            while (!danaRPump.bolusStopped) {
                sendMessage(stop)
                SystemClock.sleep(200)
            }
        } else {
            danaRPump.bolusStopped = true
        }
    }

    fun tempBasal(percent: Int, durationInHours: Int): Boolean {
        if (!isConnected) return false
        if (danaRPump.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
            sendMessage(DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)))
        sendMessage(DanaRS_Packet_Basal_Set_Temporary_Basal(aapsLogger, percent, durationInHours))
        SystemClock.sleep(200)
        sendMessage(DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun highTempBasal(percent: Int): Boolean {
        if (danaRPump.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
            sendMessage(DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)))
        sendMessage(DanaRS_Packet_APS_Basal_Set_Temporary_Basal(aapsLogger, percent))
        sendMessage(DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error(LTag.PUMPCOMM, "Wrong duration param")
            return false
        }
        if (danaRPump.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
            sendMessage(DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingtempbasal)))
        sendMessage(DanaRS_Packet_APS_Basal_Set_Temporary_Basal(aapsLogger, percent))
        sendMessage(DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingtempbasal)))
        sendMessage(DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger))
        sendMessage(DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.settingextendedbolus)))
        sendMessage(DanaRS_Packet_Bolus_Set_Extended_Bolus(aapsLogger, insulin, durationInHalfHours))
        SystemClock.sleep(200)
        sendMessage(DanaRS_Packet_Bolus_Get_Extended_Bolus_State(aapsLogger, danaRPump))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.stoppingextendedbolus)))
        sendMessage(DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel(aapsLogger))
        sendMessage(DanaRS_Packet_Bolus_Get_Extended_Bolus_State(aapsLogger, danaRPump))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.updatingbasalrates)))
        val basal = danaRPump.buildDanaRProfileRecord(profile)
        val msgSet = DanaRS_Packet_Basal_Set_Profile_Basal_Rate(aapsLogger, 0, basal)
        sendMessage(msgSet)
        val msgActivate = DanaRS_Packet_Basal_Set_Profile_Number(aapsLogger, 0)
        sendMessage(msgActivate)
        danaRPump.lastSettingsRead = 0 // force read full settings
        readPumpStatus()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    fun loadHistory(type: Byte): PumpEnactResult {
        val result = PumpEnactResult(injector)
        if (!isConnected) return result
        var msg: DanaRS_Packet_History_? = null
        when (type) {
            RecordTypes.RECORD_TYPE_ALARM     -> msg = DanaRS_Packet_History_Alarm(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_PRIME     -> msg = DanaRS_Packet_History_Prime(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_BASALHOUR -> msg = DanaRS_Packet_History_Basal(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_BOLUS     -> msg = DanaRS_Packet_History_Bolus(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_CARBO     -> msg = DanaRS_Packet_History_Carbohydrate(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_DAILY     -> msg = DanaRS_Packet_History_Daily(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_GLUCOSE   -> msg = DanaRS_Packet_History_Blood_Glucose(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_REFILL    -> msg = DanaRS_Packet_History_Refill(aapsLogger, rxBus)
            RecordTypes.RECORD_TYPE_SUSPEND   -> msg = DanaRS_Packet_History_Suspend(aapsLogger, rxBus)
        }
        if (msg != null) {
            sendMessage(DanaRS_Packet_General_Set_History_Upload_Mode(aapsLogger, 1))
            SystemClock.sleep(200)
            sendMessage(msg)
            while (!msg.done && isConnected) {
                SystemClock.sleep(100)
            }
            SystemClock.sleep(200)
            sendMessage(DanaRS_Packet_General_Set_History_Upload_Mode(aapsLogger, 0))
        }
        result.success = true
        result.comment = "OK"
        return result
    }

    inner class LocalBinder : Binder() {
        val serviceInstance: DanaRSService
            get() = this@DanaRSService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    private fun waitForWholeMinute() {
        while (true) {
            val time = DateUtil.now()
            val timeToWholeMinute = 60000 - time % 60000
            if (timeToWholeMinute > 59800 || timeToWholeMinute < 300) break
            rxBus.send(EventPumpStatusChanged(resourceHelper.gs(R.string.waitingfortimesynchronization, (timeToWholeMinute / 1000).toInt())))
            SystemClock.sleep(min(timeToWholeMinute, 100))
        }
    }
}