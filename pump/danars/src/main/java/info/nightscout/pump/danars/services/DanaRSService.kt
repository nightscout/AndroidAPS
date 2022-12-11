package info.nightscout.pump.danars.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.BolusProgressData
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.comm.RecordTypes
import info.nightscout.pump.dana.events.EventDanaRNewStatus
import info.nightscout.pump.danars.DanaRSPlugin
import info.nightscout.pump.danars.comm.DanaRSPacket
import info.nightscout.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketAPSHistoryEvents
import info.nightscout.pump.danars.comm.DanaRSPacketAPSSetEventHistory
import info.nightscout.pump.danars.comm.DanaRSPacketBasalGetBasalRate
import info.nightscout.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetProfileBasalRate
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetProfileNumber
import info.nightscout.pump.danars.comm.DanaRSPacketBasalSetTemporaryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGet24CIRCFArray
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetBolusOption
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetCIRCFArray
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetCalculationInformation
import info.nightscout.pump.danars.comm.DanaRSPacketBolusGetStepBolusInformation
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSet24CIRCFArray
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetExtendedBolus
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetExtendedBolusCancel
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetStepBolusStart
import info.nightscout.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import info.nightscout.pump.danars.comm.DanaRSPacketEtcKeepConnection
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralGetPumpCheck
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralGetShippingInformation
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import info.nightscout.pump.danars.comm.DanaRSPacketGeneralSetHistoryUploadMode
import info.nightscout.pump.danars.comm.DanaRSPacketHistory
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryAlarm
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryBasal
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryBloodGlucose
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryBolus
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryCarbohydrate
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryDaily
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryPrime
import info.nightscout.pump.danars.comm.DanaRSPacketHistoryRefill
import info.nightscout.pump.danars.comm.DanaRSPacketHistorySuspend
import info.nightscout.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import info.nightscout.pump.danars.comm.DanaRSPacketOptionGetPumpUTCAndTimeZone
import info.nightscout.pump.danars.comm.DanaRSPacketOptionGetUserOption
import info.nightscout.pump.danars.comm.DanaRSPacketOptionSetPumpTime
import info.nightscout.pump.danars.comm.DanaRSPacketOptionSetPumpUTCAndTimeZone
import info.nightscout.pump.danars.comm.DanaRSPacketOptionSetUserOption
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventInitializationChanged
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.events.EventProfileSwitchChanged
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

class DanaRSService : DaggerService() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var bleComm: BLEComm
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()
    private var lastApproachingDailyLimit: Long = 0

    override fun onCreate() {
        super.onCreate()
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ stopSelf() }, fabricPrivacy::logException)
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

    fun sendMessage(message: DanaRSPacket) {
        bleComm.sendMessage(message)
    }

    fun readPumpStatus() {
        try {
            val pump = activePlugin.activePump
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumpsettings)))
            sendMessage(DanaRSPacketEtcKeepConnection(injector)) // test encryption for v3 & BLE
            if (!bleComm.isConnected) return
            sendMessage(DanaRSPacketGeneralGetShippingInformation(injector)) // serial no
            sendMessage(DanaRSPacketGeneralGetPumpCheck(injector)) // firmware
            sendMessage(DanaRSPacketBasalGetProfileNumber(injector))
            sendMessage(DanaRSPacketBolusGetBolusOption(injector)) // isExtendedEnabled
            sendMessage(DanaRSPacketBasalGetBasalRate(injector)) // basal profile, basalStep, maxBasal
            sendMessage(DanaRSPacketBolusGetCalculationInformation(injector)) // target
            if (danaPump.profile24) sendMessage(DanaRSPacketBolusGet24CIRCFArray(injector))
            else sendMessage(DanaRSPacketBolusGetCIRCFArray(injector))
            sendMessage(DanaRSPacketOptionGetUserOption(injector)) // Getting user options
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumpstatus)))
            sendMessage(DanaRSPacketGeneralInitialScreenInformation(injector))
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingbolusstatus)))
            sendMessage(DanaRSPacketBolusGetStepBolusInformation(injector)) // last bolus, bolusStep, maxBolus
            danaPump.lastConnection = System.currentTimeMillis()
            val profile = profileFunction.getProfile()
            if (profile != null && abs(danaPump.currentBasal - profile.getBasal()) >= pump.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumpsettings)))
                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileSwitchChanged())
                }
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumptime)))
            if (danaPump.usingUTC) sendMessage(DanaRSPacketOptionGetPumpUTCAndTimeZone(injector))
            else sendMessage(DanaRSPacketOptionGetPumpTime(injector))
            var timeDiff = (danaPump.getPumpTime() - System.currentTimeMillis()) / 1000L
            if (danaPump.getPumpTime() == 0L) {
                // initial handshake was not successful
                // de-initialize pump
                danaPump.reset()
                rxBus.send(EventDanaRNewStatus())
                rxBus.send(EventInitializationChanged())
                return
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds")
            // phone timezone
            val tz = DateTimeZone.getDefault()
            val instant = DateTime.now().millis
            val offsetInMilliseconds = tz.getOffset(instant).toLong()
            val offset = TimeUnit.MILLISECONDS.toHours(offsetInMilliseconds).toInt()
            if (abs(timeDiff) > 3 || danaPump.usingUTC && offset != danaPump.zoneOffset) {
                if (abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds - large difference")
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    uiInteraction.runAlarm(rh.gs(info.nightscout.pump.dana.R.string.largetimediff), rh.gs(info.nightscout.pump.dana.R.string.largetimedifftitle), info.nightscout.core.ui.R.raw.error)

                    //de-initialize pump
                    danaPump.reset()
                    rxBus.send(EventDanaRNewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                } else {
                    when {
                        danaPump.usingUTC      -> {
                            sendMessage(DanaRSPacketOptionSetPumpUTCAndTimeZone(injector, dateUtil.now(), offset))
                        }

                        danaPump.protocol >= 5 -> { // can set seconds
                            sendMessage(DanaRSPacketOptionSetPumpTime(injector, dateUtil.now()))
                        }

                        else                   -> {
                            waitForWholeMinute() // Dana can set only whole minute
                            // add 10sec to be sure we are over minute (will be cut off anyway)
                            sendMessage(DanaRSPacketOptionSetPumpTime(injector, dateUtil.now() + T.secs(10).msecs()))
                        }
                    }
                    if (danaPump.usingUTC) sendMessage(DanaRSPacketOptionGetPumpUTCAndTimeZone(injector))
                    else sendMessage(DanaRSPacketOptionGetPumpTime(injector))
                    timeDiff = (danaPump.getPumpTime() - System.currentTimeMillis()) / 1000L
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds")
                }
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.core.ui.R.string.reading_pump_history)))
            loadEvents()
            // RS doesn't provide exact timestamp = rely on history
            val eb = pumpSync.expectedPumpState().extendedBolus
            danaPump.fromExtendedBolus(eb)
            val tbr = pumpSync.expectedPumpState().temporaryBasal
            danaPump.fromTemporaryBasal(tbr)
            rxBus.send(EventDanaRNewStatus())
            rxBus.send(EventInitializationChanged())
            if (danaPump.dailyTotalUnits > danaPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMPCOMM, "Approaching daily limit: " + danaPump.dailyTotalUnits + "/" + danaPump.maxDailyTotalUnits)
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    uiInteraction.addNotification(Notification.APPROACHING_DAILY_LIMIT, rh.gs(info.nightscout.pump.dana.R.string.approachingdailylimit), Notification.URGENT)
                    pumpSync.insertAnnouncement(
                        rh.gs(info.nightscout.pump.dana.R.string.approachingdailylimit) + ": " + danaPump.dailyTotalUnits + "/" + danaPump.maxDailyTotalUnits + "U",
                        null,
                        danaPump.pumpType(),
                        danaPump.serialNumber
                    )
                    lastApproachingDailyLimit = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "Unhandled exception", e)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump status loaded")
    }

    fun loadEvents(): PumpEnactResult {
        if (!danaRSPlugin.isInitialized()) {
            val result = PumpEnactResult(injector).success(false)
            result.comment = "pump not initialized"
            return result
        }
        SystemClock.sleep(1000)
        val msg = DanaRSPacketAPSHistoryEvents(injector, danaPump.readHistoryFrom)
        aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + dateUtil.dateAndTimeString(danaPump.readHistoryFrom))
        sendMessage(msg)
        while (!danaPump.historyDoneReceived && bleComm.isConnected) {
            SystemClock.sleep(100)
        }
        danaPump.readHistoryFrom = if (danaPump.lastEventTimeLoaded != 0L) danaPump.lastEventTimeLoaded - T.mins(1).msecs() else 0
        aapsLogger.debug(LTag.PUMPCOMM, "Events loaded")
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingpumpstatus)))
        sendMessage(DanaRSPacketGeneralInitialScreenInformation(injector))
        danaPump.lastConnection = System.currentTimeMillis()
        return PumpEnactResult(injector).success(msg.success())
    }

    fun setUserSettings(): PumpEnactResult {
        val message = DanaRSPacketOptionSetUserOption(injector)
        sendMessage(message)
        return PumpEnactResult(injector).success(message.success())
    }

    fun bolus(insulin: Double, carbs: Int, carbTime: Long, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!isConnected) return false
        if (BolusProgressData.stopPressed) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.startingbolus)))
        val preferencesSpeed = sp.getInt(info.nightscout.pump.dana.R.string.key_danars_bolusspeed, 0)
        danaPump.bolusDone = false
        danaPump.bolusingTreatment = t
        danaPump.bolusAmountToBeDelivered = insulin
        danaPump.bolusStopped = false
        danaPump.bolusStopForced = false
        danaPump.bolusProgressLastTimeStamp = dateUtil.now()
        val start = DanaRSPacketBolusSetStepBolusStart(injector, insulin, preferencesSpeed)
        if (carbs > 0) {
//            MsgSetCarbsEntry msg = new MsgSetCarbsEntry(carbTime, carbs); ####
//            sendMessage(msg);
            val msgSetHistoryEntryV2 = DanaRSPacketAPSSetEventHistory(injector, DanaPump.HistoryEntry.CARBS.value, carbTime, carbs, 0)
            sendMessage(msgSetHistoryEntryV2)
            danaPump.readHistoryFrom = min(danaPump.readHistoryFrom, carbTime - T.mins(1).msecs())
            if (!msgSetHistoryEntryV2.isReceived || msgSetHistoryEntryV2.failed)
                uiInteraction.runAlarm(rh.gs(info.nightscout.pump.dana.R.string.carbs_store_error), rh.gs(info.nightscout.core.ui.R.string.error), info.nightscout.core.ui.R.raw.boluserror)
        }
        val bolusStart = System.currentTimeMillis()
        if (insulin > 0) {
            if (!danaPump.bolusStopped) {
                sendMessage(start)
            } else {
                t.insulin = 0.0
                return false
            }
            while (!danaPump.bolusStopped && !start.failed && !danaPump.bolusDone) {
                SystemClock.sleep(100)
                if (System.currentTimeMillis() - danaPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if i didn't receive status for more than 20 sec expecting broken comm
                    danaPump.bolusStopped = true
                    danaPump.bolusStopForced = true
                    aapsLogger.debug(LTag.PUMPCOMM, "Communication stopped")
                    bleComm.disconnect("Communication stopped")
                }
            }
        }
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.t = t
        bolusingEvent.percent = 99
        danaPump.bolusingTreatment = null
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
            bolusingEvent.status = rh.gs(info.nightscout.pump.dana.R.string.waitingforestimatedbolusend, waitTime / 1000)
            rxBus.send(bolusingEvent)
            SystemClock.sleep(1000)
        }
        // do not call loadEvents() directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                // reread bolus status
                rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.gettingbolusstatus)))
                sendMessage(DanaRSPacketBolusGetStepBolusInformation(injector)) // last bolus
                bolusingEvent.percent = 100
                rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.shared.R.string.disconnecting)))
            }
        })
        return !start.failed
    }

    fun bolusStop() {
        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop >>>>> @ " + if (danaPump.bolusingTreatment == null) "" else danaPump.bolusingTreatment?.insulin)
        val stop = DanaRSPacketBolusSetStepBolusStop(injector)
        danaPump.bolusStopForced = true
        if (isConnected) {
            sendMessage(stop)
            while (!danaPump.bolusStopped) {
                sendMessage(stop)
                SystemClock.sleep(200)
            }
        } else {
            danaPump.bolusStopped = true
        }
    }

    fun tempBasal(percent: Int, durationInHours: Int): Boolean {
        if (!isConnected) return false
        val status = DanaRSPacketGeneralInitialScreenInformation(injector)
        sendMessage(status)
        if (status.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingtempbasal)))
            sendMessage(DanaRSPacketBasalSetCancelTemporaryBasal(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.settingtempbasal)))
        val msgTBR = DanaRSPacketBasalSetTemporaryBasal(injector, percent, durationInHours)
        sendMessage(msgTBR)
        SystemClock.sleep(200)
        loadEvents()
        SystemClock.sleep(4500)
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        danaPump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgTBR.success()
    }

    fun highTempBasal(percent: Int): Boolean {
        val status = DanaRSPacketGeneralInitialScreenInformation(injector)
        sendMessage(status)
        if (status.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingtempbasal)))
            sendMessage(DanaRSPacketBasalSetCancelTemporaryBasal(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.settingtempbasal)))
        val msgTBR = DanaRSPacketAPSBasalSetTemporaryBasal(injector, percent)
        sendMessage(msgTBR)
        loadEvents()
        SystemClock.sleep(4500)
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        danaPump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgTBR.success()
    }

    fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error(LTag.PUMPCOMM, "Wrong duration param")
            return false
        }
        val status = DanaRSPacketGeneralInitialScreenInformation(injector)
        sendMessage(status)
        if (status.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingtempbasal)))
            sendMessage(DanaRSPacketBasalSetCancelTemporaryBasal(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.settingtempbasal)))
        val msgTBR = DanaRSPacketAPSBasalSetTemporaryBasal(injector, percent)
        sendMessage(msgTBR)
        loadEvents()
        SystemClock.sleep(4500)
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        aapsLogger.debug(LTag.PUMPCOMM, "Expected TBR found: $tbr")
        danaPump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgTBR.success()
    }

    fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingtempbasal)))
        val msgCancel = DanaRSPacketBasalSetCancelTemporaryBasal(injector)
        sendMessage(msgCancel)
        loadEvents()
        SystemClock.sleep(4500)
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        danaPump.fromTemporaryBasal(tbr)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgCancel.success()
    }

    fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.settingextendedbolus)))
        val msgExtended = DanaRSPacketBolusSetExtendedBolus(injector, insulin, durationInHalfHours)
        sendMessage(msgExtended)
        SystemClock.sleep(200)
        loadEvents()
        SystemClock.sleep(4500)
        val eb = pumpSync.expectedPumpState().extendedBolus
        danaPump.fromExtendedBolus(eb)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgExtended.success()
    }

    fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.stoppingextendedbolus)))
        val msgStop = DanaRSPacketBolusSetExtendedBolusCancel(injector)
        sendMessage(msgStop)
        loadEvents()
        SystemClock.sleep(4500)
        val eb = pumpSync.expectedPumpState().extendedBolus
        danaPump.fromExtendedBolus(eb)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgStop.success()
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.updatingbasalrates)))
        val basal = danaPump.buildDanaRProfileRecord(profile)
        val msgSet = DanaRSPacketBasalSetProfileBasalRate(injector, 0, basal)
        sendMessage(msgSet)
        val msgActivate = DanaRSPacketBasalSetProfileNumber(injector, 0)
        sendMessage(msgActivate)
        if (danaPump.profile24) {
            val msgProfile = DanaRSPacketBolusSet24CIRCFArray(injector, profile)
            sendMessage(msgProfile)
        }
        readPumpStatus()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgSet.success()
    }

    fun loadHistory(type: Byte): PumpEnactResult {
        val result = PumpEnactResult(injector)
        if (!isConnected) return result
        var msg: DanaRSPacketHistory? = null
        when (type) {
            RecordTypes.RECORD_TYPE_ALARM     -> msg = DanaRSPacketHistoryAlarm(injector)
            RecordTypes.RECORD_TYPE_PRIME     -> msg = DanaRSPacketHistoryPrime(injector)
            RecordTypes.RECORD_TYPE_BASALHOUR -> msg = DanaRSPacketHistoryBasal(injector)
            RecordTypes.RECORD_TYPE_BOLUS     -> msg = DanaRSPacketHistoryBolus(injector)
            RecordTypes.RECORD_TYPE_CARBO     -> msg = DanaRSPacketHistoryCarbohydrate(injector)
            RecordTypes.RECORD_TYPE_DAILY     -> msg = DanaRSPacketHistoryDaily(injector)
            RecordTypes.RECORD_TYPE_GLUCOSE   -> msg = DanaRSPacketHistoryBloodGlucose(injector)
            RecordTypes.RECORD_TYPE_REFILL    -> msg = DanaRSPacketHistoryRefill(injector)
            RecordTypes.RECORD_TYPE_SUSPEND   -> msg = DanaRSPacketHistorySuspend(injector)
        }
        if (msg != null) {
            sendMessage(DanaRSPacketGeneralSetHistoryUploadMode(injector, 1))
            SystemClock.sleep(200)
            sendMessage(msg)
            while (!msg.done && isConnected) {
                SystemClock.sleep(100)
            }
            SystemClock.sleep(200)
            sendMessage(DanaRSPacketGeneralSetHistoryUploadMode(injector, 0))
        }
        result.success = msg?.success() ?: false
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
            val time = dateUtil.now()
            val timeToWholeMinute = 60000 - time % 60000
            if (timeToWholeMinute > 59800 || timeToWholeMinute < 300) break
            rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.dana.R.string.waitingfortimesynchronization, (timeToWholeMinute / 1000).toInt())))
            SystemClock.sleep(min(timeToWholeMinute, 100))
        }
    }
}