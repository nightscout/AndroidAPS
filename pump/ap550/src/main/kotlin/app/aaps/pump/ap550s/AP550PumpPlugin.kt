package app.aaps.pump.ap550s

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.provider.Settings
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.BasalUpdateResult
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.constraints.PumpPluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.Dana
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.comment
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.withClick
import app.aaps.core.ui.compose.icons.IcPluginAp550
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.pump.ap550.protocol.comm.Ap550MessageFactory
import app.aaps.pump.ap550.protocol.comm.RecordTypes
import app.aaps.pump.ap550.protocol.logging.Ap550Sdk
import app.aaps.pump.ap550s.compose.Ap550ComposeContent
import app.aaps.pump.ap550s.database.Ap550HistoryDatabase
import app.aaps.pump.ap550s.events.EventBolusStart
import app.aaps.pump.ap550s.events.EventHypoglycemiaAlertCheck
import app.aaps.pump.ap550s.events.EventProfileSwitchChangedSuccess
import app.aaps.pump.ap550s.events.EventPumpSuspendedChange
import app.aaps.pump.ap550s.keys.Ap550BooleanKey
import app.aaps.pump.ap550s.keys.Ap550BooleanNonKey
import app.aaps.pump.ap550s.keys.Ap550IntentKey
import app.aaps.pump.ap550s.keys.Ap550StringNonKey
import app.aaps.pump.ap550s.receiver.AP550KeepAliveReceiver
import app.aaps.pump.ap550s.service.AP550ExecutionService
import app.aaps.pump.ap550s.utils.ConnectUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey as MetroIntKey
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.StringUtils
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

// Pump plugin registration — @MetroIntKey range 1000–1200, see AppRootGraph for the overview.
// 1140 is the first free key after the drivers that were already registered in dev.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@PumpDriver
@MetroIntKey(1140)
@SingleIn(AppScope::class)
class AP550PumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val uel: UserEntryLogger,
    private val context: Context,
    private val ap550Pump: AP550Pump,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val ap550HistoryDatabase: Ap550HistoryDatabase,
    private val decimalFormatter: DecimalFormatter,
    private var pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val bolusProgressData: BolusProgressData,
    notificationManager: NotificationManager
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ -> Ap550ComposeContent(pluginName = rh.gs(R.string.ap_550)) }
        .icon(IcPluginAp550)
        .pluginName(TextRef.AndroidRes(R.string.ap_550))
        .description(TextRef.AndroidRes(R.string.description_pump_ap550)),
    ownPreferences = Ap550BooleanKey.entries + Ap550BooleanNonKey.entries + Ap550StringNonKey.entries,
    aapsLogger, rh, preferences, commandQueue, notificationManager
), Pump, Dana, PluginConstraints, PumpPluginConstraints, OwnDatabasePlugin {

    // The SDK message factory. Field-injected so the constructor stays readable; its own @Inject
    // constructor holds no eager state.
    @Inject lateinit var sdkMessageFactory: Ap550MessageFactory
    @Inject lateinit var loop: Loop
    @Inject lateinit var persistenceLayer: PersistenceLayer

    private var sExecutionService: AP550ExecutionService? = null
    private var scope: CoroutineScope? = null

    override var isFakingTempsByExtendedBoluses: Boolean = false
        protected set
    override var pumpDescription = PumpDescription()

    private val connectUtil: ConnectUtil = ConnectUtil()

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Test_ble_connect: ServiceConnection Service is disconnected")
            rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, 0))
            sExecutionService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Test_ble_connect: ServiceConnection Service is connected")
            val mLocalBinder = service as AP550ExecutionService.LocalBinder
            sExecutionService = mLocalBinder.serviceInstance
        }
    }

    init {
        pumpDescription.fillFor(PumpType.AP550S)
        // The pump itself only knows percent temp basals; the switch only decides what AAPS sends and
        // shows. Reflect the stored setting here, because the old preference screen callback
        // (preprocessPreferences) that used to do this no longer exists.
        pumpDescription.tempBasalStyle =
            if (preferences.get(Ap550BooleanKey.BasalIsPercent)) PumpDescription.PERCENT
            else PumpDescription.ABSOLUTE
        isFakingTempsByExtendedBoluses = preferences.get(Ap550BooleanNonKey.UseExtended)
        // ===== SDK init: the encrypted log component must be set up before the first pump talk =====
        Ap550Sdk.init(context)
    }

    /** Forget the paired pump: clear the stored identity and drop the link. */
    fun unpairPump() {
        uel.log(Action.CLEAR_PAIRING_KEYS, Sources.AP550S)
        preferences.remove(Ap550StringNonKey.MacAddress)
        preferences.remove(Ap550StringNonKey.BtDeviceName)
        preferences.remove(Ap550StringNonKey.SerialNumber)
        preferences.remove(Ap550StringNonKey.PairingCode)
        // Forget the profile state of this pump too: the mismatch flag and the cached profile.
        // Binding a new pump is only possible after unpairing, so clearing here makes sure the new
        // pump starts from "profile unknown" and is written, not compared against the old pump.
        preferences.remove(Ap550BooleanNonKey.BasalProfileMismatch)
        ap550Pump.forgetProfile()
        ap550Pump.sn_for_pair = ""
        ap550Pump.reset()
        disconnect("unpair")
    }

    override suspend fun onStart() {
        super.onStart()
        val intent = Intent(context, AP550ExecutionService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        // The driver keeps its own 15 s wakeup chain, because WorkManager cannot go below 15 minutes
        // and a timer does not survive Doze. It runs only while this driver is the active pump.
        AP550KeepAliveReceiver.startAliveTask(context)

        // Own scope on IO, like the io scheduler used before, cancelled in onStop.
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        rxBus.toFlow(EventAppExit::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                aapsLogger.debug(LTag.PUMP, "EventAppExit received")
                context.unbindService(mConnection)
            }
        rxBus.toFlow(EventConfigBuilderChange::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { ap550Pump.reset() }
        rxBus.toFlow(EventProfileSwitchChangedSuccess::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { activeProfileCancelTempBasal() }
        rxBus.toFlow(EventPumpSuspendedChange::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                loop.scheduleBuildAndStoreDeviceStatus("Pump suspended changed")
            }

        // Emulating a temp basal above the base rate with an extended bolus is a user setting. When it
        // flips, a running extended bolus no longer matches what the loop expects, so stop it.
        preferences.observe(Ap550BooleanNonKey.UseExtended)
            .drop(1)
            .onEach { useExtended ->
                if (!isEnabled()) return@onEach
                val previousValue = isFakingTempsByExtendedBoluses
                isFakingTempsByExtendedBoluses = useExtended
                if (isFakingTempsByExtendedBoluses != previousValue && pumpSync.expectedPumpState().extendedBolus != null) {
                    sExecutionService?.extendedBolusStop()
                }
            }
            .launchIn(newScope)

        // A different pump was paired: forget the old pump state and read the new one. The serial
        // number is watched, not the Bluetooth name, because two pumps can advertise the same name
        // and a StateFlow only emits when the value really changes.
        preferences.observe(Ap550StringNonKey.SerialNumber)
            .drop(1)
            .onEach {
                ap550Pump.reset()
                pumpSync.connectNewPump(true)
                pluginScope.launch { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed)) }
            }
            .launchIn(newScope)

        // Keep the reported temp basal style in step with the switch.
        preferences.observe(Ap550BooleanKey.BasalIsPercent)
            .drop(1)
            .onEach { isPercent ->
                pumpDescription.tempBasalStyle = if (isPercent) PumpDescription.PERCENT else PumpDescription.ABSOLUTE
                aapsLogger.debug(LTag.PUMP, "BasalIsPercent changed to: $isPercent tempBasalStyle: ${pumpDescription.tempBasalStyle}")
            }
            .launchIn(newScope)
    }

    override suspend fun onStop() {
        aapsLogger.debug(LTag.PUMP, "Test_ble_connect: onStop")
        AP550KeepAliveReceiver.stopAliveTask(context)
        disconnect("switch pump")
        scope?.cancel()
        scope = null
        context.unbindService(mConnection)
        super.onStop()
    }

    override fun isSuspended(): Boolean {
        return ap550Pump.pumpSuspended
    }

    // A pump driver is never busy for long: the QueueWorker runs the commands one by one, and
    // `performing` already holds the slot while one runs. Connect states are reported by
    // isConnecting()/isConnected(), which the QueueWorker handles first. Returning isConnected()
    // here would block the queue forever, because the QueueWorker would only log "busy" and never
    // take a command.
    override fun isBusy(): Boolean = false

    override fun isConfigured(): Boolean = preferences.get(Ap550StringNonKey.MacAddress).isNotEmpty()

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        val result = pumpEnactResultProvider()

        if (sExecutionService == null) {
            aapsLogger.error("setNewBasalProfile sExecutionService is null")
            result.comment("setNewBasalProfile sExecutionService is null")
            return result
        }
        if (!isInitialized()) {
            aapsLogger.error("setNewBasalProfile not initialized")
            // Not initialized yet — deferred, not a genuine error; re-pushed on reconnect. success=true keeps it
            // out of the central failure alarm; enacted stays false so nothing is shown. Profile-set
            // notifications (OK / clear-failed) are owned centrally by CommandQueueImplementation.onProfileChanged.
            result.success = true
            result.comment(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            return result
        }

        return when (sExecutionService!!.updateBasalsInPump(profile)) {
            BasalUpdateResult.SUCCESS -> {
                result.success = true
                result.enacted = true
                result.comment(app.aaps.core.ui.R.string.profile_set_ok)
                rxBus.send(EventProfileSwitchChangedSuccess())
                result
            }
            BasalUpdateResult.DISCONNECTED -> {
                result.comment(R.string.failed_update_basal_profile_pump_disconnected)
                result
            }
            BasalUpdateResult.FAILED -> {
                // The pump rejects the profile when a rate is above its max basal. The failure is
                // posted centrally (onProfileChanged) from success=false, with this comment as text.
                result.comment(rh.gs(R.string.ap550_basal_above_pump_max_basal, ap550Pump.maxBasal))
                result
            }
        }
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (!isInitialized()) return true // TODO: not sure what's better. so far TRUE to prevent too many SMS

        // The comparison below trusts the cached pump profile, which is refreshed at most once an
        // hour and is not tied to the pump that is bound now. A profile the user edited on the pump
        // by hand, or a newly bound pump, can therefore look equal while it is not, and the profile
        // write is skipped. When the pump has reported a mismatch - or we have not written the
        // profile to this pump yet - force the write.
        if (preferences.get(Ap550BooleanNonKey.BasalProfileMismatch)) {
            aapsLogger.debug(LTag.PUMP, "isThisProfileSet: profile flagged as mismatched, forcing a rewrite")
            return false
        }

        if (ap550Pump.pumpProfiles == null) {
            aapsLogger.debug(LTag.PUMP, "No profile set")
            return true // TODO: not sure what's better. so far TRUE to prevent too many SMS
        }

        val basalValues = if (ap550Pump.basal48Enable) 48 else 24
        val basalIncrement = if (ap550Pump.basal48Enable) 30 * 60 else 60 * 60
        for (h in 0 until basalValues) {
            aapsLogger.debug(LTag.PUMP, "ap550Pump.getActiveProfile() = " + ap550Pump.activeProfile)
            val pumpValue: Double = ap550Pump.pumpProfiles!![ap550Pump.activeProfile][h]
            val profileValue = profile.getBasalTimeFromMidnight(h * basalIncrement)
            aapsLogger.debug(LTag.PUMP, "Hour: $h Pump: $pumpValue Profile: $profileValue")
            if (abs(pumpValue - profileValue) > pumpDescription.basalStep) {
                aapsLogger.debug(LTag.PUMP, "Diff found. Hour: $h Pump: $pumpValue Profile: $profileValue")
                aapsLogger.error("isThisProfileSet: pump profiles ${ap550Pump.pumpProfiles.contentDeepToString()}")
                aapsLogger.error("isThisProfileSet: active profile " + ap550Pump.activeProfile.toString())
                return false
            }
        }
        return true
    }

    override val lastDataTime: StateFlow<Long> = ap550Pump.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> = ap550Pump.lastBolusTimeFlow
    override val lastBolusAmount: StateFlow<PumpInsulin?> = ap550Pump.lastBolusAmountFlow.mapState { it?.let(::PumpInsulin) }
    override val reservoirLevel: StateFlow<PumpInsulin> = ap550Pump.reservoirRemainingUnitsFlow.mapState(::PumpInsulin)
    override val batteryLevel: StateFlow<Int?> = ap550Pump.batteryRemainingFlow
    override val baseBasalRate: PumpRate get() = PumpRate(ap550Pump.currentBasal)

    override fun stopBolusDelivering() {
        if (sExecutionService == null) {
            aapsLogger.error("stopBolusDelivering sExecutionService is null")
            return
        }
        sExecutionService!!.bolusStop()
    }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        var percent = percent
        val result = pumpEnactResultProvider()
        // Already constrained in IU and in % by the queue; only the pump's own percent ceiling is left.
        if (percent < 0) {
            result.isTempCancel(false).enacted(false).success(false).comment(app.aaps.core.ui.R.string.invalid_input)
            aapsLogger.error("setTempBasalPercent: Invalid input")
            ap550Pump.setIsManual(0)
            return result
        }
        if (percent > pumpDescription.maxTempPercent) percent = pumpDescription.maxTempPercent
        if (ap550Pump.isTempBasalInProgress && (ap550Pump.isManual == 1 || ap550Pump.tempBasalPercent == percent) && ap550Pump.tempBasalRemainingMin > 4 && !enforceNew) {
            result.enacted(false).success(true).isTempCancel(false)
                .comment(app.aaps.core.ui.R.string.ok)
                .duration(ap550Pump.tempBasalRemainingMin)
                .percent(ap550Pump.tempBasalPercent)
                .isPercent(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: Correct value already set")
            ap550Pump.setIsManual(0)
            return result
        }
        val durationInHours = max((durationInMinutes / 30).toDouble(), 1.0).toInt()
        val connectionOK = if (ap550Pump.isManual == 0) {
            sExecutionService!!.tempBasal(percent, durationInHours)
        } else {
            true
        }
        if (connectionOK && ap550Pump.isTempBasalInProgress && (ap550Pump.isManual == 1 || ap550Pump.tempBasalPercent == percent)) {
            result.enacted(true)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .isTempCancel(false)
                .duration(ap550Pump.tempBasalDuration.toInt())
                .percent(ap550Pump.tempBasalPercent)
                .isPercent(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: OK")
            pumpSync.syncTemporaryBasalWithPumpId(
                ap550Pump.tempBasalStart,
                PumpRate(ap550Pump.tempBasalPercent.toDouble()),
                ap550Pump.tempBasalDuration,
                false,
                tbrType,
                ap550Pump.tempBasalStart,
                pumpDescription.pumpType,
                serialNumber()
            )
            ap550Pump.setIsManual(0)
            return result
        }
        val error = errorCode2StringRes(ap550Pump.basalStartErrorCode, 2)
        result.enacted(false).success(false).comment(error)
        aapsLogger.error("setTempBasalPercent: Failed to set temp basal")
        ap550Pump.setIsManual(0)
        return result
    }

    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {

        // Read the pump's extended bolus state before sending a new one.
        sExecutionService!!.getPumpExtendedBolusStatus()

        var insulin = insulin
        // Already constrained in IU (queue) and in cU (PumpWithConcentration boundary); no re-apply here.
        // needs to be rounded
        val durationInHalfHours = max((durationInMinutes / 30).toDouble(), 1.0).toInt()
        insulin = Round.roundTo(insulin, pumpDescription.extendedBolusStep)

        val result = pumpEnactResultProvider()

        if (ap550Pump.isExtendedInProgress) {
            cancelExtendedBolus()
            if (ap550Pump.isExtendedInProgress) {
                result.enacted(false).success(false)
                aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus failed. aborting setExtendedBolus")
                return result
            }
        }

        if (Math.abs(ap550Pump.extendedBolusAmount - insulin) < pumpDescription.extendedBolusStep) {
            result.enacted(false)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .duration(ap550Pump.extendedBolusRemainingMinutes)
                .absolute(ap550Pump.extendedBolusAbsoluteRate)
                .isPercent(false)
                .isTempCancel(false)
            aapsLogger.debug(LTag.PUMP, ("setExtendedBolus: Correct extended bolus already set. Current: " + ap550Pump.extendedBolusAmount).toString() + " Asked: " + insulin)
            return result
        }

        val useExtended = preferences.get(Ap550BooleanNonKey.UseExtended)
        val connectionOK: Boolean = sExecutionService!!.extendedBolus(insulin, durationInHalfHours)
        if (connectionOK && ap550Pump.isExtendedInProgress && Math.abs(ap550Pump.extendedBolusAmount - insulin) < pumpDescription.extendedBolusStep) {
            result.enacted(true)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .isTempCancel(false)
                .duration(ap550Pump.extendedBolusRemainingMinutes)
                .absolute(ap550Pump.extendedBolusAbsoluteRate)
                .isPercent(false)
            if (!useExtended) result.bolusDelivered(ap550Pump.extendedBolusAmount)
            pumpSync.syncExtendedBolusWithPumpId(
                ap550Pump.extendedBolusStart,
                PumpRate(ap550Pump.extendedBolusAmount),
                ap550Pump.extendedBolusDuration,
                useExtended,
                ap550Pump.extendedBolusStart,
                pumpDescription.pumpType,
                serialNumber()
            )
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: OK")
            return result
        }
        val error = errorCode2StringRes(ap550Pump.extendedBolusStartErrorCode, 1)
        result.enacted(false).success(false).comment(error)
        // The dialog that issued this command may be gone by the time the pump answers, so it cannot show the
        // error itself. Post the delivery-failure card instead — the same card a failed normal bolus gets.
        notificationManager.post(
            NotificationId.BOLUS_DELIVERY_FAILED,
            rh.gs(error),
            sound = AlarmSound.BOLUS_ERROR
        )
        aapsLogger.error("setExtendedBolus: Failed to extended bolus")
        aapsLogger.error(((("inProgress: " + ap550Pump.isExtendedInProgress).toString() + " start: " + ap550Pump.extendedBolusStart).toString() + " amount: " + ap550Pump.extendedBolusAmount).toString() + " duration: " + ap550Pump.extendedBolusDuration)
        return result
    }

    override suspend fun cancelExtendedBolus(): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (ap550Pump.isExtendedInProgress) {
            sExecutionService!!.extendedBolusStop()
            if (!ap550Pump.isExtendedInProgress) {
                result.success(true).enacted(true).isTempCancel(true)
                pumpSync.syncStopExtendedBolusWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    pumpDescription.pumpType,
                    serialNumber()
                )
            } else result.success(false).enacted(false).isTempCancel(true).comment(app.aaps.core.ui.R.string.canceling_eb_failed)
        } else {
            pumpSync.syncStopExtendedBolusWithPumpId(
                dateUtil.now(),
                dateUtil.now(),
                pumpDescription.pumpType,
                serialNumber()
            )
            result.success(true).comment(app.aaps.core.ui.R.string.ok).isTempCancel(true)
            aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus: OK")
        }
        return result
    }

    override fun connect(reason: String) {
        if (sExecutionService != null && !sExecutionService!!.isConnected) {
            sExecutionService!!.connect(reason)
            pumpDescription.basalStep = ap550Pump.basalStep
            pumpDescription.bolusStep = ap550Pump.bolusStep
        }
    }

    override fun isConnected(): Boolean {
        return sExecutionService != null && sExecutionService!!.isConnected
    }

    override fun isConnecting(): Boolean {
        return sExecutionService != null && sExecutionService!!.isConnecting
    }

    override fun disconnect(reason: String) {
        // The QueueWorker asks for a disconnect with "Queue empty" once the queue is drained. The
        // ap550 BLE link is managed by the plugin itself (updatePumpData timer + EventAP550Reconnect)
        // and does not take part in the AAPS idle-disconnect policy. Otherwise every drained queue
        // would repeat the whole MTU exchange -> readVersion -> dynamic key -> pairing handshake.
        // All other reasons (switch pump / Application exit / bluetooth is off / handshake failed /
        // manual disconnect) disconnect as usual.
        if (reason.contains("Queue empty")) return
        if (sExecutionService != null) {
            sExecutionService!!.disconnect(reason)
        }
    }

    fun disconnectCommand() {
        sExecutionService!!.disconnectCommand()
    }

    fun disconnected(): Boolean {
        return sExecutionService != null && sExecutionService!!.disconnected()
    }

    fun disconnecting(): Boolean {
        return sExecutionService != null && sExecutionService!!.disconnecting()
    }

    fun stopResumePump(status: Int): PumpEnactResult {
        val result = pumpEnactResultProvider()
        val targetSuspended = status == 1
        val targetStatusText = if (targetSuspended) rh.gs(R.string.ap550_pump_action_suspend) else rh.gs(R.string.ap550_pump_action_resume)

        // Version check
        if (ap550Pump.pumpVersionString == (context.getString(R.string.ap550_version_before_DCN18) ?: "CY8CKIT-062 PSoC 6 BLE Pioneer Kit")) {
            aapsLogger.error(LTag.PUMP, "stopResumePump: pump version is too old, it does not support $targetStatusText")
            return result.success(false).enacted(false).comment(rh.gs(R.string.ap550_pump_version_too_low))
        }

        // Idempotence check on the current state
        if (ap550Pump.pumpSuspended == targetSuspended) {
            return result.success(true).enacted(false).comment(rh.gs(R.string.ap550_pump_already_in_status, targetStatusText))
        }

        if (sExecutionService == null) {
            aapsLogger.error(LTag.PUMP, "stopResumePump: ExecutionService is not initialized")
            return result.success(false).enacted(false).comment(rh.gs(R.string.ap550_execution_service_not_initialized))
        }

        // Remember when the request came in, so MsgInitConnStatusBasic can tell an AAPS request
        // apart from a manual action on the pump itself.
        if (targetSuspended) ap550Pump.AAPSStopButtomTime = System.currentTimeMillis()
        else ap550Pump.AAPSResumeButtomTime = System.currentTimeMillis()

        return sExecutionService!!.stopResumePump(status)
    }

    fun mutePump() {
        sExecutionService!!.mutePump()
    }

    override fun stopConnecting() {
        if (sExecutionService != null) sExecutionService!!.stopConnecting()
    }

    /**
     * Waits until the running connect attempt has finished (link up or attempt given up).
     *
     * [connect] returns before the link is up - the waits inside the attempt run on a background
     * thread in the service, so the command queue is not blocked. A caller that needs the result
     * right away, like the pairing wizard, waits for the attempt this way. Blocks the calling
     * thread, so only call it from a background dispatcher.
     */
    fun joinConnectAttempt() {
        sExecutionService?.joinConnectAttempt()
    }

    override suspend fun getPumpStatus(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Test_ble_connect getPumpStatus reason: $reason")
        if (sExecutionService != null && sExecutionService!!.isConnected) {
            aapsLogger.debug(LTag.PUMP, "Test_pumpStatus Getting Pump Status")
            sExecutionService!!.pumpStatus
            pumpDescription.basalStep = ap550Pump.basalStep
            pumpDescription.bolusStep = ap550Pump.bolusStep
        }
    }

    fun initPairing(): Boolean {
        ap550Pump.initPair = false
        val msgInitConnPairingMsg = sdkMessageFactory.msgInitConnPairing(0)
        sExecutionService!!.sendMessage(msgInitConnPairingMsg)
        return ap550Pump.initPair
    }

    fun versionPairCheck(): Boolean {
        return sExecutionService!!.checkPumpVersion()
    }

    fun sendPairingCode(pairingCode: String): Boolean {
        aapsLogger.debug(LTag.PUMP, "sendPairingCode: $pairingCode")
        if (!StringUtils.isEmpty(pairingCode) && pairingCode.length == 6) {
            val value = pairingCode.toInt()
            val msgInitConnPairingCode = sdkMessageFactory.msgInitConnPairingCode(value)
            sExecutionService!!.sendMessage(msgInitConnPairingCode)
            val msgHostId = sdkMessageFactory.msgHostId(getAndroidId())
            sExecutionService!!.sendMessage(msgHostId)
        } else {
            return false
        }
        return ap550Pump.paired
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.ST
    }

    override fun serialNumber(): String {
        return ap550Pump.serialNumber
    }

    /**
     * AP550s interface
     */
    override fun loadHistory(type: Byte): PumpEnactResult {
        return sExecutionService!!.loadHistory(type)
    }

    // Read the pump data during a passive poll
    fun triggerFetchPumpTdd(): Boolean = sExecutionService?.fetchPumpTddToRawSp() ?: false

    /**
     * Constraint interface
     */
    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfSmaller(ap550Pump.maxBasal, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, ap550Pump.maxBasal, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        percentRate.setIfGreater(0, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, 0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        percentRate.setIfSmaller(pumpDescription.maxTempPercent, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, pumpDescription.maxTempPercent, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)

        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {

        // Current time
        val now = dateUtil.now()
        // Timestamp four hours ago
        val fourHoursAgo = now - 4 * 60 * 60 * 1000
        // Timestamp of midnight of the current day
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val midnight = calendar.timeInMillis

        // The interface method is not suspend, but the bolus history it needs is. Reading it here
        // blocks the caller for the length of a database query — the same trade-off
        // AutotuneIob.initializeTreatmentData makes.
        val (normalBolusLast4Hours, normalBolusToday) = runBlocking {
            persistenceLayer.getBolusesFromTimeToTime(fourHoursAgo, now, true).sumOf { it.amount } to
                persistenceLayer.getBolusesFromTimeToTime(midnight, now, true).sumOf { it.amount }
        }
        aapsLogger.debug(LTag.PUMP, "Normalbolus last 4 hours: $normalBolusLast4Hours")

        // Square and dual wave bolus total of the last 4 hours
        val extendedBolusLast4Hours = calculateExtendedBolusAmount(fourHoursAgo, now)
        aapsLogger.debug(LTag.PUMP, "Extendedbolus last 4 hours: $extendedBolusLast4Hours")

        // Total bolus of the last 4 hours
        val bolusLast4Hours = normalBolusLast4Hours + extendedBolusLast4Hours
        aapsLogger.debug(LTag.PUMP, "Bolus last 4 hours: $bolusLast4Hours")

        // Square and dual wave bolus total since midnight
        val extendedBolusToday = calculateExtendedBolusAmount(midnight, now)
        aapsLogger.debug(LTag.PUMP, "Extendedbolus today: $extendedBolusToday")

        // Normal bolus total since midnight
        val bolusToday = normalBolusToday + extendedBolusToday
        aapsLogger.debug(LTag.PUMP, "Bolus today: $bolusToday")

        // 4-hour limit and 24-hour limit
        val maxBolus4Hours = max(0.0, ap550Pump.maxBolus * 2 - bolusLast4Hours)
        val maxBolusToday = max(0.0, ap550Pump.maxBolus * 4 - bolusToday)
        aapsLogger.debug(LTag.PUMP, "Max bolus 4 hours: $maxBolus4Hours")
        aapsLogger.debug(LTag.PUMP, "Max bolus today: $maxBolusToday")

        insulin.setIfSmaller(maxBolus4Hours, rh.gs(app.aaps.core.ui.R.string.limitingbolus, maxBolus4Hours, rh.gs(R.string.ap550_limit_bolus_4h)), this)
        insulin.setIfSmaller(maxBolusToday, rh.gs(app.aaps.core.ui.R.string.limitingbolus, maxBolus4Hours, rh.gs(R.string.ap550_limit_bolus_today)), this)
        insulin.setIfSmaller(ap550Pump.maxBolus, rh.gs(app.aaps.core.ui.R.string.limitingbolus, ap550Pump.maxBolus, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return insulin
    }

    // cU-domain pump limit (PumpPluginConstraints): folded into the IU scan by ConstraintsChecker.
    override fun applyBasalConstraints(absoluteRate: PumpRate): PumpRate =
        PumpRate(absoluteRate.cU.coerceAtMost(ap550Pump.maxBasal))

    override fun applyBolusConstraints(insulin: PumpInsulin): PumpInsulin =
        PumpInsulin(insulin.cU.coerceAtMost(ap550Pump.maxBolus))

    override fun applyExtendedBolusConstraints(insulin: PumpInsulin): PumpInsulin = applyBolusConstraints(insulin)

    fun calculateExtendedBolusAmount(startTime: Long, endTime: Long): Double {
        val oldest = endTime - 32 * 60 * 60 * 1000 // Records after `oldest`: now minus 24 h + 8 h, the longest square wave bolus
        val extendedBolusList = runBlocking { persistenceLayer.getExtendedBolusesAfterTimeByEndTime(startTime, oldest, true) }
        var extendedBolusAmount = if (extendedBolusList.size > 1) {
            // All but the last one, because those extended boluses are already finished
            extendedBolusList.take(extendedBolusList.size - 1).sumOf { it.amount }
        } else {
            0.0
        }
        val lastExtendedBolusAmount = if (extendedBolusList.isNotEmpty()) {
            val firstEb = extendedBolusList.first()
            val lastEb = extendedBolusList.last()
            aapsLogger.debug(LTag.PUMP, "First extendedbolus timestamp: ${firstEb.timestamp}" + "Last extendedbolus timestamp: ${lastEb.timestamp}")

            // Share of the duration that has already run
            val elapsedMs = endTime - lastEb.timestamp
            val durationMs = lastEb.duration
            val ratio = if (durationMs > 0) minOf(elapsedMs.toDouble() / durationMs, 1.0) else 0.0
            lastEb.amount * ratio
        } else {
            0.0
        }
        extendedBolusAmount += lastExtendedBolusAmount

        return extendedBolusAmount
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return applyBolusConstraints(insulin)
    }

    override suspend fun loadTDDs(): PumpEnactResult {
        return loadHistory(RecordTypes.RECORD_TYPE_DAILY)
    }

    // Reply for sms communicator - pump specific part; common part is generated by PumpStatusProviderImpl
    override fun pumpSpecificShortStatus(veryShort: Boolean): String {
        var ret = ""
        if (!veryShort) {
            ret += "TDD: " + decimalFormatter.to0Decimal(ap550Pump.dailyTotalUnits) + " / " + ap550Pump.maxDailyTotalUnits + " U\n"
        }
        ret += "Reserv: " + decimalFormatter.to0Decimal(ap550Pump.reservoirRemainingUnits) + "U"
        return ret
    }

    // TODO: daily total constraint
    override fun getCustomActions(): List<CustomAction>? {
        return null
    }

    override fun canHandleDST(): Boolean {
        return false
    }

    override suspend fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        sExecutionService!!.timeZoneChanged = true
    }

    override fun clearPairing() {
    }

    override fun isInitialized(): Boolean {
        aapsLogger.debug(LTag.PUMP,
            "Pump initialization check: ${ap550Pump.lastConnection > 0 && ap550Pump.maxBasal > 0 && !ap550Pump.isConfigUD && !ap550Pump.isEasyModeEnabled && ap550Pump.isExtendedBolusEnabled && ap550Pump.isPasswordOK}, " +
                    "lastConnection: ${dateUtil.dateAndTimeString(ap550Pump.lastConnection)}, " +
                    "maxBasal: ${ap550Pump.maxBasal}, " +
                    "isConfigUD: ${ap550Pump.isConfigUD}, " +
                    "isEasyModeEnabled: ${ap550Pump.isEasyModeEnabled}, " +
                    "isExtendedBolusEnabled: ${ap550Pump.isExtendedBolusEnabled}, " +
                    "isPasswordOK: ${ap550Pump.isPasswordOK}"
        )
        return isConfigured() &&
                ap550Pump.lastConnection > 0 &&
                ap550Pump.maxBasal > 0 &&
                !ap550Pump.isConfigUD &&
                !ap550Pump.isEasyModeEnabled &&
                ap550Pump.isExtendedBolusEnabled &&
                ap550Pump.isPasswordOK
    }

    override fun isHandshakeInProgress(): Boolean {
        return sExecutionService != null && sExecutionService!!.isHandshakeInProgress
    }

    override fun finishHandshaking() {
        sExecutionService?.finishHandshaking()
    }

    private fun errorCode2StringRes(errorCode: Int, treatmentType: Int): Int {
        return connectUtil.errorCode2StringRes(errorCode, treatmentType)
    }

    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // This is the CommandBolus path: calling it always triggers the hypoglycemia alert check
        rxBus.send(EventHypoglycemiaAlertCheck())

        if (detailedBolusInfo.insulin == 0.0 || detailedBolusInfo.carbs > 0) {
            throw IllegalArgumentException(detailedBolusInfo.toString(), Exception())
        }
        // Already constrained in IU (queue) and in cU (PumpWithConcentration boundary); no re-apply here.
        var connectionOK = false
        if (detailedBolusInfo.insulin > 0)
            connectionOK = sExecutionService?.bolus(
                detailedBolusInfo.insulin,
                detailedBolusInfo.carbs.toInt(),
                (detailedBolusInfo.carbsTimestamp ?: detailedBolusInfo.timestamp),
                detailedBolusInfo.id,
                detailedBolusInfo.bolusType
            ) == true
        val delivered = bolusProgressData.state.value?.delivered ?: PumpInsulin(0.0)
        val result = pumpEnactResultProvider()
        result.success(connectionOK && abs(detailedBolusInfo.insulin - delivered.cU) < pumpDescription.bolusStep)
            .bolusDelivered(delivered.cU)
        if (!result.success) {
            val error: String
            if (ap550Pump.injectingErrorCode != 0) {
                error = rh.gs(R.string.ap550_bolus_delivery_error, rh.gs(errorCode2StringRes(ap550Pump.injectingErrorCode, 1)))
            } else {
                if (ap550Pump.bolusStartErrorCode == 12) {   // Communication error
                    error = rh.gs(R.string.ap550_bolus_comm_error)
                    sExecutionService?.isBolusStart = false
                    aapsLogger.debug(LTag.PUMP, "deliverTreatment: communication error, isBolusStart=${sExecutionService?.isBolusStart}")
                } else if (ap550Pump.bolusStartErrorCode == 9) {
                    error = rh.gs(R.string.ap550_bolus_exceeds_pump_max, String.format("%.2f", ap550Pump.maxBolus))
                } else {
                    error = rh.gs(R.string.ap550_bolus_delivery_error, rh.gs(errorCode2StringRes(ap550Pump.bolusStartErrorCode, 1)))
                }
            }
            result.comment(error)
        } else {
            result.comment(app.aaps.core.ui.R.string.ok)
        }
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        detailedBolusInfo.insulin = delivered.cU
        detailedBolusInfo.timestamp = dateUtil.now()

        // Two ways to handle the pump bolus record after AAPS delivers a bolus:
        // (1) DCN18 and newer: pull the record from the pump
        // (2) Older than DCN18: keep the old way and store it ourselves, which can duplicate a bolus
        if (ap550Pump.pumpVersionString != rh.gs(R.string.ap550_version_before_DCN18)) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "DCN18 pump, pumpVersionString=${ap550Pump.pumpVersionString}")
            // Do not send EventDismissBolusProgressIfRunning here: CommandBolus sends the same event
            // after bolusEnded = true. Sending it early opens a window where the progress dialog is
            // closed but bolusEnded is still false, and OverviewFragment.onResume shows the dialog
            // again for a moment.
            aapsLogger.debug(LTag.PUMP, "TestBolusRecord " +
                    "pumpBolusTime = ${dateUtil.dateAndTimeAndSecondsString(ap550Pump.bolusProgressLastTimeStamp)}, " +
                    "detailedBolusInfo.time = ${dateUtil.dateAndTimeAndSecondsString(detailedBolusInfo.timestamp)}")
            rxBus.send(EventBolusStart(false))   // Bolus delivery finished
            // Pull the bolus record from the pump
            sExecutionService?.bolusRecord("after AAPS delivered a bolus")

        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "pump older than DCN18, pumpVersionString=${ap550Pump.pumpVersionString}")
            if (detailedBolusInfo.insulin > 0 && ap550Pump.bolusStartErrorCode != 12) {  // Do not store on a communication error
                aapsLogger.debug(LTag.PUMP, "TestBolusRecord, " +
                        "pumpBolusTime = ${dateUtil.dateAndTimeAndSecondsString(ap550Pump.bolusProgressLastTimeStamp)}, " +
                        "detailedBolusInfo.time = ${dateUtil.dateAndTimeAndSecondsString(detailedBolusInfo.timestamp)}")
                pumpSync.syncBolusWithPumpId(
                    detailedBolusInfo.timestamp + 15 * 1000,  // Add 15 s, so the time we send is not older than the bolus time stored on the pump
                    PumpInsulin(detailedBolusInfo.insulin),
                    detailedBolusInfo.bolusType,
                    dateUtil.now(),
                    PumpType.AP550S,
                    serialNumber()
                )
                rxBus.send(EventBolusStart(false));   // Bolus delivery finished
            }
        }

        return result
    }

    fun getBolusRecord() {
        // Pull the bolus record from the pump
        sExecutionService?.bolusRecord("after the pump delivered a bolus")
    }

    fun getAndroidId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: ""
    }

    // This is called from APS
    override suspend fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        var absoluteRate = absoluteRate
        val result = pumpEnactResultProvider()
        result.isPercent(false)

        // Already constrained in IU (queue) and in cU (PumpWithConcentration boundary); no re-apply here.

        val doTempOff = baseBasalRate.cU - absoluteRate == 0.0 && absoluteRate >= 0.10
        val doLowTemp = absoluteRate < baseBasalRate.cU || absoluteRate < 0.10
        val doHighTemp = absoluteRate > baseBasalRate.cU && !isFakingTempsByExtendedBoluses
        val doExtendedTemp = absoluteRate > baseBasalRate.cU && isFakingTempsByExtendedBoluses

        if (doTempOff) {
            ap550Pump.setIsManual(0)
            // If extended in progress
            if (ap550Pump.isExtendedInProgress && isFakingTempsByExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doTempOff)")
                return cancelExtendedBolus()
            }
            // If temp in progress
            if (ap550Pump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)")
                return cancelRealTempBasal()
            }
            result.success(true).enacted(false).absolute(baseBasalRate.cU).isPercent(false).isTempCancel(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK")
            return result
        }
        if (doLowTemp || doHighTemp) {
            // If extended in progress
            if (ap550Pump.isExtendedInProgress && isFakingTempsByExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)")
                val result = cancelExtendedBolus()
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)")
                    ap550Pump.setIsManual(0)
                    return result
                }
            }
            // Check if some temp is already in progress
            if (ap550Pump.isTempBasalInProgress) {
                // Correct basal already set ?
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: currently running: " + ap550Pump.temporaryBasalToString())
                if ((ap550Pump.isManual == 1 || ap550Pump.tempBasalAbsolute == absoluteRate) && ap550Pump.tempBasalRemainingMin > 4 && !enforceNew) {
                    result.success(true).absolute(absoluteRate)
                        .enacted(false)
                        .duration(ap550Pump.tempBasalRemainingMin)
                        .isPercent(false)
                        .isTempCancel(false)
                    aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)")
                    ap550Pump.setIsManual(0)
                    return result
                }
            }
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal " + absoluteRate + "U/h for " + durationInMinutes + " minutes (doLowTemp || doHighTemp)")
            val result = setTempBasalAbsoluteCore(absoluteRate, durationInMinutes, enforceNew, tbrType)
            ap550Pump.setIsManual(0)
            return result
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (ap550Pump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doExtendedTemp)")
                val result = cancelRealTempBasal()
                result.absolute(absoluteRate).isPercent(false)
                // Check for proper result
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)")
                    ap550Pump.setIsManual(0)
                    return result
                }
            }

            // Calculate # of halfHours from minutes
            val durationInHalfHours = max((durationInMinutes / 30).toDouble(), 1.0).toInt()
            // We keep current basal running so need to sub current basal
            var extendedRateToSet = absoluteRate - baseBasalRate.cU
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, pumpDescription.extendedBolusStep * 2) // *2 because of half hours

            // What is current rate of extended bolusing in u/h?
            aapsLogger.debug(
                LTag.PUMP,
                ((("setTempBasalAbsolute: Extended bolus in progress: " + (ap550Pump.isExtendedInProgress)) + " rate: " + ap550Pump.extendedBolusAbsoluteRate) + "U/h duration remaining: " + ap550Pump.extendedBolusRemainingMinutes) + "min"
            )
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h")

            // Compare with extended rate in progress
            if (ap550Pump.isExtendedInProgress && Math.abs(ap550Pump.extendedBolusAbsoluteRate - extendedRateToSet) < pumpDescription.extendedBolusStep) {
                // correct extended already set
                result.success(true).absolute(ap550Pump.extendedBolusAbsoluteRate).enacted(false).duration(ap550Pump.extendedBolusRemainingMinutes).isPercent(false).isTempCancel(false)
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct extended already set")
                ap550Pump.setIsManual(0)
                return result
            }

            // Now set new extended, no need to stop previous (if running) because it's replaced
            val extendedAmount = extendedRateToSet / 2 * durationInHalfHours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  half hours: " + durationInHalfHours)
            val result = setExtendedBolus(extendedAmount, durationInMinutes)
            if (!result.success) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set extended bolus")
                ap550Pump.setIsManual(0)
                return result
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Extended bolus set ok")
            result.absolute(result.absolute + baseBasalRate.cU)
            ap550Pump.setIsManual(0)
            return result
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error")
        result.success(false).comment("Internal error")
        ap550Pump.setIsManual(0)
        return result
    }

    private suspend fun setTempBasalAbsoluteCore(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        var absoluteRate = absoluteRate
        val result = pumpEnactResultProvider()
        if (absoluteRate < 0) {
            result.isTempCancel(false).enacted(false).success(false).comment(app.aaps.core.ui.R.string.invalid_input)
            aapsLogger.error("setTempBasalPercent: Invalid input")
            return result
        }
        if (absoluteRate > pumpDescription.maxTempAbsolute) absoluteRate = pumpDescription.maxTempAbsolute
        if (ap550Pump.isTempBasalInProgress && (ap550Pump.isManual == 1 || ap550Pump.tempBasalAbsolute == absoluteRate) && ap550Pump.tempBasalRemainingMin > 4 && !enforceNew) {
            result.enacted(false).success(true).isTempCancel(false)
                .comment(app.aaps.core.ui.R.string.ok)
                .duration(ap550Pump.tempBasalRemainingMin)
                .absolute(ap550Pump.tempBasalAbsolute)
                .isPercent(false)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: Correct value already set")
            return result
        }
        val durationInHours = max((durationInMinutes / 30).toDouble(), 1.0).toInt()
        val connectionOK = if (ap550Pump.isManual == 0) {
            sExecutionService!!.tempBasalAbsolute(absoluteRate, durationInHours)
        } else {
            true
        }
        if (connectionOK && ap550Pump.isTempBasalInProgress && (ap550Pump.isManual == 1 || ap550Pump.tempBasalAbsolute == absoluteRate)) {
            result.enacted(true)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .isTempCancel(false)
                .duration(ap550Pump.tempBasalDuration.toInt())
                .absolute(ap550Pump.tempBasalAbsolute)
                .isPercent(false)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: OK")
            pumpSync.syncTemporaryBasalWithPumpId(
                ap550Pump.tempBasalStart,
                PumpRate(ap550Pump.tempBasalAbsolute),
                ap550Pump.tempBasalDuration,
                true,
                tbrType,
                ap550Pump.tempBasalStart,
                pumpDescription.pumpType,
                serialNumber()
            )
            ap550Pump.setIsManual(0)
            return result
        }
        val error = errorCode2StringRes(ap550Pump.basalStartErrorCode, 2)
        result.enacted(false).success(false).comment(error)
        aapsLogger.error("setTempBasalAbsolute: Failed to set temp basal")
        ap550Pump.setIsManual(0)
        return result
    }

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (ap550Pump.isTempBasalInProgress) return cancelRealTempBasal()
        else {
            pumpSync.syncStopTemporaryBasalWithPumpId(
                dateUtil.now(),
                dateUtil.now(),
                pumpDescription.pumpType,
                serialNumber()
            )
        }
        if (ap550Pump.isExtendedInProgress && isFakingTempsByExtendedBoluses) {
            return cancelExtendedBolus()
        }
        val result = pumpEnactResultProvider()
        result.success(true).enacted(false).comment(app.aaps.core.ui.R.string.ok).isTempCancel(true)
        return result
    }

    override fun model(): PumpType {
        return PumpType.AP550S
    }

    private suspend fun cancelRealTempBasal(): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (ap550Pump.isTempBasalInProgress) {
            sExecutionService?.tempBasalStop()
            if (!ap550Pump.isTempBasalInProgress) {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    pumpDescription.pumpType,
                    serialNumber()
                )
                result.success(true).enacted(true).isTempCancel(true).comment(app.aaps.core.ui.R.string.ok)
            } else result.success(false).enacted(false).isTempCancel(true).comment(app.aaps.core.ui.R.string.canceling_eb_failed)
        } else {
            result.success(true).isTempCancel(true).comment(app.aaps.core.ui.R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK")
        }
        return result
    }

    // When a profile is set in AAPS, stop the AAPS temp basal as well, so AAPS stays
    // in step with what the 550S pump does.
    private fun activeProfileCancelTempBasal() {
        pluginScope.launch {
            pumpSync.syncStopTemporaryBasalWithPumpId(
                dateUtil.now(),
                dateUtil.now(),
                pumpDescription.pumpType,
                serialNumber()
            )
        }
    }

    override fun loadEvents(): PumpEnactResult = pumpEnactResultProvider() // no history, not needed
    override fun setUserOptions(): PumpEnactResult = pumpEnactResultProvider()

    override fun clearAllTables() {
        ap550HistoryDatabase.clearAllTables()
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "ap550_settings",
        titleResId = R.string.ap_550,
        // The unpair row hides itself through Ap550IntentKey.Unpair.visibility when no pump is
        // paired, so it is always part of the list here.
        items = Ap550BooleanKey.entries.toList() + Ap550IntentKey.Unpair.withClick { unpairPump() },
        icon = pluginDescription.icon
    )
}