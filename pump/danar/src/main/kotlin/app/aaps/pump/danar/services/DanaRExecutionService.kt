package app.aaps.pump.danar.services

import android.os.Binder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.BolusProgressData.stopPressed
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.dana.R
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableR
import app.aaps.pump.danar.comm.MsgBolusStart
import app.aaps.pump.danar.comm.MsgBolusStartWithSpeed
import app.aaps.pump.danar.comm.MsgCheckValue
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
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import javax.inject.Inject
import kotlin.math.abs

class DanaRExecutionService : AbstractDanaRExecutionService() {

    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var messageHashTableR: MessageHashTableR
    @Inject lateinit var profileFunction: ProfileFunction

    override fun messageHashTable() = messageHashTableR

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
            val checkValue = MsgCheckValue(injector)
            if (danaPump.isNewPump) {
                mSerialIOThread?.sendMessage(checkValue)
                if (!checkValue.isReceived) {
                    return
                }
            }
            mSerialIOThread?.sendMessage(statusMsg)
            mSerialIOThread?.sendMessage(statusBasicMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingtempbasalstatus)))
            mSerialIOThread?.sendMessage(tempStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingextendedbolusstatus)))
            mSerialIOThread?.sendMessage(exStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            val now = System.currentTimeMillis()
            danaPump.lastConnection = now
            val profile = profileFunction.getProfile()
            if (profile != null && abs(danaPump.currentBasal - profile.getBasal()) >= danaRPlugin.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                mSerialIOThread?.sendMessage(MsgSettingBasal(injector))
                if (!danaRPlugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileSwitchChanged())
                }
            }
            if (danaPump.lastSettingsRead + 60 * 60 * 1000L < now || !danaRPlugin.isInitialized()) {
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
                mSerialIOThread?.sendMessage(MsgSettingProfileRatiosAll(injector))
                mSerialIOThread?.sendMessage(MsgSettingUserOptions(injector))
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
                if (abs(timeDiff) > 10) {
                    mSerialIOThread?.sendMessage(MsgSetTime(injector, dateUtil.now()))
                    mSerialIOThread?.sendMessage(MsgSettingPumpTime(injector))
                    timeDiff = (danaPump.pumpTime - System.currentTimeMillis()) / 1000L
                    aapsLogger.debug(LTag.PUMP, "Pump time difference: $timeDiff seconds")
                }
                danaPump.lastSettingsRead = now
            }
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
            doSanityCheck()
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
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingtempbasal)))
        mSerialIOThread?.sendMessage(MsgSetTempBasalStop(injector))
        mSerialIOThread?.sendMessage(MsgStatusTempBasal(injector))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))
        mSerialIOThread?.sendMessage(MsgSetExtendedBolusStart(injector, insulin, (durationInHalfHours and 0xFF).toByte()))
        mSerialIOThread?.sendMessage(MsgStatusBolusExtended(injector))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.stoppingextendedbolus)))
        mSerialIOThread?.sendMessage(MsgSetExtendedBolusStop(injector))
        mSerialIOThread?.sendMessage(MsgStatusBolusExtended(injector))
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        return true
    }

    override fun loadEvents(): PumpEnactResult = pumpEnactResultProvider.get()

    override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (stopPressed) return false
        danaPump.bolusingDetailedBolusInfo = detailedBolusInfo
        danaPump.bolusDone = false
        val preferencesSpeed = preferences.get(DanaIntKey.BolusSpeed)
        val start: MessageBase =
            if (preferencesSpeed == 0) MsgBolusStart(injector, detailedBolusInfo.insulin)
            else MsgBolusStartWithSpeed(injector, detailedBolusInfo.insulin, preferencesSpeed)
        danaPump.bolusStopped = false
        danaPump.bolusStopForced = false
        if (detailedBolusInfo.insulin > 0) {
            val bolusStart = System.currentTimeMillis()
            if (!danaPump.bolusStopped) {
                mSerialIOThread?.sendMessage(start)
            } else {
                return false
            }
            while (!danaPump.bolusStopped && !start.failed) {
                SystemClock.sleep(100)
                if (System.currentTimeMillis() - danaPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                    danaPump.bolusStopped = true
                    danaPump.bolusStopForced = true
                    aapsLogger.debug(LTag.PUMP, "Communication stopped")
                }
            }
            SystemClock.sleep(300)
            danaPump.bolusingDetailedBolusInfo = null
            var speed = 12
            when (preferencesSpeed) {
                0 -> speed = 12
                1 -> speed = 30
                2 -> speed = 60
            }
            // try to find real amount if bolusing was interrupted or comm failed
            if (BolusProgressData.delivered != detailedBolusInfo.insulin) {
                disconnect("bolusingInterrupted")
                for (i in 0..59) {
                    rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.pump.danar.R.string.waiting_1_minute, i, 60)))
                    SystemClock.sleep(1000)
                }
                val bolusDurationInMSec = (detailedBolusInfo.insulin * speed * 1000).toLong()
                val expectedEnd = bolusStart + bolusDurationInMSec + 3000
                while (System.currentTimeMillis() < expectedEnd) {
                    val waitTime = expectedEnd - System.currentTimeMillis()
                    rxBus.send(EventOverviewBolusProgress(status = rh.gs(R.string.waitingforestimatedbolusend, waitTime / 1000), id = detailedBolusInfo.id))
                    SystemClock.sleep(1000)
                }
                connect()
                for (i in 0..30) {
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, i))
                    if (isConnected) break
                    SystemClock.sleep(1000)
                }
                if (!isConnected) {
                    ToastUtils.errorToast(context, app.aaps.core.ui.R.string.treatmentdeliveryerror)
                    BolusProgressData.delivered = 0.0
                    return false
                }
                mSerialIOThread?.sendMessage(MsgStatus(injector))
                if (danaPump.lastBolusTime > System.currentTimeMillis() - 2 * 60 * 1000L) { // last bolus max 2 min old
                    BolusProgressData.delivered = danaPump.lastBolusAmount
                    aapsLogger.debug(LTag.PUMP, "Used bolus amount from history: " + danaPump.lastBolusAmount)
                } else {
                    BolusProgressData.delivered = 0.0
                    aapsLogger.debug(LTag.PUMP, "Bolus amount in history too old: " + dateUtil.dateAndTimeString(danaPump.lastBolusTime))
                    return false
                }
            } else {
                commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.bolus_ok), null)
            }
        }
        return !start.failed
    }

    override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean = false

    override fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean = false

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

        val serviceInstance: DanaRExecutionService
            get() = this@DanaRExecutionService
    }
}
