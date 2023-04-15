package info.nightscout.pump.medtrum.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
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
        val packet = PrimePacket(injector)
        return sendPacketAndGetResponse(packet)
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
        // TODO proper way for this might need send commands etc
        bleComm.stopConnecting()
    }

    fun disconnect(from: String) {
        // TODO proper way for this might need send commands etc
        bleComm.disconnect(from)
    }

    fun readPumpStatus() {
        // TODO read pump history
    }

    fun loadEvents(): PumpEnactResult {
        if (!medtrumPlugin.isInitialized()) {
            val result = PumpEnactResult(injector).success(false)
            result.comment = "pump not initialized"
            return result
        }
        // TODO need this? Check
        val result = PumpEnactResult(injector)
        return result
    }

    fun bolus(insulin: Double, carbs: Int, carbTime: Long, t: EventOverviewBolusProgress.Treatment): Boolean {
        if (!isConnected) return false
        // TODO
        return false
    }

    fun bolusStop() {
        // TODO
    }

    fun updateBasalsInPump(profile: Profile): Boolean {
        val packet = medtrumPump.buildMedtrumProfileArray(profile)?.let { SetBasalProfilePacket(injector, it) }
        return packet?.let { sendPacketAndGetResponse(it) } == true
    }

    fun changePump() {
        aapsLogger.debug(LTag.PUMP, "changePump: called!")
        try {
            medtrumPump.pumpSN = sp.getString(info.nightscout.pump.medtrum.R.string.key_snInput, " ").toLong(radix = 16)
        } catch (e: NumberFormatException) {
            aapsLogger.debug(LTag.PUMP, "changePump: Invalid input!")
        }
        medtrumPump.setPatchActivatedState(sp.getBoolean(R.string.key_patch_activated, false))
        medtrumPump.patchSessionToken = sp.getLong(R.string.key_session_token, 0)
        if (medtrumPump.patchActivated) {
            aapsLogger.debug(LTag.PUMP, "changePump: Patch is already activated, setting as ACTIVE")            
            medtrumPump.pumpState = MedtrumPumpState.ACTIVE // Set inital status as active will be updated on first connection
        }
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
        // TODO 
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
                bleComm.disconnect("Failure")
                toState(IdleState())
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
                val currTimeSec = dateUtil.nowWithoutMilliseconds() / 1000
                if (abs(timeUtil.convertPumpTimeToSystemTimeSeconds(medtrumPump.lastTimeReceivedFromPump) - currTimeSec) <= 5) { // Allow 5 sec deviation
                    toState(SynchronizeState())
                } else {
                    aapsLogger.debug(
                        LTag.PUMPCOMM,
                        "GetTimeState.onIndication need to set time. systemTime: $currTimeSec PumpTime: ${medtrumPump.lastTimeReceivedFromPump} Pump Time to system time: " + timeUtil.convertPumpTimeToSystemTimeSeconds(
                            medtrumPump.lastTimeReceivedFromPump
                        )
                    )
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
