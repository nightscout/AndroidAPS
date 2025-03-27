package app.aaps.pump.medtrum.services

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
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.ConnectionState
import app.aaps.pump.medtrum.comm.enums.AlarmState
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.comm.packets.ActivatePacket
import app.aaps.pump.medtrum.comm.packets.AuthorizePacket
import app.aaps.pump.medtrum.comm.packets.CancelBolusPacket
import app.aaps.pump.medtrum.comm.packets.CancelTempBasalPacket
import app.aaps.pump.medtrum.comm.packets.ClearPumpAlarmPacket
import app.aaps.pump.medtrum.comm.packets.GetDeviceTypePacket
import app.aaps.pump.medtrum.comm.packets.GetRecordPacket
import app.aaps.pump.medtrum.comm.packets.GetTimePacket
import app.aaps.pump.medtrum.comm.packets.MedtrumPacket
import app.aaps.pump.medtrum.comm.packets.NotificationPacket
import app.aaps.pump.medtrum.comm.packets.PrimePacket
import app.aaps.pump.medtrum.comm.packets.ResumePumpPacket
import app.aaps.pump.medtrum.comm.packets.SetBasalProfilePacket
import app.aaps.pump.medtrum.comm.packets.SetBolusPacket
import app.aaps.pump.medtrum.comm.packets.SetPatchPacket
import app.aaps.pump.medtrum.comm.packets.SetTempBasalPacket
import app.aaps.pump.medtrum.comm.packets.SetTimePacket
import app.aaps.pump.medtrum.comm.packets.SetTimeZonePacket
import app.aaps.pump.medtrum.comm.packets.StopPatchPacket
import app.aaps.pump.medtrum.comm.packets.SubscribePacket
import app.aaps.pump.medtrum.comm.packets.SynchronizePacket
import app.aaps.pump.medtrum.keys.MedtrumBooleanKey
import app.aaps.pump.medtrum.keys.MedtrumIntKey
import app.aaps.pump.medtrum.keys.MedtrumStringKey
import app.aaps.pump.medtrum.util.MedtrumSnUtil
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.round

class MedtrumService : DaggerService(), BLECommCallback {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var medtrumPlugin: MedtrumPlugin
    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var bleComm: BLEComm
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Inject lateinit var dateUtil: DateUtil

    companion object {

        private const val COMMAND_DEFAULT_TIMEOUT_SEC: Long = 60
        private const val COMMAND_SYNC_TIMEOUT_SEC: Long = 120
        private const val COMMAND_CONNECTING_TIMEOUT_SEC: Long = 30
        private const val ALARM_HOURLY_MAX_CLEAR_CODE = 4
        private const val ALARM_DAILY_MAX_CLEAR_CODE = 5

        private const val CHECK_EXPIRY_WARNING_TIME_MS = 5 * 60 * 1000L
    }

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()

    private var currentState: State = IdleState()
    private var mPacket: MedtrumPacket? = null

    private val scope = CoroutineScope(Dispatchers.Default)

    val isConnected: Boolean
        get() = medtrumPump.connectionState == ConnectionState.CONNECTED
    val isConnecting: Boolean
        get() = medtrumPump.connectionState == ConnectionState.CONNECTING

    override fun onCreate() {
        super.onCreate()
        bleComm.setCallback(this)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ stopSelf() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(MedtrumStringKey.MedtrumSnInput.key)) {
                               aapsLogger.debug(LTag.PUMPCOMM, "Serial number changed, reporting new pump!")
                               medtrumPump.loadUserSettingsFromSP()
                               medtrumPump.deviceType = MedtrumSnUtil().getDeviceTypeFromSerial(medtrumPump.pumpSN).value
                               medtrumPump.resetPatchParameters()
                               pumpSync.connectNewPump()
                               medtrumPump.setFakeTBRIfNotSet()
                           }
                           if (event.isChanged(MedtrumBooleanKey.MedtrumWarningNotification.key)
                               || event.isChanged(MedtrumIntKey.MedtrumPumpExpiryWarningHours.key)
                           ) {
                               medtrumPump.loadUserSettingsFromSP()
                           }
                           if (event.isChanged(MedtrumStringKey.MedtrumAlarmSettings.key)
                               || event.isChanged(MedtrumBooleanKey.MedtrumPatchExpiration.key)
                               || event.isChanged(MedtrumIntKey.MedtrumHourlyMaxInsulin.key)
                               || event.isChanged(MedtrumIntKey.MedtrumDailyMaxInsulin.key)
                           ) {
                               medtrumPump.loadUserSettingsFromSP()
                               commandQueue.setUserOptions(object : Callback() {
                                   override fun run() {
                                       if (medtrumPlugin.isInitialized() && !this.result.success) {
                                           uiInteraction.addNotification(
                                               Notification.PUMP_SETTINGS_FAILED,
                                               rh.gs(R.string.pump_setting_failed),
                                               Notification.NORMAL,
                                           )
                                       }
                                   }
                               })
                           }
                       }, fabricPrivacy::logException)
        scope.launch {
            medtrumPump.pumpStateFlow.collect { pumpState ->
                handlePumpStateUpdate(pumpState)
            }
        }
        scope.launch {
            medtrumPump.connectionStateFlow.collect { connectionState ->
                handleConnectionStateChange(connectionState)
            }
        }
        scope.launch {
            medtrumPump.pumpWarningFlow.collect { pumpWarning ->
                notifyPumpWarning(pumpWarning)
            }
        }
        scope.launch {
            while (true) {
                checkExpiryWarning()
                kotlinx.coroutines.delay(CHECK_EXPIRY_WARNING_TIME_MS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        scope.cancel()
    }

    fun connect(from: String): Boolean {
        aapsLogger.debug(LTag.PUMP, "connect: called from: $from")
        return when (currentState) {
            is IdleState  -> {
                medtrumPump.connectionState = ConnectionState.CONNECTING
                bleComm.connect(from, medtrumPump.pumpSN)
            }

            is ReadyState -> {
                aapsLogger.error(LTag.PUMPCOMM, "Connect attempt when in ReadyState from: $from")
                return if (isConnected) {
                    aapsLogger.debug(LTag.PUMP, "connect: already connected")
                    true
                } else {
                    aapsLogger.debug(LTag.PUMP, "connect: not connected, resetting state and trying to connect")
                    toState(IdleState())
                    medtrumPump.connectionState = ConnectionState.CONNECTING
                    bleComm.connect(from, medtrumPump.pumpSN)
                }
            }

            else          -> {
                aapsLogger.error(LTag.PUMPCOMM, "Connect attempt when in state: $currentState from: $from")
                false
            }
        }
    }

    fun startPrime(): Boolean {
        return sendPacketAndGetResponse(PrimePacket(injector))
    }

    fun startActivate(): Boolean {
        val profile = profileFunction.getProfile()?.let { medtrumPump.buildMedtrumProfileArray(it) }
        val packet = profile?.let { ActivatePacket(injector, it) }
        return packet?.let { sendPacketAndGetResponse(it) } == true
    }

    fun deactivatePatch(): Boolean {
        var result = true
        if (medtrumPump.tempBasalInProgress) {
            result = sendPacketAndGetResponse(CancelTempBasalPacket(injector))
        }
        // Make sure we have all events of this patch if possible
        loadEvents()
        if (result) result = sendPacketAndGetResponse(StopPatchPacket(injector))
        return result
    }

    fun stopConnecting() {
        bleComm.disconnect("stopConnecting")
    }

    fun disconnect(from: String) {
        medtrumPump.connectionState = ConnectionState.DISCONNECTING
        bleComm.disconnect(from)
    }

    fun readPumpStatus() {
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_pump_status)))
        updateTimeIfNeeded(false)
        // Check if there is active bolus but it is not being monitored
        // if so wait for bolus and show progress
        if (!medtrumPump.bolusDone && medtrumPump.bolusingTreatment == null) {
            val detailedBolusInfo = detailedBolusInfoStorage.findDetailedBolusInfo(medtrumPump.bolusStartTime, medtrumPump.bolusAmountToBeDelivered)
            if (detailedBolusInfo != null) {
                detailedBolusInfoStorage.add(detailedBolusInfo) // Reinsert
            }
            medtrumPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, detailedBolusInfo?.bolusType == BS.Type.SMB, detailedBolusInfo?.id ?: 0)
            if (detailedBolusInfo?.bolusType == BS.Type.SMB) {
                rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.ui.R.string.smb_bolus_u, detailedBolusInfo.insulin)))
            } else {
                rxBus.send(EventPumpStatusChanged(rh.gs(app.aaps.core.ui.R.string.bolus_u_min, detailedBolusInfo?.insulin ?: 0.0)))
            }
            waitForBolusProgress()
        }
        loadEvents()
    }

    fun timeUpdateNotification(updateSuccess: Boolean) {
        if (updateSuccess) {
            aapsLogger.debug(LTag.PUMPCOMM, "Pump time updated")
            uiInteraction.addNotification(
                Notification.INSIGHT_DATE_TIME_UPDATED, // :---)
                rh.gs(app.aaps.core.ui.R.string.pump_time_updated),
                Notification.INFO,
            )
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to update pump time")
            uiInteraction.addNotification(
                Notification.PUMP_TIMEZONE_UPDATE_FAILED,
                rh.gs(R.string.pump_time_update_failed),
                Notification.URGENT,
            )
        }
    }

    fun updateTimeIfNeeded(needLoadHistory: Boolean = true): Boolean {
        // Note we only check timeZone here, time is updated each connection attempt if needed, because the pump requires it to be checked
        // But we don't check timeZone each time, therefore we do it here (if needed)
        var result = true
        if (medtrumPump.pumpTimeZoneOffset != dateUtil.getTimeZoneOffsetMinutes(dateUtil.now())) {
            result = sendPacketAndGetResponse(SetTimePacket(injector))
            if (result) result = sendPacketAndGetResponse(SetTimeZonePacket(injector))
            timeUpdateNotification(result)
        }
        // Do this here, because TBR can be cancelled due to time change by connect flow
        if (needLoadHistory && result) {
            result = loadEvents()
        }
        if (result) medtrumPump.needCheckTimeUpdate = false
        return result
    }

    fun loadEvents(): Boolean {
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_pump_status)))
        // Sync records
        val result = syncRecords()
        if (result) {
            aapsLogger.debug(LTag.PUMPCOMM, "Events loaded")
            medtrumPump.lastConnection = System.currentTimeMillis()
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to load events")
        }
        // Update ui text to getting pump status (indicating syncing records is finished)
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_pump_status)))
        return result
    }

    fun clearAlarms(): Boolean {
        var result = loadEvents() // Make sure we have all events before clearing alarms
        if (result && medtrumPump.pumpState.isSuspendedByPump()) {
            when (medtrumPump.pumpState) {
                MedtrumPumpState.HOURLY_MAX_SUSPENDED -> {
                    result = sendPacketAndGetResponse(ClearPumpAlarmPacket(injector, ALARM_HOURLY_MAX_CLEAR_CODE))
                }

                MedtrumPumpState.DAILY_MAX_SUSPENDED  -> {
                    result = sendPacketAndGetResponse(ClearPumpAlarmPacket(injector, ALARM_DAILY_MAX_CLEAR_CODE))
                }

                else                                  -> {
                    // Nothing to reset
                }
            }
            // Resume suspended pump
            if (result) result = sendPacketAndGetResponse(ResumePumpPacket(injector))
            if (result) medtrumPump.clearAlarmState()
        }
        return result
    }

    fun setUserSettings(): Boolean {
        rxBus.send(EventPumpStatusChanged(rh.gs(R.string.setting_pump_settings)))
        return sendPacketAndGetResponse(SetPatchPacket(injector))
    }

    fun setBolus(detailedBolusInfo: DetailedBolusInfo, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!canSetBolus()) return false

        val insulin = detailedBolusInfo.insulin
        medtrumPump.bolusDone = false
        medtrumPump.bolusStopped = false
        medtrumPump.bolusErrorReason = null

        if (!sendBolusCommand(insulin)) {
            medtrumPump.bolusErrorReason = rh.gs(R.string.bolus_error_reason_unable_to_send_command)
            aapsLogger.error(LTag.PUMPCOMM, "Failed to set bolus")
            commandQueue.readStatus(rh.gs(R.string.bolus_error), null) // make sure if anything is delivered (which is highly unlikely at this point) we get it
            medtrumPump.bolusDone = true
            t.insulin = 0.0
            return false
        }

        val bolusStart = System.currentTimeMillis()
        medtrumPump.bolusProgressLastTimeStamp = bolusStart
        medtrumPump.bolusStartTime = bolusStart
        medtrumPump.bolusingTreatment = t
        medtrumPump.bolusAmountToBeDelivered = insulin

        detailedBolusInfo.timestamp = bolusStart // Make sure the timestamp is set to the start of the bolus
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        // Sync the initial bolus
        val newRecord = pumpSync.addBolusWithTempId(
            timestamp = detailedBolusInfo.timestamp,
            amount = detailedBolusInfo.insulin,
            temporaryId = detailedBolusInfo.timestamp,
            type = detailedBolusInfo.bolusType,
            pumpType = medtrumPump.pumpType(),
            pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
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

        if (medtrumPump.bolusStopped && t.insulin == 0.0) {
            // In this case we don't get a bolus end event, so need to remove all the stuff added previously
            val syncOk = pumpSync.syncBolusWithTempId(
                timestamp = bolusStart,
                amount = 0.0,
                temporaryId = bolusStart,
                type = detailedBolusInfo.bolusType,
                pumpId = bolusStart,
                pumpType = medtrumPump.pumpType(),
                pumpSerial = medtrumPump.pumpSN.toString(radix = 16)
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

    private fun canSetBolus(): Boolean {
        if (!isConnected) {
            aapsLogger.warn(LTag.PUMPCOMM, "Pump not connected, not setting bolus")
            medtrumPump.bolusErrorReason = rh.gs(R.string.bolus_error_reason_not_connected)
            return false
        }
        if (BolusProgressData.stopPressed) {
            aapsLogger.warn(LTag.PUMPCOMM, "Bolus stop pressed, not setting bolus")
            medtrumPump.bolusErrorReason = rh.gs(R.string.bolus_error_reason_user)
            return false
        }
        if (!medtrumPump.bolusDone) {
            aapsLogger.warn(LTag.PUMPCOMM, "Bolus already in progress, not setting new one")
            medtrumPump.bolusErrorReason = rh.gs(R.string.bolus_error_reason_already_in_progress)
            return false
        }
        return true
    }

    private fun sendBolusCommand(insulin: Double): Boolean {
        return if (insulin > 0) {
            sendPacketAndGetResponse(SetBolusPacket(injector, insulin))
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Bolus not set, insulin: $insulin")
            false
        }
    }

    private fun waitForBolusProgress() {
        val bolusingEvent = EventOverviewBolusProgress
        var communicationLost = false
        var connectionRetryCounter = 0
        var checkTime = medtrumPump.bolusProgressLastTimeStamp
        var lastSentBolusAmount: Double? = null

        while (!medtrumPump.bolusStopped && !medtrumPump.bolusDone && !communicationLost) {
            SystemClock.sleep(100)
            if (medtrumPump.bolusProgressLastTimeStamp > checkTime) checkTime = medtrumPump.bolusProgressLastTimeStamp
            if (System.currentTimeMillis() - checkTime > T.secs(20).msecs()) {
                if (connectionRetryCounter < 3) {
                    aapsLogger.warn(LTag.PUMPCOMM, "No bolus progress for 20 seconds, retrying connection")
                    connect("retrying connection")
                    checkTime = System.currentTimeMillis()
                    connectionRetryCounter++
                } else {
                    communicationLost = true
                    medtrumPump.bolusErrorReason = rh.gs(R.string.bolus_error_reason_communication_lost)
                    aapsLogger.warn(LTag.PUMPCOMM, "Retry connection failed, communication stopped")
                    disconnect("Communication stopped")
                }
            } else {
                val currentBolusAmount = medtrumPump.bolusingTreatment?.insulin

                if (currentBolusAmount != null && currentBolusAmount != lastSentBolusAmount) {
                    bolusingEvent.t = medtrumPump.bolusingTreatment
                    bolusingEvent.status = rh.gs(app.aaps.pump.common.R.string.bolus_delivered_so_far, medtrumPump.bolusingTreatment?.insulin, medtrumPump.bolusAmountToBeDelivered)
                    bolusingEvent.percent = round(currentBolusAmount.div(medtrumPump.bolusAmountToBeDelivered) * 100).toInt() - 1
                    rxBus.send(bolusingEvent)
                    lastSentBolusAmount = currentBolusAmount
                }
            }
        }

        bolusingEvent.percent = 99
        val bolusDurationInMSec = (medtrumPump.bolusAmountToBeDelivered * 60 * 1000)
        val expectedEnd = medtrumPump.bolusStartTime + bolusDurationInMSec + 1000
        while (System.currentTimeMillis() < expectedEnd && !medtrumPump.bolusDone) {
            SystemClock.sleep(1000)
        }

        // Allow time for notification packet with new sequence number to arrive
        SystemClock.sleep(2000)

        bolusingEvent.t = medtrumPump.bolusingTreatment
        medtrumPump.bolusingTreatment = null

        // Do not call update status directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_bolus_status)))
                bolusingEvent.percent = 100
            }
        })
    }

    fun stopBolus() {
        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop >>>>> @ " + if (medtrumPump.bolusingTreatment == null) "" else medtrumPump.bolusingTreatment?.insulin)
        medtrumPump.bolusErrorReason = rh.gs(R.string.bolus_error_reason_user)
        if (isConnected) {
            var success = sendPacketAndGetResponse(CancelBolusPacket(injector))
            val timeout = System.currentTimeMillis() + T.secs(30).msecs()
            while (!success && System.currentTimeMillis() < timeout) {
                success = sendPacketAndGetResponse(CancelBolusPacket(injector))
                SystemClock.sleep(200)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "bolusStop success: $success")
            medtrumPump.bolusStopped = true
        } else {
            medtrumPump.bolusStopped = true
        }
    }

    fun setTempBasal(absoluteRate: Double, durationInMinutes: Int): Boolean {
        var result = true
        if (medtrumPump.tempBasalInProgress) {
            result = sendPacketAndGetResponse(CancelTempBasalPacket(injector))
        }
        if (result) result = sendPacketAndGetResponse(SetTempBasalPacket(injector, absoluteRate, durationInMinutes))

        // Get history records, this will update the previous basals
        // Do not call update status directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_temp_basal_status)))
            }
        })

        return result
    }

    fun cancelTempBasal(): Boolean {
        val result = sendPacketAndGetResponse(CancelTempBasalPacket(injector))

        // Get history records, this will update the previous basals
        // Do not call update status directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.getting_temp_basal_status)))
            }
        })

        return result
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
        var result = true
        // Update basal affects the TBR records (the pump will cancel the TBR, set our basal profile, and resume the TBR in a new record)
        // Cancel any TBR in progress
        if (medtrumPump.tempBasalInProgress) {
            result = sendPacketAndGetResponse(CancelTempBasalPacket(injector))
        }
        val packet = medtrumPump.buildMedtrumProfileArray(profile)?.let { SetBasalProfilePacket(injector, it) }
        if (result) result = packet?.let { sendPacketAndGetResponse(it) } == true

        // Get history records, this will update the pump state and add changes in TBR to AAPS history
        commandQueue.loadEvents(null)

        return result
    }

    /** This gets the history records from the pump */
    private fun syncRecords(): Boolean {
        aapsLogger.debug(LTag.PUMP, "syncRecords: called!, syncedSequenceNumber: ${medtrumPump.syncedSequenceNumber}, currentSequenceNumber: ${medtrumPump.currentSequenceNumber}")
        var result = true
        var failureCount = 0
        if (medtrumPump.syncedSequenceNumber < medtrumPump.currentSequenceNumber) {
            for (sequence in (medtrumPump.syncedSequenceNumber + 1)..medtrumPump.currentSequenceNumber) {
                rxBus.send(EventPumpStatusChanged(rh.gs(R.string.syncing_records, (medtrumPump.currentSequenceNumber - sequence + 1))))
                val packet = GetRecordPacket(injector, sequence)
                result = sendPacketAndGetResponse(packet, COMMAND_SYNC_TIMEOUT_SEC)
                if (!result && packet.failed) {
                    // Record may be broken for unkown reasons, try the next packet if that fails abort
                    failureCount++
                    aapsLogger.error(LTag.PUMPCOMM, "Failed to sync record $sequence, failureCount: $failureCount")
                    if (failureCount == 1) {
                        // Show notification to alert user of failure
                        uiInteraction.addNotificationWithSound(
                            Notification.PUMP_SYNC_ERROR,
                            rh.gs(R.string.pump_sync_error),
                            Notification.URGENT,
                            app.aaps.core.ui.R.raw.alarm
                        )
                    } else if (failureCount >= 2) {
                        break
                    }
                } else if (!result) {
                    // Communication timeout, try again
                    break
                }
            }
        }
        return result
    }

    private fun handlePumpStateUpdate(state: MedtrumPumpState) {
        // Map the pump state to an alarm state and add it to the active alarms
        val alarmState = when (state) {
            MedtrumPumpState.NONE                 -> AlarmState.NONE
            MedtrumPumpState.LOW_BG_SUSPENDED     -> AlarmState.LOW_BG_SUSPENDED
            MedtrumPumpState.LOW_BG_SUSPENDED2    -> AlarmState.LOW_BG_SUSPENDED2
            MedtrumPumpState.AUTO_SUSPENDED       -> AlarmState.AUTO_SUSPENDED
            MedtrumPumpState.HOURLY_MAX_SUSPENDED -> AlarmState.HOURLY_MAX_SUSPENDED
            MedtrumPumpState.DAILY_MAX_SUSPENDED  -> AlarmState.DAILY_MAX_SUSPENDED
            MedtrumPumpState.SUSPENDED            -> AlarmState.SUSPENDED
            MedtrumPumpState.PAUSED               -> AlarmState.PAUSED
            MedtrumPumpState.OCCLUSION            -> AlarmState.OCCLUSION
            MedtrumPumpState.EXPIRED              -> AlarmState.EXPIRED
            MedtrumPumpState.RESERVOIR_EMPTY      -> AlarmState.RESERVOIR_EMPTY
            MedtrumPumpState.PATCH_FAULT          -> AlarmState.PATCH_FAULT
            MedtrumPumpState.PATCH_FAULT2         -> AlarmState.PATCH_FAULT2
            MedtrumPumpState.BASE_FAULT           -> AlarmState.BASE_FAULT
            MedtrumPumpState.BATTERY_OUT          -> AlarmState.BATTERY_OUT
            MedtrumPumpState.NO_CALIBRATION       -> AlarmState.NO_CALIBRATION
            else                                  -> null
        }
        if (alarmState != null && alarmState != AlarmState.NONE) {
            medtrumPump.addAlarm(alarmState)
            pumpSync.insertAnnouncement(
                medtrumPump.alarmStateToString(alarmState),
                null,
                medtrumPump.pumpType(),
                medtrumPump.pumpSN.toString(radix = 16)
            )
        }

        // Map the pump state to a notification
        when (state) {
            MedtrumPumpState.NONE,
            MedtrumPumpState.STOPPED              -> {
                rxBus.send(EventDismissNotification(Notification.PUMP_WARNING))
                rxBus.send(EventDismissNotification(Notification.PUMP_ERROR))
                rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
                uiInteraction.addNotification(
                    Notification.PATCH_NOT_ACTIVE,
                    rh.gs(R.string.patch_not_active),
                    Notification.URGENT,
                )
                medtrumPump.setFakeTBRIfNotSet()
                medtrumPump.clearAlarmState()

                // Reset sequence numbers, make sure AAPS history can be synced properly on next activation
                medtrumPump.resetPatchParameters()
            }

            MedtrumPumpState.IDLE,
            MedtrumPumpState.FILLED,
            MedtrumPumpState.PRIMING,
            MedtrumPumpState.PRIMED,
            MedtrumPumpState.EJECTING,
            MedtrumPumpState.EJECTED              -> {
                rxBus.send(EventDismissNotification(Notification.PUMP_ERROR))
                rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
                medtrumPump.setFakeTBRIfNotSet()
                medtrumPump.clearAlarmState()
            }

            MedtrumPumpState.ACTIVE,
            MedtrumPumpState.ACTIVE_ALT           -> {
                rxBus.send(EventDismissNotification(Notification.PATCH_NOT_ACTIVE))
                rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
            }

            MedtrumPumpState.LOW_BG_SUSPENDED,
            MedtrumPumpState.LOW_BG_SUSPENDED2,
            MedtrumPumpState.AUTO_SUSPENDED,
            MedtrumPumpState.SUSPENDED,
            MedtrumPumpState.PAUSED               -> {
                uiInteraction.addNotification(
                    Notification.PUMP_SUSPENDED,
                    rh.gs(R.string.pump_is_suspended),
                    Notification.NORMAL,
                )
                // Pump will report proper TBR for this from loadEvents()
                commandQueue.loadEvents(null)
            }

            MedtrumPumpState.HOURLY_MAX_SUSPENDED -> {
                uiInteraction.addNotificationWithSound(
                    Notification.PUMP_SUSPENDED,
                    rh.gs(R.string.pump_is_suspended_hour_max),
                    Notification.URGENT,
                    app.aaps.core.ui.R.raw.alarm
                )
                // Pump will report proper TBR for this from loadEvents()
                commandQueue.loadEvents(null)
            }

            MedtrumPumpState.DAILY_MAX_SUSPENDED  -> {
                uiInteraction.addNotificationWithSound(
                    Notification.PUMP_SUSPENDED,
                    rh.gs(R.string.pump_is_suspended_day_max),
                    Notification.URGENT,
                    app.aaps.core.ui.R.raw.alarm
                )
                // Pump will report proper TBR for this from loadEvents()
                commandQueue.loadEvents(null)
            }

            MedtrumPumpState.OCCLUSION,
            MedtrumPumpState.EXPIRED,
            MedtrumPumpState.RESERVOIR_EMPTY,
            MedtrumPumpState.PATCH_FAULT,
            MedtrumPumpState.PATCH_FAULT2,
            MedtrumPumpState.BASE_FAULT,
            MedtrumPumpState.BATTERY_OUT,
            MedtrumPumpState.NO_CALIBRATION       -> {
                rxBus.send(EventDismissNotification(Notification.PATCH_NOT_ACTIVE))
                rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
                // Pump suspended due to error, show error!
                uiInteraction.addNotificationWithSound(
                    Notification.PUMP_ERROR,
                    rh.gs(R.string.pump_error, alarmState?.let { medtrumPump.alarmStateToString(it) }),
                    Notification.URGENT,
                    app.aaps.core.ui.R.raw.alarm
                )
                // Get pump status, use readStatus here as for loadEvents() we cannot be sure callback is executed
                commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed), object : Callback() {
                    override fun run() {
                        // Make sure a 0 temp is set
                        medtrumPump.setFakeTBRIfNotSet()
                    }
                })
            }
        }
    }

    private fun handleConnectionStateChange(connectionState: ConnectionState) {
        if (medtrumPlugin.isInitialized()) {
            when (connectionState) {
                ConnectionState.CONNECTED     -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                ConnectionState.DISCONNECTED  -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                ConnectionState.CONNECTING    -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
                ConnectionState.DISCONNECTING -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
            }
        }
    }

    private fun notifyPumpWarning(alarmState: AlarmState) {
        // Notification on pump warning
        if (medtrumPump.desiredPumpWarning && alarmState != AlarmState.NONE) {
            uiInteraction.addNotification(
                Notification.PUMP_WARNING,
                rh.gs(R.string.pump_warning, medtrumPump.alarmStateToString(alarmState)),
                Notification.ANNOUNCEMENT,
            )
            pumpSync.insertAnnouncement(
                medtrumPump.alarmStateToString(alarmState),
                null,
                medtrumPump.pumpType(),
                medtrumPump.pumpSN.toString(radix = 16)
            )
        }
    }

    private fun checkExpiryWarning() {
        if (medtrumPump.desiredPatchExpiration && medtrumPump.desiredPumpWarning) {
            val warningAt = medtrumPump.patchStartTime + T.hours(medtrumPump.desiredPumpWarningExpiryThresholdHours.toLong()).msecs()
            if (dateUtil.now() >= warningAt && dateUtil.now() <= warningAt + CHECK_EXPIRY_WARNING_TIME_MS) {
                uiInteraction.addNotification(
                    Notification.PUMP_WARNING,
                    rh.gs(R.string.alarm_pump_expires_soon),
                    Notification.ANNOUNCEMENT,
                )
                pumpSync.insertAnnouncement(
                    rh.gs(R.string.alarm_pump_expires_soon),
                    null,
                    medtrumPump.pumpType(),
                    medtrumPump.pumpSN.toString(radix = 16)
                )
            }
        }
    }

    /** BLECommCallbacks */
    override fun onBLEConnected() {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< onBLEConnected")
        currentState.onConnected()
    }

    override fun onBLEDisconnected() {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< onBLEDisconnected")
        currentState.onDisconnected()
    }

    override fun onNotification(notification: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< onNotification" + notification.contentToString())
        NotificationPacket(injector).handleNotification(notification)
    }

    override fun onIndication(indication: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< onIndication" + indication.contentToString())
        currentState.onIndication(indication)
    }

    override fun onSendMessageError(reason: String, isRetryAble: Boolean) {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< error during send message $reason")
        currentState.onSendMessageError(reason, isRetryAble)
    }

    /** Service stuff */
    inner class LocalBinder : Binder() {

        val serviceInstance: MedtrumService
            get() = this@MedtrumService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * States are used to keep track of the communication and to guide the flow
     */
    private fun toState(nextState: State) {
        currentState = nextState
        currentState.onEnter()
    }

    private fun sendPacketAndGetResponse(packet: MedtrumPacket, timeout: Long = COMMAND_DEFAULT_TIMEOUT_SEC): Boolean {
        var result = false
        if (currentState is ReadyState) {
            toState(CommandState())
            mPacket = packet
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            result = currentState.waitForResponse(timeout)
            SystemClock.sleep(100)
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Send packet attempt when in state: $currentState")
        }
        return result
    }

    // State class
    private abstract inner class State {

        var responseHandled = false
        var responseSuccess = false
        var sendRetryCounter = 0

        open fun onEnter() {
            // Used when a state is entered
        }

        open fun onIndication(data: ByteArray) {
            aapsLogger.warn(LTag.PUMPCOMM, "onIndication: " + this.toString() + "Should not be called here!")
        }

        open fun onConnected() {
            aapsLogger.debug(LTag.PUMPCOMM, "onConnected")
        }

        fun onDisconnected() {
            aapsLogger.debug(LTag.PUMPCOMM, "onDisconnected")
            medtrumPump.connectionState = ConnectionState.DISCONNECTED
            responseHandled = true
            responseSuccess = false
            toState(IdleState())
        }

        fun waitForResponse(timeout: Long): Boolean {
            val startTime = System.currentTimeMillis()
            val timeoutMillis = T.secs(timeout).msecs()
            while (!responseHandled) {
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    // If we haven't received a response in the specified time, assume the command failed
                    aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service State timeout")
                    // Disconnect to cancel any outstanding commands and go back to ready state
                    disconnect("Timeout")
                    toState(IdleState())
                    return false
                }
                SystemClock.sleep(25)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service State responseHandled: $responseHandled responseSuccess: $responseSuccess")
            return responseSuccess
        }

        fun onSendMessageError(reason: String, isRetryAble: Boolean) {
            aapsLogger.warn(LTag.PUMPCOMM, "onSendMessageError: " + this.toString() + "reason: $reason")
            // Retry 3 times
            if (sendRetryCounter < 3 && isRetryAble) {
                sendRetryCounter++
                mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            } else {
                responseHandled = true
                responseSuccess = false
                disconnect("onSendMessageError")
                toState(IdleState())
            }
        }
    }

    private inner class IdleState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached IdleState")
        }

        override fun onConnected() {
            super.onConnected()
            toState(AuthState())
        }
    }

    // State for connect flow
    private inner class AuthState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached AuthState")
            mPacket = AuthorizePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            scope.launch {
                waitForResponse(COMMAND_CONNECTING_TIMEOUT_SEC)
            }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                toState(GetDeviceTypeState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow
    private inner class GetDeviceTypeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached GetDeviceTypeState")
            mPacket = GetDeviceTypePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            scope.launch {
                waitForResponse(COMMAND_CONNECTING_TIMEOUT_SEC)
            }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                // Place holder, not really used (yet)
                val deviceType = (mPacket as GetDeviceTypePacket).deviceType
                val deviceSN = (mPacket as GetDeviceTypePacket).deviceSN
                aapsLogger.debug(LTag.PUMPCOMM, "GetDeviceTypeState: deviceType: $deviceType deviceSN: $deviceSN")
                toState(GetTimeState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow
    private inner class GetTimeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached GetTimeState")
            mPacket = GetTimePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            scope.launch {
                waitForResponse(COMMAND_CONNECTING_TIMEOUT_SEC)
            }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                val currTime = dateUtil.now()
                aapsLogger.debug(LTag.PUMPCOMM, "GetTimeState.onIndication systemTime: $currTime, pumpTime: ${medtrumPump.lastTimeReceivedFromPump}")
                if (abs(medtrumPump.lastTimeReceivedFromPump - currTime) <= T.secs(10).msecs()) { // Allow 10 sec deviation
                    toState(SynchronizeState())
                } else {
                    aapsLogger.warn(LTag.PUMPCOMM, "GetTimeState.onIndication time difference too big, setting time")
                    toState(SetTimeState())
                }
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow
    private inner class SetTimeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SetTimeState")
            mPacket = SetTimePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            scope.launch {
                waitForResponse(COMMAND_CONNECTING_TIMEOUT_SEC)
            }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                toState(SetTimeZoneState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow
    private inner class SetTimeZoneState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SetTimeZoneState")
            mPacket = SetTimeZonePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            scope.launch {
                waitForResponse(COMMAND_CONNECTING_TIMEOUT_SEC)
            }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                medtrumPump.needCheckTimeUpdate = false
                timeUpdateNotification(true)
                toState(SynchronizeState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow
    private inner class SynchronizeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SynchronizeState")
            mPacket = SynchronizePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            scope.launch {
                waitForResponse(COMMAND_CONNECTING_TIMEOUT_SEC)
            }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                toState(SubscribeState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow
    private inner class SubscribeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SubscribeState")
            mPacket = SubscribePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            scope.launch {
                waitForResponse(COMMAND_CONNECTING_TIMEOUT_SEC)
            }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                toState(ReadyState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // This state is reached when the patch is ready to receive commands (Activation, Bolus, temp basal and whatever)
    private inner class ReadyState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached ReadyState!")
            // Now we are fully connected and authenticated and we can start sending commands. Let AAPS know
            if (!isConnected) {
                medtrumPump.connectionState = ConnectionState.CONNECTED
            }
        }
    }

    // This state is when a command is send and we wait for a response for that command
    private inner class CommandState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached CommandState")
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Success!
                responseHandled = true
                responseSuccess = true
                toState(ReadyState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                toState(ReadyState())
            }
        }
    }
}
