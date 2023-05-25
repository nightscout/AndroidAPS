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
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.medtrum.MedtrumPlugin
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.comm.packets.*
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.pump.medtrum.extension.toLong
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
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
import java.util.*
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
    @Inject lateinit var dateUtil: DateUtil

    val timeUtil = MedtrumTimeUtil()

    private val disposable = CompositeDisposable()
    private val mBinder: IBinder = LocalBinder()

    private var currentState: State = IdleState()
    private var mPacket: MedtrumPacket? = null
    
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
                           if (event.isChanged(rh.gs(info.nightscout.pump.medtrum.R.string.key_snInput))) {
                               changePump()
                           }
                       }, fabricPrivacy::logException)
        changePump()
        // TODO: We should probably listen to the pump state as well and handle some state changes? Or do we handle that in the packets or medtrumPump?
    }

    override fun onDestroy() {
        disposable.clear()
        super.onDestroy()
    }

    fun connect(from: String): Boolean {
        aapsLogger.debug(LTag.PUMP, "connect: called from: $from")
        if (currentState is IdleState) {
            medtrumPump.connectionState = ConnectionState.CONNECTING
            if (medtrumPump.patchActivated) {
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
        return sendPacketAndGetResponse(StopPatchPacket(injector))
    }

    fun stopConnecting() {
        bleComm.disconnect("stopConnecting")
    }

    fun disconnect(from: String) {
        bleComm.disconnect(from)
    }

    fun readPumpStatus() {
        // TODO decide what we need to do here
        var result = false

        // Most of these things are already done when a connection is setup, but wo dont know how long the pump was connected for?
        // So just do a syncronize to make sure we have the latest data
        result = sendPacketAndGetResponse(SynchronizePacket(injector))

        // Sync records (based on the info we have from the sync)
        if (result) result = syncRecords()
        if (!result) {
            aapsLogger.error(LTag.PUMPCOMM, "Failed to sync records")
            return
        }
    }

    fun setBolus(insulin: Double, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!isConnected) return false
        val result = sendPacketAndGetResponse(SetBolusPacket(injector, insulin))

        medtrumPump.bolusDone = false
        medtrumPump.bolusingTreatment = t
        medtrumPump.bolusAmountToBeDelivered = insulin
        medtrumPump.bolusStopped = false
        medtrumPump.bolusStopForced = false
        medtrumPump.bolusProgressLastTimeStamp = dateUtil.now()

        val bolusStart = System.currentTimeMillis()
        
        val bolusingEvent = EventOverviewBolusProgress
        while (medtrumPump.bolusStopped == false && result == true && medtrumPump.bolusDone == false) {
            SystemClock.sleep(100)
            if (System.currentTimeMillis() - medtrumPump.bolusProgressLastTimeStamp > T.secs(15).msecs()) {
                medtrumPump.bolusStopped = true
                medtrumPump.bolusStopForced = true
                aapsLogger.debug(LTag.PUMPCOMM, "Communication stopped")
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
        while (System.currentTimeMillis() < expectedEnd) {
            SystemClock.sleep(1000)
        }

        // Do not call update status directly, reconnection may be needed
        commandQueue.readStatus(rh.gs(info.nightscout.pump.medtrum.R.string.gettingbolusstatus), object : Callback() {
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

        // Get history records, this will update the pump state
        if (result) result = syncRecords()

        return result
    }

    fun cancelTempBasal(): Boolean {
        var result = false

        result = sendPacketAndGetResponse(CancelTempBasalPacket(injector))

        // Get history records, this will update the pump state
        if (result) result = syncRecords()

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
        if (result) result = syncRecords()

        return result
    }

    fun changePump() {
        aapsLogger.debug(LTag.PUMP, "changePump: called!")
        medtrumPump.loadUserSettingsFromSP()
    }

    /** This gets the history records from the pump */
    private fun syncRecords(): Boolean {
        aapsLogger.debug(LTag.PUMP, "syncRecords: called!, syncedSequenceNumber: ${medtrumPump.syncedSequenceNumber}, currentSequenceNumber: ${medtrumPump.currentSequenceNumber}")
        var result = false
        // Note: medtrum app fetches all records when they sync?
        for (sequence in medtrumPump.syncedSequenceNumber..medtrumPump.currentSequenceNumber) {
            result = sendPacketAndGetResponse(GetRecordPacket(injector, sequence))
            if (!result) break
        }
        return result
    }

    /** BLECommCallbacks */
    override fun onBLEConnected() {
        currentState.onConnected()
    }

    override fun onBLEDisconnected() {
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

    private fun sendPacketAndGetResponse(packet: MedtrumPacket): Boolean {
        var result = false
        if (currentState is ReadyState) {
            toState(CommandState())
            mPacket = packet
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
            result = currentState.waitForResponse()
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "Send packet attempt when in non Ready state")
        }
        return result
    }

    // State class, Can we move this to different file?
    private abstract inner class State {

        open fun onEnter() {}
        open fun onIndication(data: ByteArray) {
            aapsLogger.debug(LTag.PUMPCOMM, "onIndication: " + this.toString() + "Should not be called here!")
        }

        open fun onConnected() {
            aapsLogger.debug(LTag.PUMPCOMM, "onConnected")
        }

        open fun onDisconnected() {
            aapsLogger.debug(LTag.PUMPCOMM, "onDisconnected")
            medtrumPump.connectionState = ConnectionState.DISCONNECTED
            if (medtrumPump.patchActivated) {
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            }
            // TODO: Check flow for this
            toState(IdleState())
        }

        open fun waitForResponse(): Boolean {
            return false
        }

        open fun onSendMessageError(reason: String) {
            aapsLogger.debug(LTag.PUMPCOMM, "onSendMessageError: " + this.toString() + "reason: $reason")
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

        override fun onDisconnected() {
            super.onDisconnected()
        }
    }

    // State for connect flow, could be replaced by commandState and steps in connect()
    private inner class AuthState : State() {
        val retryCounter = 0

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached AuthState")
            mPacket = AuthorizePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Succes!
                // TODO Get pump version info
                val deviceType = (mPacket as AuthorizePacket).deviceType
                val swVersion = (mPacket as AuthorizePacket).swVersion
                aapsLogger.debug(LTag.PUMPCOMM, "GetDeviceTypeState: deviceType: $deviceType swVersion: $swVersion") // TODO remove me later
                toState(GetDeviceTypeState())
            } else if (mPacket?.failed == true) {
                // Failure
                // retry twice
                // TODO: Test and see if this can be removed
                if (retryCounter < 2) {
                    aapsLogger.error(LTag.PUMPCOMM, "AuthState failed!, retrying")
                    mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
                } else {
                    bleComm.disconnect("Failure")
                    toState(IdleState())
                }
            }
        }
    }

    // State for connect flow, could be replaced by commandState and steps in connect()
    private inner class GetDeviceTypeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached GetDeviceTypeState")
            mPacket = GetDeviceTypePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Succes!
                // TODO Get device type and SN
                val deviceType = (mPacket as GetDeviceTypePacket).deviceType
                val deviceSN = (mPacket as GetDeviceTypePacket).deviceSN
                aapsLogger.debug(LTag.PUMPCOMM, "GetDeviceTypeState: deviceType: $deviceType deviceSN: $deviceSN") // TODO remove me later
                toState(GetTimeState())
            } else if (mPacket?.failed == true) {
                // Failure
                bleComm.disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow, could be replaced by commandState and steps in connect()
    private inner class GetTimeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached GetTimeState")
            mPacket = GetTimePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Succes!
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
                bleComm.disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow, could be replaced by commandState and steps in connect()
    private inner class SetTimeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SetTimeState")
            mPacket = SetTimePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Succes!
                toState(SetTimeZoneState())
            } else if (mPacket?.failed == true) {
                // Failure
                bleComm.disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow, could be replaced by commandState and steps in connect()
    private inner class SetTimeZoneState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SetTimeZoneState")
            mPacket = SetTimeZonePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Succes!
                toState(SynchronizeState())
            } else if (mPacket?.failed == true) {
                // Failure
                bleComm.disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow, could be replaced by commandState and steps in connect()
    private inner class SynchronizeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SynchronizeState")
            mPacket = SynchronizePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Succes!
                toState(SubscribeState())
            } else if (mPacket?.failed == true) {
                // Failure
                bleComm.disconnect("Failure")
                toState(IdleState())
            }
        }
    }

    // State for connect flow, could be replaced by commandState and steps in connect()
    private inner class SubscribeState : State() {

        override fun onEnter() {
            aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service reached SubscribeState")
            mPacket = SubscribePacket(injector)
            mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        }

        override fun onIndication(data: ByteArray) {
            if (mPacket?.handleResponse(data) == true) {
                // Succes!
                toState(ReadyState())
            } else if (mPacket?.failed == true) {
                // Failure
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
            medtrumPump.connectionState = ConnectionState.CONNECTED
            if (medtrumPump.patchActivated) {
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
            }
        }
    }

    // This state is when a command is send and we wait for a response for that command
    private inner class CommandState : State() {

        private var responseHandled = false
        private var responseSuccess = false

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

        override fun onDisconnected() {
            super.onDisconnected()
            responseHandled = true
            responseSuccess = false
        }

        override fun onSendMessageError(reason: String) {
            super.onSendMessageError(reason)
            responseHandled = true
            responseSuccess = false
        }

        override fun waitForResponse(): Boolean {
            val startTime = System.currentTimeMillis()
            val timeoutMillis = T.secs(45).msecs()
            while (!responseHandled) {
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    // If we haven't received a response in the specified time, assume the command failed
                    aapsLogger.debug(LTag.PUMPCOMM, "Medtrum Service CommandState timeout")
                    // Disconnect to cancel any outstanding commands and go back to ready state
                    bleComm.disconnect("Timeout")
                    toState(ReadyState())
                    return false
                }
                Thread.sleep(100)
            }
            return responseSuccess
        }
    }
}
