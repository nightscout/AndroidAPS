package info.nightscout.pump.medtrum.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.medtrum.MedtrumPlugin
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.comm.enums.AlarmState
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.comm.packets.*
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var medtrumPlugin: MedtrumPlugin
    @Inject lateinit var medtrumPump: MedtrumPump
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: Constraints
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
    }

    val timeUtil = MedtrumTimeUtil()

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()

    private var currentState: State = IdleState()
    private var mPacket: MedtrumPacket? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
                           if (event.isChanged(rh.gs(info.nightscout.pump.medtrum.R.string.key_sn_input))) {
                               medtrumPump.loadUserSettingsFromSP()
                           }
                           if (event.isChanged(rh.gs(info.nightscout.pump.medtrum.R.string.key_alarm_setting))
                               || event.isChanged(rh.gs(info.nightscout.pump.medtrum.R.string.key_patch_expiration))
                               || event.isChanged(rh.gs(info.nightscout.pump.medtrum.R.string.key_hourly_max_insulin))
                               || event.isChanged(rh.gs(info.nightscout.pump.medtrum.R.string.key_daily_max_insulin))
                           ) {
                               medtrumPump.loadUserSettingsFromSP()
                               commandQueue.setUserOptions(object : Callback() {
                                   override fun run() {
                                       if (medtrumPlugin.isInitialized() && this.result.success == false) {
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
            medtrumPump.pumpStateFlow.collect { state ->
                handlePumpStateUpdate(state)
            }
        }

        medtrumPump.loadUserSettingsFromSP()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        scope.cancel()
    }

    fun connect(from: String): Boolean {
        aapsLogger.debug(LTag.PUMP, "connect: called from: $from")
        if (currentState is IdleState) {
            medtrumPump.connectionState = ConnectionState.CONNECTING
            if (medtrumPlugin.isInitialized()) {
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
            }
            return bleComm.connect(from, medtrumPump.pumpSN)
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Connect attempt when in non Idle state from: $from")
            return false
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
        if (result) result = sendPacketAndGetResponse(StopPatchPacket(injector))
        return result
    }

    fun stopConnecting() {
        bleComm.disconnect("stopConnecting")
    }

    fun disconnect(from: String) {
        bleComm.disconnect(from)
    }

    fun readPumpStatus() {
        // Update pump events
        loadEvents()
    }

    fun loadEvents(): Boolean {
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.medtrum.R.string.gettingpumpstatus)))
        // Sync records (based on the info we have from the sync)
        val result = syncRecords()
        if (result) {
            aapsLogger.debug(LTag.PUMPCOMM, "Events loaded")
            medtrumPump.lastConnection = System.currentTimeMillis()
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to load events")
            // TODO: remove me before release
            fabricPrivacy.logMessage("Medtrum LoadEvents: Failed to load events")
        }
        return result
    }

    fun clearAlarms(): Boolean {
        var result = true
        if (medtrumPump.activeAlarms.isNotEmpty()) {
            when (medtrumPump.pumpState) {
                MedtrumPumpState.HMAX_SUSPENDED -> {
                    result = sendPacketAndGetResponse(ClearPumpAlarmPacket(injector, ALARM_HOURLY_MAX_CLEAR_CODE))
                }

                MedtrumPumpState.DMAX_SUSPENDED -> {
                    result = sendPacketAndGetResponse(ClearPumpAlarmPacket(injector, ALARM_DAILY_MAX_CLEAR_CODE))
                }

                else                            -> {
                    // TODO: Remove me before release!!!
                    // Try to brute force the commands
                    aapsLogger.warn(LTag.PUMPCOMM, "Trying to clear alarms brutus!")
                    for (i in 0..100) {
                        result = sendPacketAndGetResponse(ClearPumpAlarmPacket(injector, i))
                        if (result) {
                            aapsLogger.warn(LTag.PUMPCOMM, "Alarm cleared: $i")
                            break
                        }
                    }
                }
            }
        }
        // Resume suspended pump
        if (medtrumPump.pumpState in listOf(MedtrumPumpState.LOWBG_SUSPENDED, MedtrumPumpState.PAUSED)) {
            if (result) result = sendPacketAndGetResponse(ResumePumpPacket(injector))
        }
        if (result) result = sendPacketAndGetResponse(ResumePumpPacket(injector))

        return result
    }

    fun setUserSettings(): Boolean {
        rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.medtrum.R.string.settingpumpsettings)))
        return sendPacketAndGetResponse(SetPatchPacket(injector))
    }

    fun setBolus(detailedBolusInfo: DetailedBolusInfo, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!isConnected) return false
        val insulin = detailedBolusInfo.insulin
        val result = sendPacketAndGetResponse(SetBolusPacket(injector, insulin))

        val bolusStart = System.currentTimeMillis()

        medtrumPump.bolusDone = false
        medtrumPump.bolusingTreatment = t
        medtrumPump.bolusAmountToBeDelivered = insulin
        medtrumPump.bolusStopped = false
        medtrumPump.bolusStopForced = false
        medtrumPump.bolusProgressLastTimeStamp = bolusStart

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

        val bolusingEvent = EventOverviewBolusProgress
        while (medtrumPump.bolusStopped == false && result == true && medtrumPump.bolusDone == false) {
            SystemClock.sleep(100)
            if (System.currentTimeMillis() - medtrumPump.bolusProgressLastTimeStamp > T.secs(15).msecs()) {
                medtrumPump.bolusStopped = true
                medtrumPump.bolusStopForced = true
                aapsLogger.warn(LTag.PUMPCOMM, "Communication stopped")
                bleComm.disconnect("Communication stopped")
            } else {
                bolusingEvent.t = medtrumPump.bolusingTreatment
                bolusingEvent.status = rh.gs(info.nightscout.pump.common.R.string.bolus_delivered_so_far, medtrumPump.bolusingTreatment?.insulin, medtrumPump.bolusAmountToBeDelivered)
                bolusingEvent.percent = round((medtrumPump.bolusingTreatment?.insulin?.div(medtrumPump.bolusAmountToBeDelivered) ?: 0.0) * 100).toInt() - 1
                rxBus.send(bolusingEvent)
            }
        }

        bolusingEvent.t = medtrumPump.bolusingTreatment
        bolusingEvent.percent = 99
        medtrumPump.bolusingTreatment = null

        val bolusDurationInMSec = (insulin * 60 * 1000)
        val expectedEnd = bolusStart + bolusDurationInMSec + 2000
        while (System.currentTimeMillis() < expectedEnd && result == true && medtrumPump.bolusDone == false) {
            SystemClock.sleep(1000)
        }

        // Do not call update status directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.medtrum.R.string.gettingbolusstatus)))
                bolusingEvent.percent = 100
            }
        })
        return result
    }

    fun stopBolus() {
        var result = sendPacketAndGetResponse(CancelBolusPacket(injector))
        aapsLogger.debug(LTag.PUMPCOMM, "bolusStop: result: $result")
    }

    fun setTempBasal(absoluteRate: Double, durationInMinutes: Int): Boolean {
        var result = true
        if (medtrumPump.tempBasalInProgress) {
            result = sendPacketAndGetResponse(CancelTempBasalPacket(injector))
        }
        if (result) result = sendPacketAndGetResponse(SetTempBasalPacket(injector, absoluteRate, durationInMinutes))

        // Get history records, this will update the prevoius basals
        // Do not call update status directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.medtrum.R.string.gettingtempbasalstatus)))
            }
        })

        return result
    }

    fun cancelTempBasal(): Boolean {
        var result = sendPacketAndGetResponse(CancelTempBasalPacket(injector))

        // Get history records, this will update the prevoius basals
        // Do not call update status directly, reconnection may be needed
        commandQueue.loadEvents(object : Callback() {
            override fun run() {
                rxBus.send(EventPumpStatusChanged(rh.gs(info.nightscout.pump.medtrum.R.string.gettingtempbasalstatus)))
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
        // Note: medtrum app fetches all records when they sync?
        if (medtrumPump.syncedSequenceNumber < medtrumPump.currentSequenceNumber) {
            for (sequence in (medtrumPump.syncedSequenceNumber + 1)..medtrumPump.currentSequenceNumber) {
                result = sendPacketAndGetResponse(GetRecordPacket(injector, sequence), COMMAND_SYNC_TIMEOUT_SEC)
                SystemClock.sleep(100)
                if (result == false) break
            }
        }
        return result
    }

    private fun handlePumpStateUpdate(state: MedtrumPumpState) {
        // Map the pump state to an alarm state and add it to the active alarms
        val alarmState = when (state) {
            MedtrumPumpState.NONE             -> AlarmState.NONE
            MedtrumPumpState.LOWBG_SUSPENDED  -> AlarmState.LOWBG_SUSPENDED
            MedtrumPumpState.LOWBG_SUSPENDED2 -> AlarmState.LOWBG_SUSPENDED2
            MedtrumPumpState.AUTO_SUSPENDED   -> AlarmState.AUTO_SUSPENDED
            MedtrumPumpState.HMAX_SUSPENDED   -> AlarmState.HMAX_SUSPENDED
            MedtrumPumpState.DMAX_SUSPENDED   -> AlarmState.DMAX_SUSPENDED
            MedtrumPumpState.SUSPENDED        -> AlarmState.SUSPENDED
            MedtrumPumpState.PAUSED           -> AlarmState.PAUSED
            MedtrumPumpState.OCCLUSION        -> AlarmState.OCCLUSION
            MedtrumPumpState.EXPIRED          -> AlarmState.EXPIRED
            MedtrumPumpState.RESERVOIR_EMPTY  -> AlarmState.RESERVOIR_EMPTY
            MedtrumPumpState.PATCH_FAULT      -> AlarmState.PATCH_FAULT
            MedtrumPumpState.PATCH_FAULT2     -> AlarmState.PATCH_FAULT2
            MedtrumPumpState.BASE_FAULT       -> AlarmState.BASE_FAULT
            MedtrumPumpState.BATTERY_OUT      -> AlarmState.BATTERY_OUT
            MedtrumPumpState.NO_CALIBRATION   -> AlarmState.NO_CALIBRATION
            else                              -> null
        }
        if (alarmState != null && alarmState != AlarmState.NONE) {
            medtrumPump.addAlarm(alarmState)
        }

        // Map the pump state to a notification
        when (state) {
            MedtrumPumpState.NONE,
            MedtrumPumpState.IDLE,
            MedtrumPumpState.FILLED,
            MedtrumPumpState.PRIMING,
            MedtrumPumpState.PRIMED,
            MedtrumPumpState.EJECTING,
            MedtrumPumpState.EJECTED,
            MedtrumPumpState.STOPPED        -> {
                rxBus.send(EventDismissNotification(Notification.PUMP_ERROR))
                rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
                uiInteraction.addNotification(
                    Notification.PATCH_NOT_ACTIVE,
                    rh.gs(R.string.patch_not_active),
                    Notification.URGENT,
                )
                medtrumPump.setFakeTBRIfNeeded()
                medtrumPump.clearAlarmState()
            }

            MedtrumPumpState.ACTIVE,
            MedtrumPumpState.ACTIVE_ALT     -> {
                rxBus.send(EventDismissNotification(Notification.PATCH_NOT_ACTIVE))
                rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
                medtrumPump.clearAlarmState()
            }

            MedtrumPumpState.LOWBG_SUSPENDED,
            MedtrumPumpState.LOWBG_SUSPENDED2,
            MedtrumPumpState.AUTO_SUSPENDED,
            MedtrumPumpState.SUSPENDED,
            MedtrumPumpState.PAUSED         -> {
                uiInteraction.addNotification(
                    Notification.PUMP_SUSPENDED,
                    rh.gs(R.string.pump_is_suspended),
                    Notification.NORMAL,
                )
                // Pump will report proper TBR for this
            }

            MedtrumPumpState.HMAX_SUSPENDED -> {
                uiInteraction.addNotification(
                    Notification.PUMP_SUSPENDED,
                    rh.gs(R.string.pump_is_suspended_hour_max),
                    Notification.NORMAL,
                )
                // Pump will report proper TBR for this
            }

            MedtrumPumpState.DMAX_SUSPENDED -> {
                uiInteraction.addNotification(
                    Notification.PUMP_SUSPENDED,
                    rh.gs(R.string.pump_is_suspended_day_max),
                    Notification.NORMAL,
                )
                // Pump will report proper TBR for this
            }

            MedtrumPumpState.OCCLUSION,
            MedtrumPumpState.EXPIRED,
            MedtrumPumpState.RESERVOIR_EMPTY,
            MedtrumPumpState.PATCH_FAULT,
            MedtrumPumpState.PATCH_FAULT2,
            MedtrumPumpState.BASE_FAULT,
            MedtrumPumpState.BATTERY_OUT,
            MedtrumPumpState.NO_CALIBRATION -> {
                rxBus.send(EventDismissNotification(Notification.PATCH_NOT_ACTIVE))
                rxBus.send(EventDismissNotification(Notification.PUMP_SUSPENDED))
                // Pump suspended due to error, show error!
                uiInteraction.addNotificationWithSound(
                    Notification.PUMP_ERROR,
                    rh.gs(R.string.pump_error, alarmState?.let { medtrumPump.alarmStateToString(it) }),
                    Notification.URGENT,
                    info.nightscout.core.ui.R.raw.alarm
                )
                medtrumPump.setFakeTBRIfNeeded()
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

    override fun onSendMessageError(reason: String) {
        aapsLogger.debug(LTag.PUMPCOMM, "<<<<< error during send message $reason")
        currentState.onSendMessageError(reason)
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
        return Service.START_STICKY
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
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Send packet attempt when in non Ready state")
        }
        return result
    }

    // State class, Can we move this to different file?
    private abstract inner class State {

        protected var responseHandled = false
        protected var responseSuccess = false

        open fun onEnter() {}
        open fun onIndication(data: ByteArray) {
            aapsLogger.debug(LTag.PUMPCOMM, "onIndication: " + this.toString() + "Should not be called here!")
        }

        open fun onConnected() {
            aapsLogger.debug(LTag.PUMPCOMM, "onConnected")
        }

        fun onDisconnected() {
            aapsLogger.debug(LTag.PUMPCOMM, "onDisconnected")
            medtrumPump.connectionState = ConnectionState.DISCONNECTED
            if (medtrumPlugin.isInitialized()) {
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            }
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
                    bleComm.disconnect("Timeout")
                    toState(IdleState())
                    return false
                }
                SystemClock.sleep(100)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service State responseHandled: $responseHandled responseSuccess: $responseSuccess")
            return responseSuccess
        }

        fun onSendMessageError(reason: String) {
            aapsLogger.debug(LTag.PUMPCOMM, "onSendMessageError: " + this.toString() + "reason: $reason")
            responseHandled = true
            responseSuccess = false
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

        val retryCounter = 0

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
                // Succes!
                responseHandled = true
                responseSuccess = true
                // Check if we have a supported pump
                if (medtrumPump.pumpType() == PumpType.MEDTRUM_UNTESTED) {
                    // Throw error 
                    aapsLogger.error(LTag.PUMPCOMM, "Unsupported pump type")
                    uiInteraction.addNotificationWithSound(
                        Notification.PUMP_ERROR,
                        rh.gs(R.string.pump_unsupported, medtrumPump.deviceType),
                        Notification.URGENT,
                        info.nightscout.core.ui.R.raw.alarm
                    )
                    bleComm.disconnect("Unsupported pump")
                    toState(IdleState())
                } else {
                    toState(GetDeviceTypeState())
                }
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                bleComm.disconnect("Failure")
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
                // Succes!
                responseHandled = true
                responseSuccess = true
                // TODO Get device type and SN
                val deviceType = (mPacket as GetDeviceTypePacket).deviceType
                val deviceSN = (mPacket as GetDeviceTypePacket).deviceSN
                aapsLogger.debug(LTag.PUMPCOMM, "GetDeviceTypeState: deviceType: $deviceType deviceSN: $deviceSN") // TODO remove me later
                toState(GetTimeState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                bleComm.disconnect("Failure")
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
                // Succes!
                responseHandled = true
                responseSuccess = true
                val currTime = dateUtil.nowWithoutMilliseconds()
                if (abs(medtrumPump.lastTimeReceivedFromPump - currTime) <= T.secs(5).msecs()) { // Allow 5 sec deviation
                    toState(SynchronizeState())
                } else {
                    aapsLogger.debug(
                        LTag.PUMPCOMM,
                        "GetTimeState.onIndication need to set time. systemTime: $currTime PumpTime: ${medtrumPump.lastTimeReceivedFromPump} Pump Time to system time: " + timeUtil
                            .convertPumpTimeToSystemTimeMillis(
                                medtrumPump.lastTimeReceivedFromPump
                            )
                    )
                    // TODO: Setting time cancels any TBR, so we need to handle that and cancel? or let AAPS handle time syncs?
                    toState(SetTimeState())
                }
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                bleComm.disconnect("Failure")
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
                // Succes!
                responseHandled = true
                responseSuccess = true
                toState(SetTimeZoneState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                bleComm.disconnect("Failure")
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
                // Succes!
                responseHandled = true
                responseSuccess = true
                toState(SynchronizeState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                bleComm.disconnect("Failure")
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
                // Succes!
                responseHandled = true
                responseSuccess = true
                toState(SubscribeState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                bleComm.disconnect("Failure")
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
                // Succes!
                responseHandled = true
                responseSuccess = true
                toState(ReadyState())
            } else if (mPacket?.failed == true) {
                // Failure
                responseHandled = true
                responseSuccess = false
                bleComm.disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // This state is reached when the patch is ready to receive commands (Activation, Bolus, temp basal and whatever)
    private inner class ReadyState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached ReadyState!")
            // Now we are fully connected and authenticated and we can start sending commands. Let AAPS know
            if (isConnected == false) {
                medtrumPump.connectionState = ConnectionState.CONNECTED
                if (medtrumPlugin.isInitialized()) {
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                }
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
                // Succes!
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
