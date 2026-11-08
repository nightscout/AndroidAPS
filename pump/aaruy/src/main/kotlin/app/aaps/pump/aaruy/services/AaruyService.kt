package app.aaps.pump.aaruy.services

import app.aaps.core.ui.R as CoreUiR
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.workflow.MetroService
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AlarmState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AaruyService : MetroService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var aaruyPump: AaruyPump
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var bolusProgressData: BolusProgressData
    @Inject lateinit var aaruyBle: AaruyBLE
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var dateUtil: DateUtil

    private val mBinder: IBinder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Room for a pump that is slower than the estimate, and for its clock. */
    private val UNCONFIRMED_BOLUS_MARGIN_MS = 30_000L

    var isConnected: Boolean = false
    var isConnecting: Boolean = false

    override fun onCreate() {
        super.onCreate()
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { stopSelf() }
        // One error in handling a warning must not stop the alarms and the connection handling that follow.
        aaruyPump.connectionStateFlow.collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { connectionState ->
            isConnected = connectionState == ConnectionState.CONNECTED
            isConnecting = connectionState == ConnectionState.CONNECTING
            handleConnectionStateChange(connectionState)
        }
        aaruyPump.pumpWarnings.collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { pumpWarning ->
            notifyPumpWarning(pumpWarning)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun handleConnectionStateChange(connectionState: ConnectionState) {
        aapsLogger.error(LTag.PUMPCOMM, "--------->handleConnectionStateChange $connectionState")
        when (connectionState) {
            ConnectionState.CONNECTED     -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
            ConnectionState.DISCONNECTED  -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            ConnectionState.CONNECTING    -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
            ConnectionState.DISCONNECTING -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
        }
    }

    /**
     * A notification replaces the one with the same id, and its sound. So the errors share one notification that lists all
     * of them: a later error, as a low battery, used to remove an occlusion the user had not seen. "Stopped too long" has
     * its own id, so a later reminder cannot make it a quiet notification.
     */
    internal suspend fun notifyPumpWarning(alarmState: AlarmState) {
        // Notification on pump warning/error
        if (alarmState != AlarmState.NONE) {
            if (AlarmState.isHighAlarm(alarmState) || AlarmState.isLowAlarm(alarmState)) {
                // The copy, not the set: the pump thread changes the set while this runs.
                val errors = aaruyPump.activeAlarmsFlow.value + alarmState
                notificationManager.post(
                    NotificationId.PUMP_ERROR,
                    rh.gs(R.string.aaruy_pump_error, errors.joinToString("\n") { aaruyPump.alarmStateToString(it) }),
                    sound = AlarmSound.ALARM
                )
            }
            else if (alarmState == AlarmState.PUMP_STOP_TOO_LONG) {
                // Seen on a real pump: after 15 minutes stopped it beeps once and never again. No insulin goes in, so AAPS rings.
                notificationManager.post(
                    NotificationId.PUMP_SUSPENDED,
                    rh.gs(R.string.aaruy_pump_warning, aaruyPump.alarmStateToString(alarmState)),
                    level = NotificationLevel.URGENT,
                    sound = AlarmSound.ALARM
                )
            }
            else if (AlarmState.isRemindAlarm(alarmState)) {
                notificationManager.post(
                    NotificationId.PUMP_WARNING,
                    rh.gs(R.string.aaruy_pump_warning, aaruyPump.alarmStateToString(alarmState))
                )
            }
            pumpSync.insertAnnouncement(
                aaruyPump.alarmStateToString(alarmState),
                null,
                aaruyPump.pumpType(),
                aaruyPump.pumpSN
            )
        }
    }

    fun connect(from: String, address: String): Boolean {
        return aaruyBle.connect(from, address)
    }

    fun stopConnecting() {
        aaruyBle.stopConnecting()
    }

    fun disconnect(from: String) {
        aaruyBle.disconnect(from)
    }

    fun readPumpStatus() {
        if (!isConnected) return

        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_pump_status)))
        // Check if there is active bolus but it is not being monitored
        // if so wait for bolus and show progress
        if (!aaruyPump.bolusDone ) {
            val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(aaruyPump.bolusStartTime, aaruyPump.bolusAmountToBeDelivered)
            if (detailedBolusInfo != null) {
                detailedBolusInfoStorage.add(detailedBolusInfo) // Reinsert
            }
            if (detailedBolusInfo?.bolusType == BS.Type.SMB) {
                rxBus.send(EventPumpStatusChanged(rh.gs(CoreUiR.string.smb_bolus_u, detailedBolusInfo.insulin)))
            } else {
                rxBus.send(EventPumpStatusChanged(rh.gs(CoreUiR.string.bolus_u_min, detailedBolusInfo?.insulin ?: 0.0)))
            }
            waitForBolusProgress()
            // Lost during the bolus: a new status read is asked, and this one has no link to read with.
            if (!isConnected) return
        }

        aaruyBle.syncPumpStatus()

        if (aaruyPump.pumpState.isCanDoRefresh()) {
            aaruyBle.syncAndLoadBasicParam()
            aaruyBle.syncCurTimeToPump()
        }

        // Once for a pump, for the overview. The old screen read it at each connect, outside the queue, and the move to
        // Compose lost that, so the firmware was never shown.
        if (aaruyPump.swVersion.isEmpty()) {
            aaruyPump.swVersion = aaruyBle.getPumpVersionNum()
            aapsLogger.debug(LTag.PUMPCOMM, "Pump firmware: ${aaruyPump.swVersion}")
        }

        // The pump log can hold what no status shows, as an alarm while the link was down.
        if (aaruyPump.canSaveHistory) aaruyBle.requestHistoryData(aaruyPump.lastHistoryTime)
        aaruyBle.waitForHistoryAfterReconnect()
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
        if (!isConnected || !aaruyPump.pumpState.isCanDoRefresh()) {
            aapsLogger.debug(LTag.PUMPCOMM, "updateBasalsInPump_aaruy isConnected is $isConnected, isCanDoRefresh" +
                " is ${aaruyPump.pumpState.isCanDoRefresh()}")
            return false
        }

        val basalInfo = aaruyPump.buildAaruyProfileRecord(profile)

        aapsLogger.debug(LTag.PUMPCOMM, "updateBasalsInPump_aaruy")
        return aaruyBle.updateBasalsInPump(basalInfo)
    }

    suspend fun setBolus(detailedBolusInfo: DetailedBolusInfo): AaruyBolusOutcome {
        if (!canSetBolus()) return AaruyBolusOutcome.NOT_STARTED
        // While it runs, its command shows the progress. After it, a bolus the pump still runs is shown by the driver.
        aaruyPump.bolusCommandRunning = true
        try {
            return deliverBolus(detailedBolusInfo)
        } finally {
            aaruyPump.bolusCommandRunning = false
        }
    }

    private suspend fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): AaruyBolusOutcome {
        val insulin = detailedBolusInfo.insulin
        val isSmb = detailedBolusInfo.bolusType == BS.Type.SMB
        aaruyPump.bolusDone = false
        aaruyPump.bolusEndNotSeen = false
        aaruyPump.bolusLinkLost = false
        aaruyPump.bolusStopRequested = false
        aaruyPump.bolusStopped = false
        aaruyPump.bolusDelivered = 0.0

        val start = sendBolusCommand(insulin, isSmb)
        if (start != AaruyBolusStart.STARTED) {
            aapsLogger.error(LTag.PUMPCOMM, "Bolus not reported by the pump: $start")
            // The status and the history tell what the pump did. Not waited for: this bolus runs in the queue.
            scope.launch { commandQueue.readStatus(rh.gs(R.string.alarm_bolus_error)) }
            // A status 3 that comes later sets this back, so no second bolus can start while the pump delivers.
            aaruyPump.bolusDone = true
            aaruyPump.bolusDelivered = 0.0
            if (start == AaruyBolusStart.NOT_SENT) return AaruyBolusOutcome.NOT_STARTED
        }

        val bolusStart = dateUtil.now()
        aaruyPump.bolusProgressLastTimeStamp = bolusStart
        aaruyPump.bolusStartTime = bolusStart
        aaruyPump.bolusAmountToBeDelivered = insulin

        detailedBolusInfo.timestamp = bolusStart // Make sure the timestamp is set to the start of the bolus
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        // Sync the initial bolus
        val newRecord = pumpSync.addBolusWithTempId(
            timestamp = detailedBolusInfo.timestamp,
            amount = PumpInsulin(detailedBolusInfo.insulin),
            temporaryId = detailedBolusInfo.timestamp,
            type = detailedBolusInfo.bolusType,
            pumpType = aaruyPump.pumpType(),
            pumpSerial = aaruyPump.pumpSN
        )
        if (newRecord) {
            aapsLogger.debug(
                LTag.PUMPCOMM,
                "set bolus: **NEW** EVENT BOLUS (tempId) ${dateUtil.dateAndTimeString(detailedBolusInfo.timestamp)} (${detailedBolusInfo.timestamp}) Bolus: ${detailedBolusInfo.insulin}U "
            )
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Bolus with tempId ${detailedBolusInfo.timestamp} already exists")
        }
        // The whole amount counts until the history gives the real one: the user must not bolus again.
        if (start == AaruyBolusStart.NO_ANSWER) {
            expectBolusInLog(bolusStart, insulin)
            return AaruyBolusOutcome.UNKNOWN
        }

        waitForBolusProgress()

        // A bolus whose end was not seen may have given everything: its record keeps the amount until the log has the real one.
        if (aaruyPump.bolusStopped && aaruyPump.bolusDelivered == 0.0 && !aaruyPump.bolusEndNotSeen) {
            // In this case we don't get a bolus end event, so need to remove all the stuff added previously
            val syncOk = pumpSync.syncBolusWithTempId(
                timestamp = bolusStart,
                amount = PumpInsulin(0.0),
                temporaryId = bolusStart,
                type = detailedBolusInfo.bolusType,
                pumpId = bolusStart,
                pumpType = aaruyPump.pumpType(),
                pumpSerial = aaruyPump.pumpSN
            )
            aapsLogger.debug(
                LTag.PUMPCOMM,
                "set bolus: **SYNC** EVENT BOLUS (tempId) ${dateUtil.dateAndTimeString(detailedBolusInfo.timestamp)} (${bolusStart}) Bolus: ${0.0}U SyncOK: $syncOk"
            )
            // remove detailed bolus info
            detailedBolusInfoStorage.findDetailedBolusInfo(bolusStart, detailedBolusInfo.insulin)
        }

        return when {
            // First: a stop sent just after a reconnect took the first status of the new link as its answer.
            aaruyPump.bolusEndNotSeen                             -> AaruyBolusOutcome.UNKNOWN
            aaruyPump.bolusStopped                                -> AaruyBolusOutcome.FINISHED
            !aaruyPump.bolusDone                                  -> AaruyBolusOutcome.UNKNOWN
            // Seen on a real pump with a blocked line: "done" after 0.55 U of 3 U. Half a step leaves room for rounding.
            aaruyPump.bolusDelivered < insulin - aaruyPump.bolusStepUnits / 2 -> AaruyBolusOutcome.CUT_SHORT
            else                                                  -> AaruyBolusOutcome.FINISHED
        }
    }

    /**
     * No status showed this start, so the pump may never have had it. A bolus is logged when it ends: once this one would
     * have ended, a status read reads the log from before the start, and the log tells. See [AaruyPump.unconfirmedBolus].
     */
    private fun expectBolusInLog(start: Long, amount: Double) {
        val decideAfter = aaruyPump.estimatedBolusEnd(start, amount) + UNCONFIRMED_BOLUS_MARGIN_MS
        aaruyPump.unconfirmedBolus = AaruyPump.UnconfirmedBolus(start, amount, decideAfter, aaruyBle.logIdBeforeStart)
        aapsLogger.debug(LTag.PUMPCOMM, "Unconfirmed bolus start $start of $amount U: the pump log decides after $decideAfter")
        scope.launch {
            delay(decideAfter - dateUtil.now())
            commandQueue.readStatus(rh.gs(R.string.alarm_bolus_error))
        }
    }

    private fun sendBolusCommand(insulin: Double, isSmb: Boolean = false): AaruyBolusStart {
        return if (insulin > 0) {
            aaruyBle.setBolus(insulin, isSmb)
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Bolus not set, insulin: $insulin")
            AaruyBolusStart.NOT_SENT
        }
    }

    /**
     * Follows the bolus until the pump ends it. Without a link it ends at once: the command queue owns the link, and the
     * status read asked here connects again. That read shows the bolus again with its Stop, as after an app restart.
     */
    private fun waitForBolusProgress() {
        var silentRetries = 0
        var checkTime = aaruyPump.bolusProgressLastTimeStamp

        while (!aaruyPump.bolusStopped && !aaruyPump.bolusDone) {
            if (!isConnected) {
                aapsLogger.warn(LTag.PUMPCOMM, "The link was lost during the bolus")
                readStatusAfterLostBolus()
                return
            }
            SystemClock.sleep(100)
            if (aaruyPump.bolusProgressLastTimeStamp > checkTime) {
                checkTime = aaruyPump.bolusProgressLastTimeStamp
                silentRetries = 0
            }
            if (dateUtil.now() - checkTime > T.secs(20).msecs()) {
                if (silentRetries < 3) {
                    aapsLogger.warn(LTag.PUMPCOMM, "No bolus progress for 20 seconds")
                    aaruyBle.syncPumpStatus()
                    checkTime = dateUtil.now()
                    silentRetries++
                } else {
                    aapsLogger.warn(LTag.PUMPCOMM, "The pump does not answer, communication stopped")
                    disconnect("Communication stopped")
                    readStatusAfterLostBolus()
                    return
                }
            }
        }

        val expectedEnd = aaruyPump.estimatedBolusEnd(aaruyPump.bolusStartTime, aaruyPump.bolusAmountToBeDelivered) + 1000
        while (dateUtil.now() < expectedEnd && !aaruyPump.bolusDone) {
            val waitTime = expectedEnd - dateUtil.now()
            bolusProgressData.updateProgress(
                percent = (aaruyPump.bolusDelivered / aaruyPump.bolusAmountToBeDelivered * 100).toInt().coerceAtMost(99),
                status = TextRef.Literal(rh.gs(R.string.aaruy_wait_bolus_time_tip, (waitTime / 1000).toInt())),
                delivered = PumpInsulin(aaruyPump.bolusDelivered)
            )
            SystemClock.sleep(1000)
        }

        // The pump may have ended the bolus without a message that was seen here.
        if (isConnected) aaruyBle.syncPumpStatus()

        // Allow time for notification packet with new sequnce number to arrive
        SystemClock.sleep(2000)

        if (aaruyPump.pumpState.isCanDoRefresh()) {
            aaruyBle.syncAndLoadBasicParam()
        }
    }

    /** Not waited for: it runs after the command that asks for it. The pump has no screen, so its Stop must come back soon. */
    private fun readStatusAfterLostBolus() {
        scope.launch { commandQueue.readStatus(rh.gs(R.string.alarm_bolus_error)) }
    }

    fun stopBolus() {
        if (!isConnected) {
            aapsLogger.warn(LTag.PUMPCOMM, "bolusStop not possible, no connection")
            return
        }

        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop >>>>> @ + ${aaruyPump.bolusDelivered}")
        aaruyPump.bolusStopRequested = true
        var success = aaruyBle.cancelBolus()
        val timeout = dateUtil.now() + T.secs(30).msecs()
        while (!success && isConnected && dateUtil.now() < timeout) {
            success = aaruyBle.cancelBolus()
        }
        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop success: $success")
        // A stop the pump did not confirm stops nothing: the pump can still be delivering.
        aaruyPump.bolusStopped = success
    }

    suspend fun setTempBasal(absoluteRate: Double, durationInMinutes: Int): Boolean {
        if (!isConnected || !aaruyPump.pumpState.isCanTmpBasal()) return false
        // The log first. Dana writes its records only from its log, so the end of the old temp basal always comes before the new
        // one. Here the status of the new one writes its record, which cuts the running record at its start. When the log has an
        // earlier end for that one, as after a lost link, it must come before: the database takes one end only.
        aaruyBle.waitForHistoryAfterReconnect()
        var result = true
        // The pump takes a new temp basal only from the plain basal. What it runs now is the status, not the database.
        if (aaruyPump.pumpState == AaruyPumpState.TMPBASALRATE_RUNNING) {
            result = aaruyBle.cancelTempBasal()
        }
        if (result) result = aaruyBle.setTempBasal(absoluteRate, durationInMinutes)
        val tbr = pumpSync.expectedPumpState().temporaryBasal
        if (tbr != null) {
            aapsLogger.info(LTag.PUMP, "---->setTempBasal - tempBasalStart: ${tbr.timestamp}, " +
                "tempBasalDuration: ${tbr.duration}, tempBasalAbsoluteRate: ${tbr.rate}")
        }
        aaruyPump.fromTemporaryBasal(tbr)

        return result
    }

    suspend fun cancelTempBasal(): Boolean {
        if (!isConnected) return false

        // The pump ended it already, as during a lost link. Its record waits for the log, which has the real end: the
        // client would send nothing, and each cancel of the loop would fail until then.
        val noTempBasalOnPump = aaruyPump.pumpState == AaruyPumpState.BASALRATE_RUNNING || aaruyPump.pumpState.isSuspendedByPump()
        if (noTempBasalOnPump) aaruyBle.waitForHistoryAfterReconnect()
        val result = noTempBasalOnPump || aaruyBle.cancelTempBasal()

        val tbr = pumpSync.expectedPumpState().temporaryBasal
        aaruyPump.fromTemporaryBasal(tbr)

        return result
    }

    private fun canSetBolus(): Boolean {
        if (!isConnected) {
            aapsLogger.warn(LTag.PUMPCOMM, "Pump not connected, not setting bolus")
            return false
        }
        if (bolusProgressData.isStopPressed) {
            aapsLogger.warn(LTag.PUMPCOMM, "Bolus stop pressed, not setting bolus")
            return false
        }
        if (!aaruyPump.bolusDone) {
            aapsLogger.warn(LTag.PUMPCOMM, "Bolus already in progress, not setting new one")
            return false
        }
        if (!aaruyPump.pumpState.isCanBolus()) {
            aapsLogger.warn(LTag.PUMPCOMM, "Pump cannot run bolus, please run basal first")
            return false
        }
        return true
    }

    /** Service stuff */
    inner class LocalBinder : Binder() {

        val serviceInstance: AaruyService
            get() = this@AaruyService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }
}