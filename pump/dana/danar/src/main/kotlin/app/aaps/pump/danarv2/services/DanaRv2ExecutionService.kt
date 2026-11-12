package app.aaps.pump.danarv2.services

import android.os.Binder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T.Companion.mins
import app.aaps.core.data.time.T.Companion.secs
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
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
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.dana.DanaPump
import app.aaps.core.interfaces.pump.PumpSuspendSync
import app.aaps.pump.dana.R
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MsgBolusStart
import app.aaps.pump.danar.comm.MsgBolusStartWithSpeed
import app.aaps.pump.danar.comm.MsgSetActivateBasalProfile
import app.aaps.pump.danar.comm.MsgSetBasalProfile
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStart
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStop
import app.aaps.pump.danar.comm.MsgSetTempBasalStart
import app.aaps.pump.danar.comm.MsgSetTempBasalStop
import app.aaps.pump.danar.comm.MsgSetTime
import app.aaps.pump.danar.comm.MsgSetUserOptions
import app.aaps.pump.danar.comm.MsgSettingActiveProfile
import app.aaps.pump.danar.comm.MsgSettingBasal
import app.aaps.pump.danar.comm.MsgSettingGlucose
import app.aaps.pump.danar.comm.MsgSettingMaxValues
import app.aaps.pump.danar.comm.MsgSettingMeal
import app.aaps.pump.danar.comm.MsgSettingProfileRatios
import app.aaps.pump.danar.comm.MsgSettingProfileRatiosAll
import app.aaps.pump.danar.comm.MsgSettingPumpTime
import app.aaps.pump.danar.comm.MsgSettingShippingInfo
import app.aaps.pump.danar.comm.MsgSettingUserOptions
import app.aaps.pump.danar.comm.MsgStatus
import app.aaps.pump.danar.comm.MsgStatusBasic
import app.aaps.pump.danar.comm.MsgStatusBolusExtended
import app.aaps.pump.danar.comm.MsgStatusTempBasal
import app.aaps.pump.danar.services.AbstractDanaRExecutionService
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.danarv2.comm.MessageHashTableRv2
import app.aaps.pump.danarv2.comm.MsgCheckValueV2
import app.aaps.pump.danarv2.comm.MsgHistoryEventsV2
import app.aaps.pump.danarv2.comm.MsgSetAPSTempBasalStartV2
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlin.math.abs

class DanaRv2ExecutionService : AbstractDanaRExecutionService() {

    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var messageHashTableRv2: MessageHashTableRv2

    override fun messageHashTable() = messageHashTableRv2

    override fun onCreate() {
        super.onCreate()
        mBinder = LocalBinder()
    }

    override suspend fun getPumpStatus() {
        try {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpstatus)))
            val statusMsg = MsgStatus(injector)
            val statusBasicMsg = MsgStatusBasic(injector)
            val tempStatusMsg = MsgStatusTempBasal(injector)
            val exStatusMsg = MsgStatusBolusExtended(injector)
            val checkValue = MsgCheckValueV2(injector)
            if (danaPump.isNewPump) {
                sendToPump(checkValue)
                if (!checkValue.isReceived) {
                    return
                }
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            sendToPump(statusMsg)
            sendToPump(statusBasicMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingtempbasalstatus)))
            sendToPump(tempStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingextendedbolusstatus)))
            sendToPump(exStatusMsg)
            danaPump.lastConnection = System.currentTimeMillis()
            val profile = pumpSync.expectedPumpState().profile
            val pump = activePlugin.activePump
            if (profile != null && abs(danaPump.currentBasal - profile.getBasal()) >= pump.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                sendToPump(MsgSettingBasal(injector))
                if (!danaRv2Plugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileChangeRequested())
                }
            }
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
            var timeDiff = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
            aapsLogger.debug(LTag.PUMP, "Pump time difference: $timeDiff seconds")
            if (abs(timeDiff) > 3) {
                if (abs(timeDiff) > 60 * 60 * 1.5) {
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: $timeDiff seconds - large difference")
                    //If time-diff is very large, warn user until we can synchronize history readings properly
                    uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), AlarmSound.ERROR)

                    //de-initialize pump
                    danaPump.reset()
                    rxBus.send(EventDanaRNewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                } else {
                    waitForWholeMinute() // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cut off anyway)
                    sendToPump(MsgSetTime(injector, dateUtil.now() + secs(10).msecs()))
                    sendToPump(MsgSettingPumpTime(injector))
                    timeDiff = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: $timeDiff seconds")
                }
            }
            val now = System.currentTimeMillis()
            if (danaPump.lastSettingsRead + 60 * 60 * 1000L < now || !pump.isInitialized()) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                sendToPump(MsgSettingShippingInfo(injector))
                sendToPump(MsgSettingActiveProfile(injector))
                sendToPump(MsgSettingMeal(injector))
                sendToPump(MsgSettingBasal(injector))
                //0x3201
                sendToPump(MsgSettingMaxValues(injector))
                sendToPump(MsgSettingGlucose(injector))
                sendToPump(MsgSettingActiveProfile(injector))
                sendToPump(MsgSettingProfileRatios(injector))
                sendToPump(MsgSettingUserOptions(injector))
                sendToPump(MsgSettingProfileRatiosAll(injector))
                danaPump.lastSettingsRead = now
            }
            // The history has the exact times of a stop of the pump; the status only ends what the history did not
            if (loadEvents().success)
                PumpSuspendSync.followStatus(pumpSync, aapsLogger, danaPump.pumpSuspended, dateUtil.now(), danaRv2Plugin.model(), danaRv2Plugin.serialNumber())
            rxBus.send(EventDanaRNewStatus())
            rxBus.send(EventInitializationChanged())
            if (danaPump.dailyTotalUnits > danaPump.maxDailyTotalUnits * Constants.DAILY_RESERVOIR_LIMIT_WARNING) {
                aapsLogger.debug(LTag.PUMP, "Approaching daily limit: " + danaPump.dailyTotalUnits + "/" + danaPump.maxDailyTotalUnits)
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    notificationManager.post(NotificationId.APPROACHING_DAILY_LIMIT, TextRef.AndroidRes(R.string.approachingdailylimit))
                    pumpSync.insertAnnouncement(
                        rh.gs(app.aaps.core.interfaces.R.string.confirmation_line, rh.gs(R.string.approachingdailylimit), rh.gs(app.aaps.core.interfaces.R.string.values_with_slash, danaPump.dailyTotalUnits.toString(), rh.gs(app.aaps.core.ui.R.string.units_format_insulin_int, danaPump.maxDailyTotalUnits))),
                        null,
                        danaRv2Plugin.model(),
                        danaRv2Plugin.serialNumber()
                    )
                    lastApproachingDailyLimit = System.currentTimeMillis()
                }
            }
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
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean {
        if (!isConnected) return false
        if (danaPump.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            sendToPump(MsgSetTempBasalStop(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        sendToPump(MsgSetAPSTempBasalStartV2(injector, percent, durationInMinutes == 15, durationInMinutes == 30))
        sendToPump(MsgStatusTempBasal(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean {
        if (durationInMinutes != 15 && durationInMinutes != 30) {
            aapsLogger.error("Wrong duration param")
            return false
        }
        if (!isConnected) return false
        if (danaPump.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            sendToPump(MsgSetTempBasalStop(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        sendToPump(MsgSetAPSTempBasalStartV2(injector, percent, durationInMinutes == 15, durationInMinutes == 30))
        sendToPump(MsgStatusTempBasal(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
        sendToPump(MsgSetTempBasalStop(injector))
        sendToPump(MsgStatusTempBasal(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))
        sendToPump(MsgSetExtendedBolusStart(injector, insulin, (durationInHalfHours and 0xFF).toByte()))
        sendToPump(MsgStatusBolusExtended(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)))
        sendToPump(MsgSetExtendedBolusStop(injector))
        sendToPump(MsgStatusBolusExtended(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (bolusProgressData.isStopPressed) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.startingbolus)))
        danaPump.bolusingDetailedBolusInfo = detailedBolusInfo
        danaPump.bolusDone = false
        val preferencesSpeed = preferences.get(DanaIntKey.BolusSpeed)
        val start: MessageBase =
            if (preferencesSpeed == 0) MsgBolusStart(injector, detailedBolusInfo.insulin)
            else MsgBolusStartWithSpeed(injector, detailedBolusInfo.insulin, preferencesSpeed)
        danaPump.bolusStopped = false
        danaPump.bolusStopForced = false
        val bolusStart = System.currentTimeMillis()
        var connectionBroken = false
        if (detailedBolusInfo.insulin > 0) {
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
                if (System.currentTimeMillis() - danaPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if I didn't receive status for more than 15 sec expecting broken comm
                    connectionBroken = true
                    aapsLogger.error("Communication stopped")
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
        // The pump sent the end of the bolus (MsgBolusStop, also the answer to a stop), so it gives no more insulin: wait
        // only for its history record. Only a bolus whose end was not seen waits until the whole amount must have been
        // given. A stopped bolus waited for that too, about 18 s after a stop at 0.45 of 2 U on a real DanaRv2.
        val expectedEnd =
            if (danaPump.bolusDone && !connectionBroken) System.currentTimeMillis() + DanaPump.BOLUS_LOGGED_AFTER_END
            else bolusStart + bolusDurationInMSec + 2000
        while (System.currentTimeMillis() < expectedEnd) {
            val waitTime = expectedEnd - System.currentTimeMillis()
            bolusProgressData.updateProgress(bolusProgressData.state.value?.percent ?: 0, TextRef.AndroidRes(R.string.waitingforestimatedbolusend, listOf(waitTime / 1000)), bolusProgressData.state.value?.delivered ?: PumpInsulin(0.0))
            SystemClock.sleep(1000)
        }
        // do not call loadEvents() directly, reconnection may be needed
        appScope.launch {
            commandQueue.loadEvents()
            // load last bolus status
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            sendToPump(MsgStatus(injector))
            rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.interfaces.R.string.disconnecting)))
        }
        return !start.failed && !connectionBroken
    }

    override fun loadEvents(): PumpEnactResult {
        if (!danaRv2Plugin.isInitialized()) {
            val result = pumpEnactResultProvider().success(false)
            result.comment("pump not initialized")
            return result
        }
        if (!isConnected) return pumpEnactResultProvider().success(false)
        SystemClock.sleep(300)
        val msg = MsgHistoryEventsV2(injector, danaPump.readHistoryFrom)
        aapsLogger.debug(LTag.PUMP, "Loading event history from: " + dateUtil.dateAndTimeString(danaPump.readHistoryFrom))
        sendToPump(msg)
        val completed = waitForHistoryDone()
        SystemClock.sleep(200)
        if (danaPump.lastEventTimeLoaded != 0L) danaPump.readHistoryFrom = danaPump.lastEventTimeLoaded - mins(1).msecs() else danaPump.readHistoryFrom = 0
        danaPump.lastConnection = System.currentTimeMillis()
        return pumpEnactResultProvider().success(completed)
    }

    override suspend fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)))
        val basal = danaPump.buildDanaRProfileRecord(profile)
        val set = sendToPump(MsgSetBasalProfile(injector, 0.toByte(), basal))
        // Also after a write with no answer: the pump may have taken it.
        val activated = sendToPump(MsgSetActivateBasalProfile(injector, 0.toByte()))
        danaPump.lastSettingsRead = 0 // force read full settings
        getPumpStatus()
        // Notifications are handled centrally from this Boolean via AbstractDanaRPlugin + onProfileChanged.
        val given = pumpGivesBasal(set && activated, MsgSettingBasal(injector), basal)
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return given
    }

    override fun setUserOptions(): PumpEnactResult {
        if (!isConnected) return pumpEnactResultProvider().success(false)
        SystemClock.sleep(300)
        val done = sendToPump(MsgSetUserOptions(injector))
        SystemClock.sleep(200)
        return pumpEnactResultProvider().success(done)
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: DanaRv2ExecutionService
            get() = this@DanaRv2ExecutionService
    }
}
