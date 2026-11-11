package app.aaps.pump.danar.services

import android.os.Binder
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.pump.defs.PumpType
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
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.events.EventDanaRNewStatus
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.danar.BolusInProgress
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
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlin.math.abs

class DanaRExecutionService : AbstractDanaRExecutionService() {

    @Inject lateinit var danaRPlugin: DanaRPlugin
    @Inject lateinit var bolusInProgress: BolusInProgress
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var messageHashTableR: MessageHashTableR

    override fun messageHashTable() = messageHashTableR

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
            val checkValue = MsgCheckValue(injector)
            if (danaPump.isNewPump) {
                sendToPump(checkValue)
                if (!checkValue.isReceived) {
                    return
                }
            }
            sendToPump(statusMsg)
            sendToPump(statusBasicMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingtempbasalstatus)))
            sendToPump(tempStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingextendedbolusstatus)))
            sendToPump(exStatusMsg)
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingbolusstatus)))
            val now = System.currentTimeMillis()
            danaPump.lastConnection = now
            val profile = pumpSync.expectedPumpState().profile
            if (profile != null && abs(danaPump.currentBasal - profile.getBasal()) >= danaRPlugin.pumpDescription.basalStep) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.gettingpumpsettings)))
                sendToPump(MsgSettingBasal(injector))
                if (!danaRPlugin.isThisProfileSet(profile) && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                    rxBus.send(EventProfileChangeRequested())
                }
            }
            if (danaPump.lastSettingsRead + 60 * 60 * 1000L < now || !danaRPlugin.isInitialized()) {
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
                sendToPump(MsgSettingProfileRatiosAll(injector))
                sendToPump(MsgSettingUserOptions(injector))
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
                if (abs(timeDiff) > 10) {
                    sendToPump(MsgSetTime(injector, dateUtil.now()))
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
                        danaRPlugin.model(),
                        danaRPlugin.serialNumber()
                    )
                    lastApproachingDailyLimit = System.currentTimeMillis()
                }
            }
            // Here, after the settings read: right after an app start it brings the serial number the record needs.
            if (statusMsg.isReceived) bolusInProgress.recordFromPump(danaPump.lastBolusTime, danaPump.lastBolusAmount, PumpType.DANA_R, danaRPlugin.serialNumber())
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

    override fun loadEvents(): PumpEnactResult = pumpEnactResultProvider()

    override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!isConnected) return false
        if (bolusProgressData.isStopPressed) return false
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
                sendToPump(start)
            } else {
                return false
            }
            // Arm the 15s comm watchdog from bolus start. It was never initialised here, so a stale
            // bolusProgressLastTimeStamp (from a previous bolus / epoch) made the check below fire on
            // the first iteration → false "Communication stopped" aborting the bolus. Newer drivers
            // (DanaRSService, MedtrumService, DiaconnG8Service) already do this.
            danaPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
            while (!danaPump.bolusStopped && !start.failed) {
                SystemClock.sleep(100)
                if (System.currentTimeMillis() - danaPump.bolusProgressLastTimeStamp > 15 * 1000L) { // if I didn't receive status for more than 15 sec expecting broken comm
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
            if ((bolusProgressData.state.value?.delivered?.cU ?: 0.0) != detailedBolusInfo.insulin) {
                if (danaPump.bolusDone && isConnected) {
                    // The pump ended the bolus or answered the stop, and the link is up: it gives no more insulin. Its
                    // status has the amount once it has logged the bolus. This waited more than 60 s for a new link, as
                    // after a broken one. A real pump gave one more step after the last progress it sent.
                    SystemClock.sleep(DanaPump.BOLUS_LOGGED_AFTER_END)
                } else {
                    disconnect("bolusingInterrupted")
                    for (i in 0..59) {
                        rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.pump.danar.R.string.waiting_1_minute, i, 60)))
                        SystemClock.sleep(1000)
                    }
                    val bolusDurationInMSec = (detailedBolusInfo.insulin * speed * 1000).toLong()
                    val expectedEnd = bolusStart + bolusDurationInMSec + 3000
                    while (System.currentTimeMillis() < expectedEnd) {
                        val waitTime = expectedEnd - System.currentTimeMillis()
                        val currentPercent = bolusProgressData.state.value?.percent ?: 0
                        bolusProgressData.updateProgress(currentPercent, TextRef.AndroidRes(R.string.waitingforestimatedbolusend, listOf(waitTime / 1000)))
                        SystemClock.sleep(1000)
                    }
                    // One connect() is one try. A real DanaR paged after a broken link answered only after about 1.5
                    // min and three tries, and a single try then recorded the bolus as 0 U though the pump gave it all.
                    for (i in 0..RECONNECT_SECONDS) {
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, i))
                        if (isConnected) break
                        connect() // a new try once the last one has failed, a running one is left alone
                        SystemClock.sleep(1000)
                    }
                    if (!isConnected) {
                        rxBus.send(EventShowSnackbar(rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), EventShowSnackbar.Type.Error))
                        // The pump took the start and goes on without the link (a real one gave all of 1 U after a break
                        // at 0.15 U), so the whole amount counts. It counted 0 U, and IOB was too low.
                        val pumpTookStart = (start.isReceived && !start.failed) || (bolusProgressData.state.value?.delivered?.cU ?: 0.0) > 0
                        val counted = if (pumpTookStart) detailedBolusInfo.insulin else 0.0
                        bolusProgressData.updateProgress(bolusProgressData.state.value?.percent ?: 0, bolusProgressData.state.value?.status ?: TextRef.Literal(""), PumpInsulin(counted))
                        return false
                    }
                }
                sendToPump(MsgStatus(injector))
                val lastBolusTime = danaPump.lastBolusTime
                if (lastBolusTime != null && lastBolusTime >= bolusStart - LAST_BOLUS_BEFORE_START) {
                    val lastAmount = danaPump.lastBolusAmount ?: 0.0
                    bolusProgressData.updateProgress(bolusProgressData.state.value?.percent ?: 0, bolusProgressData.state.value?.status ?: TextRef.Literal(""), PumpInsulin(lastAmount))
                    aapsLogger.debug(LTag.PUMP, "Used bolus amount from history: " + danaPump.lastBolusAmount)
                } else {
                    bolusProgressData.updateProgress(bolusProgressData.state.value?.percent ?: 0, bolusProgressData.state.value?.status ?: TextRef.Literal(""), PumpInsulin(0.0))
                    aapsLogger.debug(LTag.PUMP, "Bolus amount in history too old: " + dateUtil.dateAndTimeStringNullable(lastBolusTime))
                    return false
                }
            } else {
                // Queue-worker deadlock guard — don't unwrap the .launch. See CommandQueue kdoc.
                appScope.launch { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.bolus_ok)) }
            }
        }
        return !start.failed
    }

    override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean = false

    override fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean = false

    override suspend fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected) return false
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.updatingbasalrates)))
        val basal = danaPump.buildDanaRProfileRecord(profile)
        val set = sendToPump(MsgSetBasalProfile(injector, 0.toByte(), basal))
        // Also after a write with no answer: the pump may have taken it.
        val activated = sendToPump(MsgSetActivateBasalProfile(injector, 0.toByte()))
        danaPump.lastSettingsRead = 0 // force read full settings
        getPumpStatus()
        // Notifications are handled centrally from this Boolean via AbstractDanaRPlugin + CommandQueueImplementation.onProfileChanged.
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

        val serviceInstance: DanaRExecutionService
            get() = this@DanaRExecutionService
    }

    companion object {

        /**
         * How much earlier than the start of this bolus the pump's last bolus may be and still be this bolus. The pump
         * gives that time in whole minutes, and the driver keeps the pump clock within 10 s of the phone. The rule was
         * "at most 2 minutes before the read", but the read comes more than a minute after the start, so a bolus that
         * started late in a minute was taken as an old one and recorded as 0 U.
         */
        private const val LAST_BOLUS_BEFORE_START = 70_000L

        /** How long the driver tries to reach the pump again after a broken bolus link, to read what it gave. */
        private const val RECONNECT_SECONDS = 180
    }
}
