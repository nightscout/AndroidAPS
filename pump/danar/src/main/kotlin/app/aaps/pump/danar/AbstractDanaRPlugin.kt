package app.aaps.pump.danar

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.PumpPluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.Dana
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round.roundTo
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginDanaI
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaBooleanKey
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.dana.keys.DanaIntNonKey
import app.aaps.pump.dana.keys.DanaIntentKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danar.compose.DanaRComposeContent
import app.aaps.pump.danar.services.AbstractDanaRExecutionService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by mike on 28.01.2018.
 */
abstract class AbstractDanaRPlugin protected constructor(
    protected var danaPump: DanaPump,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    protected val config: Config,
    commandQueue: CommandQueue,
    protected var aapsSchedulers: AapsSchedulers,
    protected var rxBus: RxBus,
    protected var activePlugin: ActivePlugin,
    protected var dateUtil: DateUtil,
    protected var pumpSync: PumpSync,
    protected var notificationManager: NotificationManager,
    protected var danaHistoryDatabase: DanaHistoryDatabase,
    protected var decimalFormatter: DecimalFormatter,
    protected var pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            DanaRComposeContent(
                pluginName = (activePlugin.activePumpInternal as? PluginBase)?.name ?: "",
                danaPump = danaPump
            )
        }
        .icon(IcPluginDanaI)
        .pluginName(app.aaps.pump.dana.R.string.danarpump)
        .shortName(app.aaps.pump.dana.R.string.danarpump_shortname)
        .description(app.aaps.pump.dana.R.string.description_pump_dana_r),
    ownPreferences = listOf(DanaStringNonKey::class.java, DanaIntKey::class.java, DanaIntNonKey::class.java, DanaBooleanKey::class.java, DanaIntentKey::class.java),
    aapsLogger, rh, preferences, commandQueue
), Pump, Dana, PumpPluginConstraints, OwnDatabasePlugin {

    protected var executionService: AbstractDanaRExecutionService? = null
    protected var disposable = CompositeDisposable()
    private var scope: CoroutineScope? = null
    override var pumpDescription = PumpDescription()
        protected set

    override suspend fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { danaPump.reset() }

        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        preferences.observe(DanaStringNonKey.RName).drop(1).onEach {
            danaPump.reset()
            pumpSync.connectNewPump(true)
            commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed))
        }.launchIn(newScope)
        danaPump.serialNumber = preferences.get(DanaStringNonKey.RName) // fill at start to allow password reset
    }

    override suspend fun onStop() {
        super.onStop()
        scope?.cancel()
        scope = null
        disposable.clear()
    }

    override fun isSuspended(): Boolean {
        return danaPump.pumpSuspended
    }

    override fun isBusy(): Boolean = false

    // Pump interface
    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (executionService == null) {
            aapsLogger.error("setNewBasalProfile sExecutionService is null")
            // Service not bound yet — deferred, not a genuine error; re-pushed on reconnect. success=true keeps
            // it out of the central failure alarm; enacted stays false so no PROFILE_SET_OK is posted.
            result.success(true).comment("setNewBasalProfile sExecutionService is null")
            return result
        }
        if (!isInitialized()) {
            aapsLogger.error("setNewBasalProfile not initialized")
            // Not initialized yet — deferred, not a genuine error; re-pushed on reconnect. success=true keeps it
            // out of the central failure alarm; enacted stays false so nothing is shown. Profile-set notifications
            // (OK / clear-failed) are owned centrally by CommandQueueImplementation.onProfileChanged.
            result.success(true).comment(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            return result
        }
        // updateBasalsInPump now returns false on a genuine pump rejection (see the DanaR*ExecutionService
        // return !msgSet.failed change), so success reflects the actual write outcome.
        if (executionService?.updateBasalsInPump(profile) != true) {
            // FAILED_UPDATE_PROFILE posted centrally (onProfileChanged) from success=false; comment carries the reason.
            result.comment(app.aaps.core.ui.R.string.failed_update_basal_profile)
        } else {
            result.success(true).enacted(true).comment("OK")
        }
        return result
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (!isInitialized()) return true
        if (danaPump.pumpProfiles == null) return true
        val basalValues = if (danaPump.basal48Enable) 48 else 24
        val basalIncrement = if (danaPump.basal48Enable) 30 * 60 else 60 * 60
        for (h in 0 until basalValues) {
            val pumpValue = danaPump.pumpProfiles!![danaPump.activeProfile][h]
            val profileValue = profile.getBasalTimeFromMidnight(h * basalIncrement)
            if (abs(pumpValue - profileValue) > pumpDescription.basalStep) {
                aapsLogger.debug(LTag.PUMP, "Diff found. Hour: $h Pump: $pumpValue Profile: $profileValue")
                return false
            }
        }
        return true
    }

    override val lastDataTime: StateFlow<Long> = danaPump.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> = danaPump.lastBolusTimeFlow
    override val baseBasalRate: PumpRate get() = PumpRate(danaPump.currentBasal)
    override val batteryLevel: StateFlow<Int?> = danaPump.batteryRemainingFlow

    override val lastBolusAmount: StateFlow<PumpInsulin?> = danaPump.lastBolusAmountFlow.mapState { it?.let(::PumpInsulin) }
    override val reservoirLevel: StateFlow<PumpInsulin> = danaPump.reservoirRemainingUnitsFlow.mapState(::PumpInsulin)

    override fun stopBolusDelivering() {
        if (executionService == null) {
            aapsLogger.error("stopBolusDelivering sExecutionService is null")
            return
        }
        executionService?.bolusStop()
    }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        var percentReq = percent
        val result = pumpEnactResultProvider.get()
        if (percentReq < 0) {
            result.isTempCancel(false).enacted(false).success(false).comment(app.aaps.core.ui.R.string.invalid_input)
            aapsLogger.error("setTempBasalPercent: Invalid input")
            return result
        }
        if (percentReq > pumpDescription.maxTempPercent) percentReq = pumpDescription.maxTempPercent
        if (danaPump.isTempBasalInProgress && danaPump.tempBasalPercent == percentReq && danaPump.tempBasalRemainingMin > 4 && !enforceNew) {
            result.enacted(false).success(true).isTempCancel(false)
                .comment(app.aaps.core.ui.R.string.ok)
                .duration(danaPump.tempBasalRemainingMin)
                .percent(danaPump.tempBasalPercent)
                .isPercent(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: Correct value already set")
            return result
        }
        val durationInHours = max(durationInMinutes / 60, 1)
        val connectionOK = executionService?.tempBasal(percentReq, durationInHours) == true
        if (connectionOK && danaPump.isTempBasalInProgress && danaPump.tempBasalPercent == percentReq) {
            result.enacted(true)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .isTempCancel(false)
                .duration(danaPump.tempBasalDuration.toInt())
                .percent(danaPump.tempBasalPercent)
                .isPercent(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: OK")
            pumpSync.syncTemporaryBasalWithPumpId(
                danaPump.tempBasalStart,
                PumpRate(danaPump.tempBasalPercent.toDouble()),
                danaPump.tempBasalDuration,
                false,
                tbrType,
                danaPump.tempBasalStart,
                pumpDescription.pumpType,
                serialNumber()
            )
            return result
        }
        result.enacted(false).success(false).comment(app.aaps.core.ui.R.string.temp_basal_delivery_error)
        aapsLogger.error("setTempBasalPercent: Failed to set temp basal")
        return result
    }

    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        // Already constrained in IU (queue) and in cU (PumpWithConcentration boundary); no re-apply here.
        val durationInHalfHours = max(durationInMinutes / 30, 1)
        // round to the pump's native extended-bolus step (cU)
        var insulinReq = roundTo(insulin, pumpDescription.extendedBolusStep)
        val result = pumpEnactResultProvider.get()
        if (danaPump.isExtendedInProgress && abs(danaPump.extendedBolusAmount - insulinReq) < pumpDescription.extendedBolusStep) {
            result.enacted(false)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .duration(danaPump.extendedBolusRemainingMinutes)
                .absolute(danaPump.extendedBolusAbsoluteRate)
                .isPercent(false)
                .isTempCancel(false)
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + danaPump.extendedBolusAmount + " Asked: " + insulinReq)
            return result
        }
        if (danaPump.isExtendedInProgress) {
            cancelExtendedBolus()
            if (danaPump.isExtendedInProgress) {
                result.enacted(false).success(false)
                aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus failed. aborting setExtendedBolus")
                return result
            }
        }
        val connectionOK = executionService?.extendedBolus(insulinReq, durationInHalfHours) == true
        if (connectionOK && danaPump.isExtendedInProgress && abs(danaPump.extendedBolusAmount - insulinReq) < pumpDescription.extendedBolusStep) {
            result.enacted(true)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .isTempCancel(false)
                .duration(danaPump.extendedBolusRemainingMinutes)
                .absolute(danaPump.extendedBolusAbsoluteRate)
                .isPercent(false)
            if (!preferences.get(DanaBooleanKey.UseExtended)) result.bolusDelivered(danaPump.extendedBolusAmount)
            pumpSync.syncExtendedBolusWithPumpId(
                danaPump.extendedBolusStart,
                PumpRate(danaPump.extendedBolusAmount),
                danaPump.extendedBolusDuration,
                preferences.get(DanaBooleanKey.UseExtended),
                danaPump.extendedBolusStart,
                pumpDescription.pumpType,
                serialNumber()
            )
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: OK")
            return result
        }
        result.enacted(false).success(false).comment(app.aaps.pump.dana.R.string.danar_valuenotsetproperly)
        aapsLogger.error("setExtendedBolus: Failed to extended bolus")
        aapsLogger.error("inProgress: " + danaPump.isExtendedInProgress + " start: " + danaPump.extendedBolusStart + " amount: " + danaPump.extendedBolusAmount + " duration: " + danaPump.extendedBolusDuration)
        return result
    }

    override suspend fun cancelExtendedBolus(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (danaPump.isExtendedInProgress) {
            executionService?.extendedBolusStop()
            if (!danaPump.isExtendedInProgress) {
                result.success(true).enacted(true).isTempCancel(true)
                pumpSync.syncStopExtendedBolusWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    pumpDescription.pumpType,
                    serialNumber()
                )
            } else result.success(false).enacted(false).isTempCancel(true).comment(app.aaps.core.ui.R.string.canceling_eb_failed)
        } else {
            result.success(true).comment(app.aaps.core.ui.R.string.ok).isTempCancel(true)
            aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus: OK")
        }
        return result
    }

    override fun isConfigured(): Boolean =
        preferences.get(DanaStringNonKey.RName).isNotEmpty()

    override fun connect(reason: String) {
        executionService?.connect()
        pumpDescription.basalStep = danaPump.basalStep
        pumpDescription.bolusStep = danaPump.bolusStep
    }

    override fun isConnected(): Boolean =
        executionService?.isConnected == true

    override fun isConnecting(): Boolean =
        executionService?.isConnecting == true

    override fun disconnect(reason: String) {
        executionService?.disconnect(reason)
    }

    override fun stopConnecting() {
        executionService?.stopConnecting()
    }

    override suspend fun getPumpStatus(reason: String) {
        executionService?.getPumpStatus()
        pumpDescription.basalStep = danaPump.basalStep
        pumpDescription.bolusStep = danaPump.bolusStep
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Sooil
    override fun serialNumber(): String = danaPump.serialNumber

    /**
     * DanaR interface
     */
    override fun loadHistory(type: Byte): PumpEnactResult =
        executionService?.loadHistory(type) ?: pumpEnactResultProvider.get()

    /**
     * Constraint interface
     */
    // cU-domain pump limit (PumpPluginConstraints): folded into the IU scan by ConstraintsChecker.
    override fun applyBasalConstraints(absoluteRate: PumpRate): PumpRate =
        PumpRate(absoluteRate.cU.coerceAtMost(danaPump.maxBasal))

    // cU-domain pump limit (PumpPluginConstraints): folded into the IU scan by ConstraintsChecker.
    override fun applyBolusConstraints(insulin: PumpInsulin): PumpInsulin =
        PumpInsulin(insulin.cU.coerceAtMost(danaPump.maxBolus))

    override fun applyExtendedBolusConstraints(insulin: PumpInsulin): PumpInsulin = applyBolusConstraints(insulin)

    override suspend fun loadTDDs(): PumpEnactResult {
        return loadHistory(RecordTypes.RECORD_TYPE_DAILY)
    }

    // TODO: daily total constraint
    override fun canHandleDST(): Boolean = false

    override fun clearPairing() {}
    override fun clearAllTables() {
        danaHistoryDatabase.clearAllTables()
    }
}
