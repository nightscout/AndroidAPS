package app.aaps.pump.aaruy.services

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
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.workflow.MetroService
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.keys.AaruyStringKey
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.round

class AaruyService : MetroService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
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
        aaruyPump.pumpWarningFlow.collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { pumpWarning ->
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

    private suspend fun notifyPumpWarning(alarmState: AlarmState) {
        // Notification on pump warning/error
        if (alarmState != AlarmState.NONE) {
            if (AlarmState.isHighAlarm(alarmState) || AlarmState.isLowAlarm(alarmState)) {
                notificationManager.post(
                    NotificationId.PUMP_ERROR,
                    rh.gs(R.string.aaruy_pump_error, aaruyPump.alarmStateToString(alarmState)),
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
                rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.ui.R.string.smb_bolus_u, detailedBolusInfo.insulin)))
            } else {
                rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.ui.R.string.bolus_u_min, detailedBolusInfo?.insulin ?: 0.0)))
            }
            waitForBolusProgress()
        }

        aaruyBle.syncPumpStatus()

        if (aaruyPump.pumpState.isCanDoRefresh()) {
            aaruyBle.syncAndLoadBasicParam()
            aaruyBle.syncCurTimeToPump()
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

        val insulin = detailedBolusInfo.insulin
        val isSmb = detailedBolusInfo.bolusType == BS.Type.SMB
        aaruyPump.bolusDone = false
        aaruyPump.bolusEndNotSeen = false
        aaruyPump.bolusStopRequested = false
        aaruyPump.bolusStopped = false
        aaruyPump.bolusDelivered = 0.0

        if (!sendBolusCommand(insulin, isSmb)) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to set bolus")
            // Make sure if anything is delivered (which is highly unlikely at this point) we get it. Not waited for: this bolus runs in the queue.
            scope.launch { commandQueue.readStatus(rh.gs(R.string.alarm_bolus_error)) }
            aaruyPump.bolusDone = true
            aaruyPump.bolusDelivered = 0.0
            return AaruyBolusOutcome.NOT_STARTED
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

        waitForBolusProgress()

        if (aaruyPump.bolusStopped && aaruyPump.bolusDelivered == 0.0) {
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

        return if (aaruyPump.bolusStopped || (aaruyPump.bolusDone && !aaruyPump.bolusEndNotSeen)) AaruyBolusOutcome.FINISHED
        else AaruyBolusOutcome.UNKNOWN
    }

    private fun sendBolusCommand(insulin: Double, isSmb: Boolean = false): Boolean {
        return if (insulin > 0) {
            aaruyBle.setBolus(insulin, isSmb)
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Bolus not set, insulin: $insulin")
            false
        }
    }

    private fun waitForBolusProgress() {
        var communicationLost = false
        var connectionRetryCounter = 0
        var checkTime = aaruyPump.bolusProgressLastTimeStamp

        while (!aaruyPump.bolusStopped && !aaruyPump.bolusDone && !communicationLost) {
            SystemClock.sleep(100)
            if (aaruyPump.bolusProgressLastTimeStamp > checkTime) {
                checkTime = aaruyPump.bolusProgressLastTimeStamp
                connectionRetryCounter = 0
            }
            if (dateUtil.now() - checkTime > T.secs(20).msecs()) {
                if (connectionRetryCounter < 3) {
                    aapsLogger.warn(LTag.PUMPCOMM, "No bolus progress for 20 seconds, connected: $isConnected")
                    if (isConnected) aaruyBle.syncPumpStatus()
                    else connect("retrying connection", preferences.get(AaruyStringKey.MacAddress))
                    checkTime = dateUtil.now()
                    connectionRetryCounter++
                } else {
                    communicationLost = true
                    aapsLogger.warn(LTag.PUMPCOMM, "Retry connection failed, communication stopped")
                    disconnect("Communication stopped")
                }
            }
        }

        val bolusDurationInMSec = (aaruyPump.bolusAmountToBeDelivered/(0.025 * aaruyPump.bolusStep) * 2 * 1000)
        val expectedEnd = aaruyPump.bolusStartTime + bolusDurationInMSec + 1000
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
        var result = true
        if (aaruyPump.isTempBasalInProgress) {
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

        val result = aaruyBle.cancelTempBasal()

        val tbr = pumpSync.expectedPumpState().temporaryBasal
        aaruyPump.fromTemporaryBasal(tbr)

        return result
    }

    fun replaceDevice():Boolean {
        if (!isConnected) return false

        aaruyBle.replaceDevice()
        return true
    }

    fun pushForward():Boolean {
        if (!isConnected) return false

        aaruyBle.pushForward()
        return true
    }

    fun needlePushAir(softNeedleType: Byte):Boolean {
        if (!isConnected) return false

        aaruyBle.needlePushAir(softNeedleType)
        return true
    }

    fun startBaseInject():Boolean {
        if (!isConnected) return false

        return aaruyBle.startBaseInject()
    }

    fun unbindDevice() {
        aaruyBle.unbindDevice()
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

    fun clearAlarms(): Boolean {
        // loadHistory

        // reset alarm
        aaruyBle.clearAlarmStatus(AlarmState.PUMP_LOW_POWER.state)
        aaruyBle.clearAlarmStatus(AlarmState.PUMP_MOTOR_BUSY.state)
        aaruyPump.clearAlarmState()
        return true
    }

    fun getPumpVersionNum(): String {
        aaruyPump.swVersion = aaruyBle.getPumpVersionNum()
        aapsLogger.warn(LTag.PUMPCOMM, "getPumpVersionNum swVersion is ${aaruyPump.swVersion}")
        return aaruyPump.swVersion
    }

    fun readDailyData() {
        if (!isConnected) return
        aaruyBle.readDailyData(aaruyPump.lastHistoryDailyTime)
    }
}