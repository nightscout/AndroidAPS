package app.aaps.pump.aaruy.services

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import app.aaps.core.data.model.BS
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
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.keys.AaruyStringKey
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.round

class AaruyService : DaggerService() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var aaruyPlugin: AaruyPlugin
    @Inject lateinit var aaruyPump: AaruyPump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aaruyBle: AaruyBLE
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var historyDailyUnitInfoDao: HistoryDailyUnitInfoDao

    companion object {
        private const val CHECK_EXPIRY_WARNING_TIME_MS = 5 * 60 * 1000L
    }

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default)

    var isConnected: Boolean = false
    var isConnecting: Boolean = false

    override fun onCreate() {
        super.onCreate()
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ stopSelf() }, fabricPrivacy::logException)
        scope.launch {
            aaruyPump.pumpStateFlow.collect { pumpState ->
                // handlePumpStateUpdate(pumpState)
            }
        }
        scope.launch {
            aaruyPump.connectionStateFlow.collect { connectionState ->
                isConnected = connectionState == ConnectionState.CONNECTED
                isConnecting = connectionState == ConnectionState.CONNECTING
                handleConnectionStateChange(connectionState)
            }
        }
        scope.launch {
            aaruyPump.pumpWarningFlow.collect { pumpWarning ->
                notifyPumpWarning(pumpWarning)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
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

    private fun notifyPumpWarning(alarmState: AlarmState) {
        // Notification on pump warning/error
        if (alarmState != AlarmState.NONE) {
            if (AlarmState.isHighAlarm(alarmState) || AlarmState.isLowAlarm(alarmState)) {
                uiInteraction.addNotificationWithSound(
                    Notification.PUMP_ERROR,
                    rh.gs(R.string.aaruy_pump_error, aaruyPump.alarmStateToString(alarmState)),
                    Notification.URGENT,
                    app.aaps.core.ui.R.raw.alarm
                )
            }
            else if (AlarmState.isRemindAlarm(alarmState)) {
                uiInteraction.addNotification(
                    Notification.PUMP_WARNING,
                    rh.gs(R.string.aaruy_pump_warning, aaruyPump.alarmStateToString(alarmState)),
                    Notification.ANNOUNCEMENT,
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

        aaruyPump.lastConnection = System.currentTimeMillis()
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

    fun setBolus(detailedBolusInfo: DetailedBolusInfo): Boolean {
        if (!canSetBolus()) return false

        val insulin = detailedBolusInfo.insulin
        val isSmb = detailedBolusInfo.bolusType == BS.Type.SMB
        aaruyPump.bolusDone = false
        aaruyPump.bolusStopped = false
        BolusProgressData.delivered = 0.0

        if (!sendBolusCommand(insulin, isSmb)) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to set bolus")
            commandQueue.readStatus(rh.gs(R.string.alarm_bolus_error), null) // make sure if anything is delivered (which is highly unlikely at this point) we get it
            aaruyPump.bolusDone = true
            BolusProgressData.delivered = 0.0
            return false
        }

        val bolusStart = System.currentTimeMillis()
        aaruyPump.bolusProgressLastTimeStamp = bolusStart
        aaruyPump.bolusStartTime = bolusStart
        aaruyPump.bolusAmountToBeDelivered = insulin

        detailedBolusInfo.timestamp = bolusStart // Make sure the timestamp is set to the start of the bolus
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        // Sync the initial bolus
        val newRecord = pumpSync.addBolusWithTempId(
            timestamp = detailedBolusInfo.timestamp,
            amount = detailedBolusInfo.insulin,
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

        if (aaruyPump.bolusStopped && BolusProgressData.delivered == 0.0) {
            // In this case we don't get a bolus end event, so need to remove all the stuff added previously
            val syncOk = pumpSync.syncBolusWithTempId(
                timestamp = bolusStart,
                amount = 0.0,
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

        return true
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
        var lastSentBolusAmount: Double? = null

        while (!aaruyPump.bolusStopped && !aaruyPump.bolusDone && !communicationLost) {
            SystemClock.sleep(100)
            if (aaruyPump.bolusProgressLastTimeStamp > checkTime) checkTime = aaruyPump.bolusProgressLastTimeStamp
            if (System.currentTimeMillis() - checkTime > T.secs(20).msecs()) {
                if (connectionRetryCounter < 3) {
                    aapsLogger.warn(LTag.PUMPCOMM, "No bolus progress for 20 seconds, retrying connection")
                    connect("retrying connection", preferences.get(AaruyStringKey.MacAddress))
                    checkTime = System.currentTimeMillis()
                    connectionRetryCounter++
                } else {
                    communicationLost = true
                    aapsLogger.warn(LTag.PUMPCOMM, "Retry connection failed, communication stopped")
                    disconnect("Communication stopped")
                }
            } else {
                val currentBolusAmount = BolusProgressData.delivered

                if (currentBolusAmount != lastSentBolusAmount) {
                    rxBus.send(EventOverviewBolusProgress(rh, BolusProgressData.delivered))
                    lastSentBolusAmount = currentBolusAmount
                }
            }
        }

        val bolusDurationInMSec = (aaruyPump.bolusAmountToBeDelivered/(0.025 * aaruyPump.bolusStep) * 2 * 1000)
        val expectedEnd = aaruyPump.bolusStartTime + bolusDurationInMSec + 1000
        while (System.currentTimeMillis() < expectedEnd && !aaruyPump.bolusDone) {
            val waitTime = expectedEnd - System.currentTimeMillis()
            rxBus.send(EventOverviewBolusProgress(status = rh.gs(R.string.aaruy_wait_bolus_time_tip, waitTime / 1000), id = BolusProgressData.id))
            SystemClock.sleep(1000)
        }

        // Allow time for notification packet with new sequnce number to arrive
        SystemClock.sleep(2000)

        // Do not call update status directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventOverviewBolusProgress(status = rh.gs(app.aaps.core.interfaces.R.string.disconnecting), id = BolusProgressData.id, percent = 100))
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_bolus_status)))
            }
        })

        if (aaruyPump.pumpState.isCanDoRefresh()) {
            aaruyBle.syncAndLoadBasicParam()
        }
    }

    fun stopBolus() {
        if (!isConnected) return

        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop >>>>> @ + ${BolusProgressData.delivered}")
        if (isConnected) {
            var success = aaruyBle.cancelBolus()
            val timeout = System.currentTimeMillis() + T.secs(30).msecs()
            while (!success && System.currentTimeMillis() < timeout) {
                success = aaruyBle.cancelBolus()
            }
            aapsLogger.debug(LTag.PUMPCOMM, "bolusStop success: $success")
            aaruyPump.bolusStopped = true
        } else {
            aaruyPump.bolusStopped = true
        }
    }

    fun setTempBasal(absoluteRate: Double, durationInMinutes: Int): Boolean {
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

        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_temp_basal_status)))
            }
        })

        return result
    }

    fun extendedBolus(insulin: Double, durationInMinutes: Int): Boolean {
        if (!canSetBolus()) return false
        var result = true
        result = aaruyBle.extendedBolus(insulin, durationInMinutes)

        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.settingextendedbolus)))

        val eb = pumpSync.expectedPumpState().extendedBolus
        if (eb != null) {
            aapsLogger.info(LTag.PUMP, "---->extendedBolus - extendedBolusStart: ${eb.timestamp}, " +
                "extendedBolusDuration: ${eb.duration}, extendedBolusRate: ${eb.rate}, extendedBolusAmount: ${eb.amount}")
        }
        aaruyPump.fromExtendedBolus(eb)

        return result
    }

    fun cancelTempBasal(): Boolean {
        if (!isConnected) return false

        val result = aaruyBle.cancelTempBasal()

        val tbr = pumpSync.expectedPumpState().temporaryBasal
        aaruyPump.fromTemporaryBasal(tbr)

        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_temp_basal_status)))
            }
        })

        return result
    }

    fun cancelExtendedBolus(): Boolean {
        if (!isConnected) return false

        val result = aaruyBle.cancelBolus()

        val eb = pumpSync.expectedPumpState().extendedBolus
        if (eb != null) {
            aapsLogger.info(LTag.PUMP, "---->extendedBolus - extendedBolusStart: ${eb.timestamp}, " +
                "extendedBolusDuration: ${eb.duration}, extendedBolusRate: ${eb.rate}, extendedBolusAmount: ${eb.amount}")
        }
        aaruyPump.fromExtendedBolus(eb)

        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_bolus_status)))
            }
        })

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
        if (BolusProgressData.stopPressed) {
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

    fun requestHistoryData() {
        if (!isConnected) return

        aaruyBle.requestHistoryData(aaruyPump.lastHistoryTime)
    }

    fun readDailyData() {
        if (!isConnected) return
        aaruyBle.readDailyData(aaruyPump.lastHistoryDailyTime)
    }
}