package app.aaps.pump.aaruy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.PumpPluginConstraints
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcPluginAaruy
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.compose.AaruyComposeContent
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.events.EventAaruyDeviceChange
import app.aaps.pump.aaruy.keys.AaruyBooleanKey
import app.aaps.pump.aaruy.keys.AaruyDoubleKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyLongKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyBolusOutcome
import app.aaps.pump.aaruy.services.AaruyService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

// Pump plugin registration: @IntKey range 1000-1200, see AppRootGraph for the overview
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@PumpDriver
@IntKey(1195)
@SingleIn(AppScope::class)
@Inject
class AaruyPlugin(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val context: Context,
    private val aaruyPump: AaruyPump,
    private val detailedBolusInfoStorage: DetailedBolusInfoStorage,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val dateUtil: DateUtil,
    private val aaruyHistoryDatabase: AaruyHistoryDatabase,
    private val pumpEnactResultProvider: () -> PumpEnactResult,
    private val aaruyBle: AaruyBLE,
    notificationManager: NotificationManager
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ -> AaruyComposeContent() }
        .icon(IcPluginAaruy)
        .pluginName(TextRef.AndroidRes(R.string.aaruy_pump))
        .description(TextRef.AndroidRes(R.string.aaruy_pump_description)),
    ownPreferences = AaruyStringKey.entries + AaruyIntKey.entries + AaruyLongKey.entries + AaruyDoubleKey.entries + AaruyBooleanKey.entries,
    aapsLogger, rh, preferences, commandQueue, notificationManager
), Pump, PumpPluginConstraints, OwnDatabasePlugin {

    private var scope: CoroutineScope? = null
    private var aaruyService: AaruyService? = null
    private var mDeviceAddress = ""
    var mDeviceName = ""

    override suspend fun onStart() {
        super.onStart()
        aaruyPump.loadVarsFromSP()
        context.bindService(Intent(context, AaruyService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        // UNDISPATCHED because RxBus has no replay, so a scheduled collector could miss an event sent before it starts.
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { context.unbindService(mConnection) }
        rxBus.toFlow(EventAaruyDeviceChange::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { changePump() }
        aaruyBle.startEmulatorIfActive()
        changePump() // load device name
    }

    override suspend fun onStop() {
        scope?.cancel()
        scope = null
        context.unbindService(mConnection)
        // onServiceDisconnected is not called after unbindService.
        aaruyService = null
        super.onStop()
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            aaruyService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            aaruyService = (service as AaruyService.LocalBinder).serviceInstance
        }
    }

    suspend fun changePump() {
        mDeviceAddress = preferences.get(AaruyStringKey.MacAddress)
        mDeviceName = preferences.get(AaruyStringKey.PumpName)
        aaruyPump.pumpSN = mDeviceName
        aaruyPump.reset()
        if (isConfigured()) commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed))
    }

    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Aaruy connect from: $reason")
        mDeviceAddress = preferences.get(AaruyStringKey.MacAddress)
        mDeviceName = preferences.get(AaruyStringKey.PumpName)
        if (mDeviceAddress != "" && mDeviceName != "") aaruyService?.connect(reason, mDeviceAddress)
    }

    override fun isConnected(): Boolean = aaruyService?.isConnected == true
    override fun isConnecting(): Boolean = aaruyService?.isConnecting == true
    override fun isHandshakeInProgress(): Boolean = false

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Aaruy disconnect from: $reason")
        aaruyService?.disconnect(reason)
    }

    override fun stopConnecting() {
        aaruyService?.stopConnecting()
    }

    override suspend fun getPumpStatus(reason: String) {
        aaruyService?.readPumpStatus()
    }

    override fun isConfigured(): Boolean =
        preferences.get(AaruyStringKey.MacAddress).isNotEmpty() && preferences.get(AaruyStringKey.PumpName).isNotEmpty()

    override fun isInitialized(): Boolean = isConfigured() && aaruyPump.pumpState != AaruyPumpState.FIRST_INIT

    override fun isSuspended(): Boolean =
        aaruyPump.pumpState < AaruyPumpState.BASALRATE_RUNNING || aaruyPump.pumpState > AaruyPumpState.PUSHFORWARD_RUNNING

    override fun isBusy(): Boolean = false

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (!isInitialized()) {
            // Deferred and not a real error: it is sent again at reconnect. Profile notifications are owned by the command queue.
            result.success = true
            result.comment = rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            return result
        }
        return if (aaruyService?.updateBasalsInPump(profile) == true) {
            result.success = true
            result.enacted = true
            result.comment = "OK"
            result
        } else {
            result.comment = rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)
            result
        }
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (!isInitialized()) return true
        return aaruyPump.isBasalInPump(profile)
    }

    override fun deliveredBasal(): List<Profile.ProfileValue>? = aaruyPump.deliveredBasal()

    override val baseBasalRate: PumpRate
        get() = PumpRate(if (aaruyPump.lastBasalRate < 0.025) 0.025 else aaruyPump.lastBasalRate)
    override val reservoirLevel: StateFlow<PumpInsulin> = aaruyPump.reservoirFlow.mapState(::PumpInsulin)
    override val batteryLevel: StateFlow<Int?> = aaruyPump.remainBatteryFlow.mapState { it.toInt() }
    override val lastDataTime: StateFlow<Long> = aaruyPump.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> = aaruyPump.lastBolusTimeFlow.mapState { if (it == 0L) null else it }
    override val lastBolusAmount: StateFlow<PumpInsulin?> = aaruyPump.lastBolusAmountFlow.mapState { if (it == 0.0) null else PumpInsulin(it) }

    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        // Already limited by the command queue and PumpPluginConstraints.
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        val outcome = aaruyService?.setBolus(detailedBolusInfo) ?: AaruyBolusOutcome.NOT_STARTED
        val result = pumpEnactResultProvider()
        result.success = outcome == AaruyBolusOutcome.FINISHED
        result.enacted = outcome != AaruyBolusOutcome.NOT_STARTED
        result.bolusDelivered = aaruyPump.bolusDelivered
        result.comment = when (outcome) {
            AaruyBolusOutcome.FINISHED    -> rh.gs(app.aaps.core.ui.R.string.ok)
            AaruyBolusOutcome.NOT_STARTED -> rh.gs(R.string.alarm_bolus_run_fail)
            AaruyBolusOutcome.UNKNOWN     -> rh.gs(R.string.aaruy_bolus_outcome_unknown, rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, result.bolusDelivered))
        }
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: $outcome. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        return result
    }

    override fun stopBolusDelivering() {
        if (!isInitialized()) return

        aapsLogger.info(LTag.PUMP, "stopBolusDelivering")
        // The stop waits for the answer of the pump, and the caller can be the main thread.
        (scope ?: CoroutineScope(Dispatchers.IO)).launch { aaruyService?.stopBolus() }
    }

    override suspend fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (!isInitialized()) {
            result.success = false
            result.enacted = false
            return result
        }

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - absoluteRate: $absoluteRate, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        temporaryBasalStorage.add(PumpSync.PumpState.TemporaryBasal(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), absoluteRate, true, tbrType, 0L, 0L))
        val connectionOK = aaruyService?.setTempBasal(absoluteRate, durationInMinutes) == true
        return if (connectionOK
            && aaruyPump.isTempBasalInProgress
            && abs(aaruyPump.tempBasalAbsoluteRate - absoluteRate) <= 0.025
        ) {
            result.success = true
            result.enacted = true
            result.duration = durationInMinutes
            result.absolute = aaruyPump.tempBasalAbsoluteRate
            result.isPercent = false
            result.isTempCancel = false
            result
        } else {
            aapsLogger.error(
                LTag.PUMP,
                "setTempBasalAbsolute failed, connectionOK: $connectionOK, tempBasalInProgress: ${aaruyPump.isTempBasalInProgress}, " +
                    "tempBasalAbsoluteRate: ${aaruyPump.tempBasalAbsoluteRate}, pumpRate is $absoluteRate"
            )
            result.success = false
            result.enacted = false
            result.comment = "Aaruy setTempBasalAbsolute failed"
            result
        }
    }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalPercent - percent: $percent, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        val result = pumpEnactResultProvider()
        result.success = false
        result.enacted = false
        result.comment = "Aaruy driver does not support percentage temp basals"
        return result
    }

    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (!isInitialized()) {
            result.success = false
            result.enacted = false
            return result
        }

        if (aaruyPump.pumpType() == PumpType.AARUY_BASE) {
            aapsLogger.info(LTag.PUMP, "setExtendedBolus - insulin: $insulin, durationInMinutes: $durationInMinutes")
            result.success = false
            result.enacted = false
            result.comment = "Aaruy base driver does not support extended boluses"
            return result
        }

        // Already limited by the command queue and PumpPluginConstraints. Round to the step of the pump.
        val rounded = Round.roundTo(insulin, pumpDescription.extendedBolusStep)

        if (aaruyPump.isExtendedInProgress && abs(aaruyPump.extendedBolusAmount - rounded) < pumpDescription.extendedBolusStep) {
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + aaruyPump.extendedBolusAmount + " Asked: " + rounded)
            return pumpEnactResultProvider().enacted(false).success(true).comment(rh.gs(app.aaps.core.ui.R.string.ok)).duration(aaruyPump.extendedBolusRemainingMinutes)
                .absolute(aaruyPump.extendedBolusAbsoluteRate).isPercent(false).isTempCancel(false)
        }

        if (aaruyService?.extendedBolus(rounded, durationInMinutes) == true) {
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: OK")
            return pumpEnactResultProvider().enacted(true).success(true).comment(rh.gs(app.aaps.core.ui.R.string.ok)).duration(aaruyPump.extendedBolusRemainingMinutes)
                .absolute(aaruyPump.extendedBolusAbsoluteRate).bolusDelivered(aaruyPump.extendedBolusAmount).isPercent(false)
        }

        result.enacted = false
        result.success = false
        aapsLogger.error("setExtendedBolus: Failed to extended bolus")
        return result
    }

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider().success(false).enacted(false)

        aapsLogger.info(LTag.PUMP, "cancelTempBasal - enforceNew: $enforceNew")
        val connectionOK = aaruyService?.cancelTempBasal() == true
        return if (connectionOK && !aaruyPump.isTempBasalInProgress) {
            pumpEnactResultProvider().success(true).enacted(true).isTempCancel(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelTempBasal failed, connectionOK: $connectionOK, tempBasalInProgress: ${aaruyPump.isTempBasalInProgress}")
            pumpEnactResultProvider().success(false).enacted(false).comment("Aaruy cancelTempBasal failed")
        }
    }

    override suspend fun cancelExtendedBolus(): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider().success(false).enacted(false)

        val connectionOK = aaruyService?.cancelExtendedBolus() == true
        return if (connectionOK && !aaruyPump.isExtendedInProgress) {
            pumpEnactResultProvider().success(true).enacted(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelExtendedBolus failed, connectionOK: $connectionOK, isExtendedInProgress: ${aaruyPump.isExtendedInProgress}")
            pumpEnactResultProvider().success(false).enacted(false).comment("Aaruy extendedBolus failed")
        }
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Aaruy
    override fun model(): PumpType = aaruyPump.pumpType()
    override fun serialNumber(): String = aaruyPump.pumpSN
    override val pumpDescription = PumpDescription().fillFor(aaruyPump.pumpType())
    override val isFakingTempsByExtendedBoluses: Boolean = false

    override suspend fun loadTDDs(): PumpEnactResult {
        if (aaruyService?.isConnected != true || !aaruyPump.canSaveHistory) return pumpEnactResultProvider().success(false)
        aaruyService?.requestHistoryData()
        return pumpEnactResultProvider().success(true)
    }

    override fun canHandleDST(): Boolean = true

    override fun clearAllTables() = aaruyHistoryDatabase.clearAllTables()

    // cU-domain pump limits: folded into the IU scan by ConstraintsChecker.
    override fun applyBasalConstraints(absoluteRate: PumpRate): PumpRate = PumpRate(absoluteRate.cU.coerceAtMost(aaruyPump.maxBasal))
    override fun applyBolusConstraints(insulin: PumpInsulin): PumpInsulin = PumpInsulin(insulin.cU.coerceAtMost(aaruyPump.maxBolus))
    override fun applyExtendedBolusConstraints(insulin: PumpInsulin): PumpInsulin = applyBolusConstraints(insulin)
}
