package app.aaps.pump.danars.services

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketAPSHistoryEvents
import app.aaps.pump.danars.comm.DanaRSPacketAPSSetEventHistory
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBolusGet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetBolusOption
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCalculationInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetStepBolusInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusSet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolus
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolusCancel
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStart
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import app.aaps.pump.danars.comm.DanaRSPacketEtcKeepConnection
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetPumpCheck
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetShippingInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralSetHistoryUploadMode
import app.aaps.pump.danars.comm.DanaRSPacketHistory
import app.aaps.pump.danars.comm.DanaRSPacketHistoryAlarm
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBasal
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBloodGlucose
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBolus
import app.aaps.pump.danars.comm.DanaRSPacketHistoryCarbohydrate
import app.aaps.pump.danars.comm.DanaRSPacketHistoryDaily
import app.aaps.pump.danars.comm.DanaRSPacketHistoryPrime
import app.aaps.pump.danars.comm.DanaRSPacketHistoryRefill
import app.aaps.pump.danars.comm.DanaRSPacketHistorySuspend
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetUserOption
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetUserOption
import dagger.android.DaggerService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.min

class DanaRSService : DaggerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var bleComm: BLEComm
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>
    @Inject lateinit var danaRSPacketAPSBasalSetTemporaryBasal: Provider<DanaRSPacketAPSBasalSetTemporaryBasal>
    @Inject lateinit var danaRSPacketAPSHistoryEvents: Provider<DanaRSPacketAPSHistoryEvents>
    @Inject lateinit var danaRSPacketAPSSetEventHistory: Provider<DanaRSPacketAPSSetEventHistory>
    @Inject lateinit var danaRSPacketBasalGetBasalRate: Provider<DanaRSPacketBasalGetBasalRate>
    @Inject lateinit var danaRSPacketBasalGetProfileNumber: Provider<DanaRSPacketBasalGetProfileNumber>
    @Inject lateinit var danaRSPacketBasalSetCancelTemporaryBasal: Provider<DanaRSPacketBasalSetCancelTemporaryBasal>
    @Inject lateinit var danaRSPacketBasalSetProfileBasalRate: Provider<DanaRSPacketBasalSetProfileBasalRate>
    @Inject lateinit var danaRSPacketBasalSetProfileNumber: Provider<DanaRSPacketBasalSetProfileNumber>
    @Inject lateinit var danaRSPacketBasalSetTemporaryBasal: Provider<DanaRSPacketBasalSetTemporaryBasal>
    @Inject lateinit var danaRSPacketBolusGet24CIRCFArray: Provider<DanaRSPacketBolusGet24CIRCFArray>
    @Inject lateinit var danaRSPacketBolusGetBolusOption: Provider<DanaRSPacketBolusGetBolusOption>
    @Inject lateinit var danaRSPacketBolusGetCalculationInformation: Provider<DanaRSPacketBolusGetCalculationInformation>
    @Inject lateinit var danaRSPacketBolusGetCIRCFArray: Provider<DanaRSPacketBolusGetCIRCFArray>
    @Inject lateinit var danaRSPacketBolusGetStepBolusInformation: Provider<DanaRSPacketBolusGetStepBolusInformation>
    @Inject lateinit var danaRSPacketBolusSet24CIRCFArray: Provider<DanaRSPacketBolusSet24CIRCFArray>
    @Inject lateinit var danaRSPacketBolusSetExtendedBolus: Provider<DanaRSPacketBolusSetExtendedBolus>
    @Inject lateinit var danaRSPacketBolusSetExtendedBolusCancel: Provider<DanaRSPacketBolusSetExtendedBolusCancel>
    @Inject lateinit var danaRSPacketBolusSetStepBolusStart: Provider<DanaRSPacketBolusSetStepBolusStart>
    @Inject lateinit var danaRSPacketBolusSetStepBolusStop: Provider<DanaRSPacketBolusSetStepBolusStop>
    @Inject lateinit var danaRSPacketEtcKeepConnection: Provider<DanaRSPacketEtcKeepConnection>
    @Inject lateinit var danaRSPacketGeneralGetPumpCheck: Provider<DanaRSPacketGeneralGetPumpCheck>
    @Inject lateinit var danaRSPacketGeneralGetShippingInformation: Provider<DanaRSPacketGeneralGetShippingInformation>
    @Inject lateinit var danaRSPacketGeneralInitialScreenInformation: Provider<DanaRSPacketGeneralInitialScreenInformation>
    @Inject lateinit var danaRSPacketGeneralSetHistoryUploadMode: Provider<DanaRSPacketGeneralSetHistoryUploadMode>
    @Inject lateinit var danaRSPacketOptionGetPumpTime: Provider<DanaRSPacketOptionGetPumpTime>
    @Inject lateinit var danaRSPacketOptionGetPumpUTCAndTimeZone: Provider<DanaRSPacketOptionGetPumpUTCAndTimeZone>
    @Inject lateinit var danaRSPacketOptionGetUserOption: Provider<DanaRSPacketOptionGetUserOption>
    @Inject lateinit var danaRSPacketOptionSetPumpTime: Provider<DanaRSPacketOptionSetPumpTime>
    @Inject lateinit var danaRSPacketOptionSetPumpUTCAndTimeZone: Provider<DanaRSPacketOptionSetPumpUTCAndTimeZone>
    @Inject lateinit var danaRSPacketOptionSetUserOption: Provider<DanaRSPacketOptionSetUserOption>
    @Inject lateinit var danaRSPacketHistoryAlarm: Provider<DanaRSPacketHistoryAlarm>
    @Inject lateinit var danaRSPacketHistoryBasal: Provider<DanaRSPacketHistoryBasal>
    @Inject lateinit var danaRSPacketHistoryBloodGlucose: Provider<DanaRSPacketHistoryBloodGlucose>
    @Inject lateinit var danaRSPacketHistoryBolus: Provider<DanaRSPacketHistoryBolus>
    @Inject lateinit var danaRSPacketHistoryCarbohydrate: Provider<DanaRSPacketHistoryCarbohydrate>
    @Inject lateinit var danaRSPacketHistoryDaily: Provider<DanaRSPacketHistoryDaily>
    @Inject lateinit var danaRSPacketHistoryPrime: Provider<DanaRSPacketHistoryPrime>
    @Inject lateinit var danaRSPacketHistoryRefill: Provider<DanaRSPacketHistoryRefill>
    @Inject lateinit var danaRSPacketHistorySuspend: Provider<DanaRSPacketHistorySuspend>

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
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
            sendMessage(danaRSPacketEtcKeepConnection.get()) // test encryption for v3 & BLE
            if (!bleComm.isConnected) return
            sendMessage(danaRSPacketGeneralGetShippingInformation.get()) // serial no
            sendMessage(danaRSPacketGeneralGetPumpCheck.get()) // firmware
            sendMessage(danaRSPacketBasalGetProfileNumber.get())
            sendMessage(danaRSPacketBolusGetBolusOption.get()) // isExtendedEnabled
            sendMessage(danaRSPacketBasalGetBasalRate.get()) // basal profile, basalStep, maxBasal
            sendMessage(danaRSPacketBolusGetCalculationInformation.get()) // target
            if (danaPump.profile24) sendMessage(danaRSPacketBolusGet24CIRCFArray.get())
            else sendMessage(danaRSPacketBolusGetCIRCFArray.get())
            sendMessage(danaRSPacketOptionGetUserOption.get()) // Getting user options
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpstatus)))
            sendMessage(danaRSPacketGeneralInitialScreenInformation.get())
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            sendMessage(danaRSPacketBolusGetStepBolusInformation.get()) // last bolus, bolusStep, maxBolus
            danaPump.lastConnection = System.currentTimeMillis()
            val profile = profileFunction.getProfile()
            if (profile != null && abs(danaPump.currentBasal - profile.getBasal()) >= pump.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileSwitchChanged())
                }
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumptime)))
            if (danaPump.usingUTC) sendMessage(danaRSPacketOptionGetPumpUTCAndTimeZone.get())
            else sendMessage(danaRSPacketOptionGetPumpTime.get())
            var timeDiff = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
            if (danaPump.pumpTime == 0L) {
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
            if (bleComm.isConnected && (abs(timeDiff) > 3 || danaPump.usingUTC && offset != danaPump.zoneOffset)) {
                if (abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds - large difference")
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), app.aaps.core.ui.R.raw.error)

                    //de-initialize pump
                    danaPump.reset()
                    rxBus.send(EventDanaRNewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                } else {
                    when {
                        danaPump.usingUTC      -> {
                            sendMessage(danaRSPacketOptionSetPumpUTCAndTimeZone.get().with(dateUtil.now(), offset))
                        }

                        danaPump.protocol >= 5 -> { // can set seconds
                            sendMessage(danaRSPacketOptionSetPumpTime.get().with(dateUtil.now()))
                        }

                        else                   -> {
                            waitForWholeMinute() // Dana can set only whole minute
                            // add 10sec to be sure we are over minute (will be cut off anyway)
                            sendMessage(danaRSPacketOptionSetPumpTime.get().with(dateUtil.now() + T.secs(10).msecs()))
                        }
                    }
                    if (danaPump.usingUTC) sendMessage(danaRSPacketOptionGetPumpUTCAndTimeZone.get())
                    else sendMessage(danaRSPacketOptionGetPumpTime.get())
                    timeDiff = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
                    aapsLogger.debug(LTag.PUMPCOMM, "Pump time difference: $timeDiff seconds")
                }
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.ui.R.string.reading_pump_history)))
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
                    uiInteraction.addNotification(Notification.APPROACHING_DAILY_LIMIT, rh.gs(R.string.approachingdailylimit), Notification.URGENT)
                    pumpSync.insertAnnouncement(
                        rh.gs(R.string.approachingdailylimit) + ": " + danaPump.dailyTotalUnits + "/" + danaPump.maxDailyTotalUnits + "U",
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
            val result = pumpEnactResultProvider.get().success(false)
            result.comment = "pump not initialized"
            return result
        }
        SystemClock.sleep(1000)
        val msg = danaRSPacketAPSHistoryEvents.get().with(danaPump.readHistoryFrom)
        aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + dateUtil.dateAndTimeString(danaPump.readHistoryFrom))
        sendMessage(msg)
        while (!danaPump.historyDoneReceived && bleComm.isConnected) {
            SystemClock.sleep(100)
        }
        danaPump.readHistoryFrom = if (danaPump.lastEventTimeLoaded != 0L) danaPump.lastEventTimeLoaded - T.mins(1).msecs() else 0
        aapsLogger.debug(LTag.PUMPCOMM, "Events loaded")
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpstatus)))
        sendMessage(danaRSPacketGeneralInitialScreenInformation.get())
        danaPump.lastConnection = System.currentTimeMillis()
        return pumpEnactResultProvider.get().success(msg.success())
    }

    fun setUserSettings(): PumpEnactResult {
        val message = danaRSPacketOptionSetUserOption.get()
        sendMessage(message)
        return pumpEnactResultProvider.get().success(message.success())
    }

    fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (BolusProgressData.stopPressed) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.startingbolus)))
        val preferencesSpeed = preferences.get(DanaIntKey.BolusSpeed)
        danaPump.bolusDone = false
        danaPump.bolusingDetailedBolusInfo = detailedBolusInfo
        danaPump.bolusStopped = false
        danaPump.bolusStopForced = false
        danaPump.bolusProgressLastTimeStamp = dateUtil.now()
        val start = danaRSPacketBolusSetStepBolusStart.get().with(detailedBolusInfo.insulin, preferencesSpeed)
        val bolusStart = System.currentTimeMillis()
        var connectionBroken = false
        if (detailedBolusInfo.insulin > 0) {
            if (!danaPump.bolusStopped) {
                sendMessage(start)
            } else {
                BolusProgressData.bolusEnded = true
                return false
            }
            while (!danaPump.bolusStopped && !start.failed && !danaPump.bolusDone && !connectionBroken) {
                SystemClock.sleep(100)
                if (System.currentTimeMillis() - danaPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if i didn't receive status for more than 20 sec expecting broken comm
                    connectionBroken = true
                    aapsLogger.debug(LTag.PUMPCOMM, "Communication stopped")
                    bleComm.disconnect("Communication stopped")
                }
            }
        }
        danaPump.bolusingDetailedBolusInfo = null
        var speed = 12
        when (preferencesSpeed) {
            0 -> speed = 12
            1 -> speed = 30
            2 -> speed = 60
        }
        val bolusDurationInMSec = (detailedBolusInfo.insulin * speed * 1000).toLong()
        val expectedEnd = bolusStart + bolusDurationInMSec + 2000
        while (System.currentTimeMillis() < expectedEnd) {
            val waitTime = expectedEnd - System.currentTimeMillis()
            rxBus.send(EventOverviewBolusProgress(status = rh.gs(R.string.waitingforestimatedbolusend, waitTime / 1000), id = detailedBolusInfo.id))
            SystemClock.sleep(1000)
        }
        // do not call loadEvents() directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                // reread bolus status
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
                sendMessage(danaRSPacketBolusGetStepBolusInformation.get()) // last bolus
                rxBus.send(EventOverviewBolusProgress(status = rh.gs(app.aaps.core.interfaces.R.string.disconnecting), id = detailedBolusInfo.id, percent = 100))
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
            }
        })
        return !start.failed && !connectionBroken
    }

    fun bolusStop() {
        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop >>>>> @${BolusProgressData.delivered}")
        val stop = danaRSPacketBolusSetStepBolusStop.get()
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
        val status = danaRSPacketGeneralInitialScreenInformation.get()
        sendMessage(status)
        if (status.failed) return false
        if (status.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            sendMessage(danaRSPacketBasalSetCancelTemporaryBasal.get())
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        val msgTBR = danaRSPacketBasalSetTemporaryBasal.get().with(percent, durationInHours)
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
        val status = danaRSPacketGeneralInitialScreenInformation.get()
        sendMessage(status)
        if (status.failed) return false
        if (status.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            sendMessage(danaRSPacketBasalSetCancelTemporaryBasal.get())
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        val msgTBR = danaRSPacketAPSBasalSetTemporaryBasal.get().with(percent)
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
        val status = danaRSPacketGeneralInitialScreenInformation.get()
        sendMessage(status)
        if (status.failed) return false
        if (status.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            sendMessage(danaRSPacketBasalSetCancelTemporaryBasal.get())
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        val msgTBR = danaRSPacketAPSBasalSetTemporaryBasal.get().with(percent)
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
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
        val msgCancel = danaRSPacketBasalSetCancelTemporaryBasal.get()
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
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))
        val msgExtended = danaRSPacketBolusSetExtendedBolus.get().with(insulin, durationInHalfHours)
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
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)))
        val msgStop = danaRSPacketBolusSetExtendedBolusCancel.get()
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
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)))
        val basal = danaPump.buildDanaRProfileRecord(profile)
        val msgSet = danaRSPacketBasalSetProfileBasalRate.get().with(0, basal)
        sendMessage(msgSet)
        val msgActivate = danaRSPacketBasalSetProfileNumber.get().with(0)
        sendMessage(msgActivate)
        if (danaPump.profile24) {
            val msgProfile = danaRSPacketBolusSet24CIRCFArray.get().with(profile)
            sendMessage(msgProfile)
        }
        readPumpStatus()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return msgSet.success()
    }

    fun loadHistory(type: Byte): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!isConnected) return result
        var msg: DanaRSPacketHistory? = null
        when (type) {
            RecordTypes.RECORD_TYPE_ALARM     -> msg = danaRSPacketHistoryAlarm.get()
            RecordTypes.RECORD_TYPE_PRIME     -> msg = danaRSPacketHistoryPrime.get()
            RecordTypes.RECORD_TYPE_BASALHOUR -> msg = danaRSPacketHistoryBasal.get()
            RecordTypes.RECORD_TYPE_BOLUS     -> msg = danaRSPacketHistoryBolus.get()
            RecordTypes.RECORD_TYPE_CARBO     -> msg = danaRSPacketHistoryCarbohydrate.get()
            RecordTypes.RECORD_TYPE_DAILY     -> msg = danaRSPacketHistoryDaily.get()
            RecordTypes.RECORD_TYPE_GLUCOSE   -> msg = danaRSPacketHistoryBloodGlucose.get()
            RecordTypes.RECORD_TYPE_REFILL    -> msg = danaRSPacketHistoryRefill.get()
            RecordTypes.RECORD_TYPE_SUSPEND   -> msg = danaRSPacketHistorySuspend.get()
        }
        if (msg != null) {
            sendMessage(danaRSPacketGeneralSetHistoryUploadMode.get().with(1))
            SystemClock.sleep(200)
            sendMessage(msg)
            while (!msg.done && isConnected) {
                SystemClock.sleep(100)
            }
            SystemClock.sleep(200)
            sendMessage(danaRSPacketGeneralSetHistoryUploadMode.get().with(0))
        }
        result.success = msg?.success() == true
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
        return START_STICKY
    }

    private fun waitForWholeMinute() {
        while (true) {
            val time = dateUtil.now()
            val timeToWholeMinute = 60000 - time % 60000
            if (timeToWholeMinute > 59800 || timeToWholeMinute < 300) break
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.waitingfortimesynchronization, (timeToWholeMinute / 1000).toInt())))
            SystemClock.sleep(min(timeToWholeMinute, 100))
        }
    }
}