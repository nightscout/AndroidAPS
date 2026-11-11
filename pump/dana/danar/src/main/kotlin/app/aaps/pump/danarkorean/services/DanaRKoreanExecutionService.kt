package app.aaps.pump.danarkorean.services

import android.os.Binder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.dana.R
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.danar.BolusInProgress
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.comm.MsgBolusStart
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStart
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStop
import app.aaps.pump.danar.comm.MsgSetSingleBasalProfile
import app.aaps.pump.danar.comm.MsgSetTempBasalStart
import app.aaps.pump.danar.comm.MsgSetTempBasalStop
import app.aaps.pump.danar.comm.MsgSetTime
import app.aaps.pump.danar.comm.MsgSettingBasal
import app.aaps.pump.danar.comm.MsgSettingGlucose
import app.aaps.pump.danar.comm.MsgSettingMaxValues
import app.aaps.pump.danar.comm.MsgSettingMeal
import app.aaps.pump.danar.comm.MsgSettingProfileRatios
import app.aaps.pump.danar.comm.MsgSettingPumpTime
import app.aaps.pump.danar.comm.MsgSettingShippingInfo
import app.aaps.pump.danar.comm.MsgStatusBolusExtended
import app.aaps.pump.danar.comm.MsgStatusTempBasal
import app.aaps.pump.danar.services.AbstractDanaRExecutionService
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarkorean.comm.MessageHashTableRKorean
import app.aaps.pump.danarkorean.comm.MsgCheckValueK
import app.aaps.pump.danarkorean.comm.MsgSettingBasal_k
import app.aaps.pump.danarkorean.comm.MsgStatusBasic_k
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlin.math.abs

class DanaRKoreanExecutionService : AbstractDanaRExecutionService() {

    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var bolusInProgress: BolusInProgress
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var messageHashTableRKorean: MessageHashTableRKorean

    override fun messageHashTable() = messageHashTableRKorean

    override fun onCreate() {
        super.onCreate()
        mBinder = LocalBinder()
    }

    override suspend fun getPumpStatus() {
        try {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpstatus)))
            //MsgStatus_k statusMsg = new MsgStatus_k();
            val statusBasicMsg = MsgStatusBasic_k(injector)
            val tempStatusMsg = MsgStatusTempBasal(injector)
            val exStatusMsg = MsgStatusBolusExtended(injector)
            val checkValue = MsgCheckValueK(injector)
            if (danaPump.isNewPump) {
                sendToPump(checkValue)
                if (!checkValue.isReceived) {
                    return
                }
            }

            //mSerialIOThread.sendMessage(statusMsg);
            sendToPump(statusBasicMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingtempbasalstatus)))
            sendToPump(tempStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingextendedbolusstatus)))
            sendToPump(exStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            val now = System.currentTimeMillis()
            danaPump.lastConnection = now
            val profile = pumpSync.expectedPumpState().profile
            if (profile != null && abs(danaPump.currentBasal - profile.getBasal()) >= danaRKoreanPlugin.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                sendToPump(MsgSettingBasal(injector))
                if (!danaRKoreanPlugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileChangeRequested())
                }
            }
            if (danaPump.lastSettingsRead + 60 * 60 * 1000L < now || !danaRKoreanPlugin.isInitialized()) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                sendToPump(MsgSettingShippingInfo(injector))
                sendToPump(MsgSettingMeal(injector))
                sendToPump(MsgSettingBasal_k(injector))
                //0x3201
                sendToPump(MsgSettingMaxValues(injector))
                sendToPump(MsgSettingGlucose(injector))
                sendToPump(MsgSettingProfileRatios(injector))
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumptime)))
                sendToPump(MsgSettingPumpTime(injector))
                if (danaPump.pumpTime == 0L) {
                    // initial handshake was not successful
                    // de-initialize pump
                    danaPump.reset()
                    rxBus.send(EventDanaRNewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                }
                var timeDiff: Long = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
                aapsLogger.debug(LTag.PUMP, "Pump time difference: $timeDiff seconds")
                if (abs(timeDiff) > 10) {
                    waitForWholeMinute() // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cut off anyway)
                    sendToPump(MsgSetTime(injector, dateUtil.now() + T.secs(10).msecs()))
                    sendToPump(MsgSettingPumpTime(injector))
                    timeDiff = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: $timeDiff seconds")
                }
                danaPump.lastSettingsRead = now
            }
            rxBus.send(EventDanaRNewStatus())
            rxBus.send(EventInitializationChanged())
            if (danaPump.dailyTotalUnits > danaPump.maxDailyTotalUnits * Constants.DAILY_RESERVOIR_LIMIT_WARNING) {
                aapsLogger.debug(LTag.PUMP, "Approaching daily limit: " + danaPump.dailyTotalUnits + "/" + danaPump.maxDailyTotalUnits)
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    notificationManager.post(NotificationId.APPROACHING_DAILY_LIMIT, TextRef.AndroidRes(R.string.approachingdailylimit))
                    pumpSync.insertAnnouncement(
                        rh.gs(app.aaps.core.interfaces.R.string.confirmation_line, rh.gs(R.string.approachingdailylimit), rh.gs(app.aaps.core.interfaces.R.string.values_with_slash, danaPump.dailyTotalUnits.toString(), rh.gs(app.aaps.core.ui.R.string.units_format_insulin_int, danaPump.maxDailyTotalUnits))),
                        null,
                        PumpType.DANA_R_KOREAN,
                        danaRKoreanPlugin.serialNumber()
                    )
                    lastApproachingDailyLimit = System.currentTimeMillis()
                }
            }
            // Here, after the settings read: right after an app start it brings the serial number the record needs.
            if (statusBasicMsg.isReceived) bolusInProgress.recordWhole(PumpType.DANA_R_KOREAN, danaRKoreanPlugin.serialNumber())
            doSanityCheck()
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    override fun tempBasal(percent: Int, durationInHours: Int): Boolean {
        if (!isConnected) return false
        if (danaPump.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            sendToPump(MsgSetTempBasalStop(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        sendToPump(MsgSetTempBasalStart(injector, percent, durationInHours))
        sendToPump(MsgStatusTempBasal(injector))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
        sendToPump(MsgSetTempBasalStop(injector))
        sendToPump(MsgStatusTempBasal(injector))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))
        sendToPump(MsgSetExtendedBolusStart(injector, insulin, (durationInHalfHours and 0xFF).toByte()))
        sendToPump(MsgStatusBolusExtended(injector))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)))
        sendToPump(MsgSetExtendedBolusStop(injector))
        sendToPump(MsgStatusBolusExtended(injector))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun loadEvents(): PumpEnactResult? {
        return null
    }

    override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (bolusProgressData.isStopPressed) return false
        danaPump.bolusingDetailedBolusInfo = detailedBolusInfo
        danaPump.bolusDone = false
        val start = MsgBolusStart(injector, detailedBolusInfo.insulin)
        danaPump.bolusStopped = false
        danaPump.bolusStopForced = false
        var connectionBroken = false
        if (detailedBolusInfo.insulin > 0) {
            danaPump.bolusingDetailedBolusInfo = detailedBolusInfo
            if (!danaPump.bolusStopped) {
                sendToPump(start)
            } else {
                bolusProgressData.updateProgress(bolusProgressData.state.value?.percent ?: 0, bolusProgressData.state.value?.status ?: TextRef.Literal(""), PumpInsulin(0.0))
                return false
            }
            // Arm the 15s comm watchdog from bolus start (was never initialised → a stale timestamp
            // made the check below fire on the first iteration, falsely aborting the bolus).
            danaPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
            while (!danaPump.bolusStopped && !start.failed && !connectionBroken) {
                SystemClock.sleep(100)
                if (System.currentTimeMillis() - danaPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                    connectionBroken = true
                    aapsLogger.debug(LTag.PUMP, "Communication stopped")
                }
            }
            SystemClock.sleep(300)
            danaPump.bolusingDetailedBolusInfo = null
            // The Korean DanaR has no read of what it gave. When the pump may have gone on without the link, the whole
            // amount counts: more counted than given is the safe side. Counting only the progress seen made IOB too low.
            val seen = bolusProgressData.state.value?.delivered?.cU ?: 0.0
            val pumpTookStart = (start.isReceived && !start.failed) || seen > 0
            if (connectionBroken && pumpTookStart) {
                // A DanaR goes on with a bolus when the link breaks: a real one gave all of 1 U after a break at 0.15 U
                // (2026-11-10).
                bolusProgressData.updateProgress(PumpInsulin(detailedBolusInfo.insulin))
            } else if (danaPump.bolusStopForced && pumpTookStart) {
                // A real Korean DanaR gave what its last progress showed when it answered a stop: twice, 0.3 U and 0.4 U
                // as in its history, once with a progress that came after the stop was sent (2026-11-10). A stop the pump
                // did not answer may not have reached it, and then the pump goes on.
                val counted = if (danaPump.bolusDone) seen else detailedBolusInfo.insulin
                bolusProgressData.updateProgress(bolusProgressData.state.value?.percent ?: 0, bolusProgressData.state.value?.status ?: TextRef.Literal(""), PumpInsulin(counted))
            }
            // Queue-worker deadlock guard — don't unwrap the .launch. See CommandQueue kdoc.
            appScope.launch { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.bolus_ok)) }
        }
        return !start.failed && !connectionBroken
    }

    override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean = false

    override fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean = false

    override suspend fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)))
        val basal: Array<Double> = danaPump.buildDanaRProfileRecord(profile)
        val set = sendToPump(MsgSetSingleBasalProfile(injector, basal))
        danaPump.lastSettingsRead = 0 // force read full settings
        getPumpStatus()
        // Notifications are handled centrally from this Boolean via AbstractDanaRPlugin + onProfileChanged.
        val given = pumpGivesBasal(set, MsgSettingBasal_k(injector), basal)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return given
    }

    override fun setUserOptions(): PumpEnactResult? = null

    inner class LocalBinder : Binder() {

        val serviceInstance: DanaRKoreanExecutionService
            get() = this@DanaRKoreanExecutionService
    }
}
