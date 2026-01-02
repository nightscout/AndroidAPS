package app.aaps.pump.danarv2.services

import android.os.Binder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T.Companion.mins
import app.aaps.core.data.time.T.Companion.secs
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressData.stopPressed
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
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
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.danarv2.comm.MessageHashTableRv2
import app.aaps.pump.danarv2.comm.MsgCheckValueV2
import app.aaps.pump.danarv2.comm.MsgHistoryEventsV2
import app.aaps.pump.danarv2.comm.MsgSetAPSTempBasalStartV2
import javax.inject.Inject
import kotlin.math.abs

class DanaRv2ExecutionService : AbstractDanaRExecutionService() {

    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var messageHashTableRv2: MessageHashTableRv2
    @Inject lateinit var profileFunction: ProfileFunction

    override fun messageHashTable() = messageHashTableRv2

    override fun onCreate() {
        super.onCreate()
        mBinder = LocalBinder()
    }

    override fun getPumpStatus() {
        try {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpstatus)))
            val statusMsg = MsgStatus(injector)
            val statusBasicMsg = MsgStatusBasic(injector)
            val tempStatusMsg = MsgStatusTempBasal(injector)
            val exStatusMsg = MsgStatusBolusExtended(injector)
            val checkValue = MsgCheckValueV2(injector)
            if (danaPump.isNewPump) {
                mSerialIOThread?.sendMessage(checkValue)
                if (!checkValue.isReceived) {
                    return
                }
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            mSerialIOThread?.sendMessage(statusMsg)
            mSerialIOThread?.sendMessage(statusBasicMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingtempbasalstatus)))
            mSerialIOThread?.sendMessage(tempStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingextendedbolusstatus)))
            mSerialIOThread?.sendMessage(exStatusMsg)
            danaPump.lastConnection = System.currentTimeMillis()
            val profile = profileFunction.getProfile()
            val pump = activePlugin.activePump
            if (profile != null && abs(danaPump.currentBasal - profile.getBasal()) >= pump.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                mSerialIOThread?.sendMessage(MsgSettingBasal(injector))
                if (!pump.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileSwitchChanged())
                }
            }
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumptime)))
            mSerialIOThread?.sendMessage(MsgSettingPumpTime(injector))
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
                    uiInteraction.runAlarm(rh.gs(R.string.largetimediff), rh.gs(R.string.largetimedifftitle), app.aaps.core.ui.R.raw.error)

                    //de-initialize pump
                    danaPump.reset()
                    rxBus.send(EventDanaRNewStatus())
                    rxBus.send(EventInitializationChanged())
                    return
                } else {
                    waitForWholeMinute() // Dana can set only whole minute
                    // add 10sec to be sure we are over minute (will be cut off anyway)
                    mSerialIOThread?.sendMessage(MsgSetTime(injector, dateUtil.now() + secs(10).msecs()))
                    mSerialIOThread?.sendMessage(MsgSettingPumpTime(injector))
                    timeDiff = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: $timeDiff seconds")
                }
            }
            val now = System.currentTimeMillis()
            if (danaPump.lastSettingsRead + 60 * 60 * 1000L < now || !pump.isInitialized()) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                mSerialIOThread?.sendMessage(MsgSettingShippingInfo(injector))
                mSerialIOThread?.sendMessage(MsgSettingActiveProfile(injector))
                mSerialIOThread?.sendMessage(MsgSettingMeal(injector))
                mSerialIOThread?.sendMessage(MsgSettingBasal(injector))
                //0x3201
                mSerialIOThread?.sendMessage(MsgSettingMaxValues(injector))
                mSerialIOThread?.sendMessage(MsgSettingGlucose(injector))
                mSerialIOThread?.sendMessage(MsgSettingActiveProfile(injector))
                mSerialIOThread?.sendMessage(MsgSettingProfileRatios(injector))
                mSerialIOThread?.sendMessage(MsgSettingUserOptions(injector))
                mSerialIOThread?.sendMessage(MsgSettingProfileRatiosAll(injector))
                danaPump.lastSettingsRead = now
            }
            loadEvents()
            rxBus.send(EventDanaRNewStatus())
            rxBus.send(EventInitializationChanged())
            if (danaPump.dailyTotalUnits > danaPump.maxDailyTotalUnits * Constants.dailyLimitWarning) {
                aapsLogger.debug(LTag.PUMP, "Approaching daily limit: " + danaPump.dailyTotalUnits + "/" + danaPump.maxDailyTotalUnits)
                if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                    uiInteraction.addNotification(Notification.APPROACHING_DAILY_LIMIT, rh.gs(R.string.approachingdailylimit), Notification.URGENT)
                    pumpSync.insertAnnouncement(
                        rh.gs(R.string.approachingdailylimit) + ": " + danaPump.dailyTotalUnits + "/" + danaPump.maxDailyTotalUnits + "U",
                        null,
                        PumpType.DANA_R_KOREAN,
                        danaRKoreanPlugin.serialNumber()
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
            mSerialIOThread?.sendMessage(MsgSetTempBasalStop(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        mSerialIOThread?.sendMessage(MsgSetTempBasalStart(injector, percent, durationInHours))
        mSerialIOThread?.sendMessage(MsgStatusTempBasal(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean {
        if (!isConnected) return false
        if (danaPump.isTempBasalInProgress) {
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
            mSerialIOThread?.sendMessage(MsgSetTempBasalStop(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        mSerialIOThread?.sendMessage(MsgSetAPSTempBasalStartV2(injector, percent, durationInMinutes == 15, durationInMinutes == 30))
        mSerialIOThread?.sendMessage(MsgStatusTempBasal(injector))
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
            mSerialIOThread?.sendMessage(MsgSetTempBasalStop(injector))
            SystemClock.sleep(500)
        }
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingtempbasal)))
        mSerialIOThread?.sendMessage(MsgSetAPSTempBasalStartV2(injector, percent, durationInMinutes == 15, durationInMinutes == 30))
        mSerialIOThread?.sendMessage(MsgStatusTempBasal(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
        mSerialIOThread?.sendMessage(MsgSetTempBasalStop(injector))
        mSerialIOThread?.sendMessage(MsgStatusTempBasal(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))
        mSerialIOThread?.sendMessage(MsgSetExtendedBolusStart(injector, insulin, (durationInHalfHours and 0xFF).toByte()))
        mSerialIOThread?.sendMessage(MsgStatusBolusExtended(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)))
        mSerialIOThread?.sendMessage(MsgSetExtendedBolusStop(injector))
        mSerialIOThread?.sendMessage(MsgStatusBolusExtended(injector))
        loadEvents()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (stopPressed) return false
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
                mSerialIOThread?.sendMessage(start)
            } else {
                BolusProgressData.delivered = 0.0
                return false
            }
            while (!danaPump.bolusStopped && !start.failed && !connectionBroken) {
                SystemClock.sleep(100)
                if (System.currentTimeMillis() - danaPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
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
        val expectedEnd = bolusStart + bolusDurationInMSec + 2000
        while (System.currentTimeMillis() < expectedEnd) {
            val waitTime = expectedEnd - System.currentTimeMillis()
            rxBus.send(EventOverviewBolusProgress(status = rh.gs(R.string.waitingforestimatedbolusend, waitTime / 1000), id = detailedBolusInfo.id))
            SystemClock.sleep(1000)
        }
        // do not call loadEvents() directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                // load last bolus status
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
                mSerialIOThread?.sendMessage(MsgStatus(injector))
                rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.interfaces.R.string.disconnecting)))
            }
        })
        return !start.failed && !connectionBroken
    }

    override fun loadEvents(): PumpEnactResult {
        if (!danaRv2Plugin.isInitialized()) {
            val result = pumpEnactResultProvider.get().success(false)
            result.comment("pump not initialized")
            return result
        }
        if (!isConnected) return pumpEnactResultProvider.get().success(false)
        SystemClock.sleep(300)
        val msg = MsgHistoryEventsV2(injector, danaPump.readHistoryFrom)
        aapsLogger.debug(LTag.PUMP, "Loading event history from: " + dateUtil.dateAndTimeString(danaPump.readHistoryFrom))
        mSerialIOThread?.sendMessage(msg)
        while (!danaPump.historyDoneReceived && mRfcommSocket?.isConnected == true) {
            SystemClock.sleep(100)
        }
        SystemClock.sleep(200)
        if (danaPump.lastEventTimeLoaded != 0L) danaPump.readHistoryFrom = danaPump.lastEventTimeLoaded - mins(1).msecs() else danaPump.readHistoryFrom = 0
        danaPump.lastConnection = System.currentTimeMillis()
        return pumpEnactResultProvider.get().success(true)
    }

    override fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)))
        val basal = danaPump.buildDanaRProfileRecord(profile)
        val msgSet = MsgSetBasalProfile(injector, 0.toByte(), basal)
        mSerialIOThread?.sendMessage(msgSet)
        val msgActivate = MsgSetActivateBasalProfile(injector, 0.toByte())
        mSerialIOThread?.sendMessage(msgActivate)
        danaPump.lastSettingsRead = 0 // force read full settings
        getPumpStatus()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun setUserOptions(): PumpEnactResult {
        if (!isConnected) return pumpEnactResultProvider.get().success(false)
        SystemClock.sleep(300)
        val msg = MsgSetUserOptions(injector)
        mSerialIOThread?.sendMessage(msg)
        SystemClock.sleep(200)
        return pumpEnactResultProvider.get().success(!msg.failed)
    }

    inner class LocalBinder : Binder() {

        val serviceInstance: DanaRv2ExecutionService
            get() = this@DanaRv2ExecutionService
    }
}
