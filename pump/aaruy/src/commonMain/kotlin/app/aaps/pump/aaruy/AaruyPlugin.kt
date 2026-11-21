package app.aaps.pump.aaruy

import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.constraints.PumpPluginConstraints
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.aaruy.compose.AaruyBleReadyCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
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
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcPluginAaruy
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.compose.AaruyComposeContent
import app.aaps.pump.aaruy.compose.AaruyHistoryTypes
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.events.EventAaruyDeviceChange
import app.aaps.pump.aaruy.keys.AaruyBooleanKey
import app.aaps.pump.aaruy.keys.AaruyDoubleKey
import app.aaps.pump.aaruy.keys.AaruyDoublePreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import app.aaps.pump.aaruy.keys.AaruyLongKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyAlerts
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyBolusOutcome
import app.aaps.pump.aaruy.services.AaruyCommands
import app.aaps.pump.aaruy.services.AaruyConfirmAlarmsCommand
import app.aaps.pump.aaruy.services.AaruyEmulatorTriggers
import app.aaps.pump.aaruy.services.AaruyReplaceSteps
import app.aaps.pump.aaruy.services.AaruyUnpairCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime

@SingleIn(AppScope::class)
@Inject
class AaruyPlugin(
    aapsLogger: AAPSLogger,
    override val rh: TextResolver,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val aaruyCommands: AaruyCommands,
    private val emulatorTriggers: AaruyEmulatorTriggers,
    private val aaruyPump: AaruyPump,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val dateUtil: DateUtil,
    private val aaruyHistoryDatabase: AaruyHistoryDatabase,
    private val pumpEnactResultProvider: () -> PumpEnactResult,
    private val aaruyBle: AaruyBLE,
    private val replaceSteps: AaruyReplaceSteps,
    private val aaruyAlerts: AaruyAlerts,
    private val bleReadyCheck: AaruyBleReadyCheck,
    private val pumpSync: PumpSync,
    private val ch: ConcentrationHelper,
    notificationManager: NotificationManager
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ -> AaruyComposeContent(rh.gs(AaruyStrings.aaruy_pump), bleReadyCheck) }
        .icon(IcPluginAaruy)
        .pluginName(AaruyStrings.aaruy_pump)
        .description(AaruyStrings.aaruy_pump_description),
    ownPreferences = AaruyStringKey.entries + AaruyIntKey.entries + AaruyLongKey.entries + AaruyDoubleKey.entries + AaruyBooleanKey.entries +
        AaruyDoublePreferenceKey.entries + AaruyIntPreferenceKey.entries,
    aapsLogger, rh, preferences, commandQueue, notificationManager
), Pump, PumpPluginConstraints, OwnDatabasePlugin {

    private var scope: CoroutineScope? = null
    private var mDeviceAddress = ""
    var mDeviceName = ""

    /** The adb hooks of the emulator are registered once per app run. */
    private var emulatorTriggersStarted = false

    override suspend fun onStart() {
        super.onStart()
        aaruyPump.loadVarsFromSP()
        // The stored pump log has the last bolus, so it is not kept as a setting.
        withContext(aapsIoDispatcher) { aaruyHistoryDatabase.historyUnitInfoDao().newest(AaruyHistoryTypes.BOLUS.toInt()) }?.let {
            aaruyPump.lastBolusTime = it.time
            aaruyPump.lastBolusAmount = it.data / 1000.0
        }
        val newScope = CoroutineScope(aapsIoDispatcher + SupervisorJob())
        scope = newScope
        aaruyCommands.start()
        // The alarms the pump still holds, from the store, with their first time (acceptance A4).
        aaruyAlerts.start(newScope)
        // UNDISPATCHED because RxBus has no replay, so a scheduled collector could miss an event sent before it starts.
        rxBus.toFlow(EventAaruyDeviceChange::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                pumpSync.connectNewPump()
                changePump(newPump = true)
            }
        // A changed setting reaches the pump through the status read, which compares and writes.
        merge(
            *AaruyDoublePreferenceKey.entries.map { preferences.observe(it).drop(1) }.toTypedArray(),
            *AaruyIntPreferenceKey.entries.map { preferences.observe(it).drop(1) }.toTypedArray()
        )
            .onEach { if (isConfigured()) commandQueue.readStatus(rh.gs(AaruyStrings.param_refresh)) }
            .launchIn(newScope)
        aaruyBle.start()
        // Only with the emulator, which the `emulate_aaruy` file in the AAPS extra folder turns on.
        aaruyBle.emulator?.let { emulator ->
            if (!emulatorTriggersStarted) emulatorTriggers.start(emulator)
            emulatorTriggersStarted = true
        }
        changePump(newPump = false) // load device name
    }

    override suspend fun onStop() {
        // Another pump is chosen: this one must not connect or write into the database any more.
        aaruyBle.stop()
        scope?.cancel()
        scope = null
        aaruyCommands.stop()
        aaruyAlerts.stop()
        super.onStop()
    }

    /** @param newPump paired or unpaired, not an app start */
    suspend fun changePump(newPump: Boolean) {
        mDeviceAddress = preferences.get(AaruyStringKey.MacAddress)
        mDeviceName = preferences.get(AaruyStringKey.PumpName)
        aaruyPump.pumpSN = mDeviceName
        aaruyPump.reset(newPump)
        // Not waited for. At an app start this runs in onStart, and the app start waited for the whole read: during a
        // bolus for 30 s, and the pump log it read was written before the app could store it.
        if (isConfigured()) scope?.launch { commandQueue.readStatus(rh.gs(CoreUiStrings.device_changed)) }
    }

    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Aaruy connect from: $reason")
        mDeviceAddress = preferences.get(AaruyStringKey.MacAddress)
        mDeviceName = preferences.get(AaruyStringKey.PumpName)
        if (mDeviceAddress != "" && mDeviceName != "") aaruyCommands.connect(reason, mDeviceAddress)
    }

    override fun isConnected(): Boolean = aaruyCommands.isConnected
    override fun isConnecting(): Boolean = aaruyCommands.isConnecting
    override fun isHandshakeInProgress(): Boolean = false

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Aaruy disconnect from: $reason")
        aaruyCommands.disconnect(reason)
    }

    override fun stopConnecting() {
        aaruyCommands.stopConnecting()
    }

    override suspend fun getPumpStatus(reason: String) {
        aaruyCommands.readPumpStatus()
    }

    override fun isConfigured(): Boolean =
        preferences.get(AaruyStringKey.MacAddress).isNotEmpty() && preferences.get(AaruyStringKey.PumpName).isNotEmpty()

    override fun isInitialized(): Boolean = isConfigured() && aaruyPump.pumpState != AaruyPumpState.FIRST_INIT

    /** Before the first status the state is not known. That is no stop: it would write SUSPENDED_BY_PUMP at each start of the app. */
    override fun isSuspended(): Boolean =
        aaruyPump.pumpState != AaruyPumpState.FIRST_INIT && (aaruyPump.pumpState < AaruyPumpState.BASALRATE_RUNNING || aaruyPump.pumpState > AaruyPumpState.PUSHFORWARD_RUNNING)

    override fun isBusy(): Boolean = false

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (!isInitialized()) {
            // Deferred and not a real error: it is sent again at reconnect. Profile notifications are owned by the command queue.
            result.success = true
            result.comment = rh.gs(CoreUiStrings.pump_not_initialized_profile_not_set)
            return result
        }
        return if (aaruyCommands.updateBasalsInPump(profile)) {
            result.success = true
            result.enacted = true
            result.comment = "OK"
            result
        } else {
            result.comment = rh.gs(CoreUiStrings.failed_update_basal_profile)
            result
        }
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (!isInitialized()) return true
        return aaruyPump.isBasalInPump(profile)
    }

    override fun deliveredBasal(): List<Profile.ProfileValue>? = aaruyPump.deliveredBasal()

    override val baseBasalRate: PumpRate
        get() = Clock.System.now().toLocalDateTime(SystemTimeZone.current()).let { PumpRate(aaruyPump.baseBasalRateAt(it.hour * 3600 + it.minute * 60)) }
    override val reservoirLevel: StateFlow<PumpInsulin?> = aaruyPump.reservoirFlow.mapState { it?.let(::PumpInsulin) }
    override val batteryLevel: StateFlow<Int?> = aaruyPump.remainBatteryFlow.mapState { it?.toInt() }
    override val lastDataTime: StateFlow<Long> = aaruyPump.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> = aaruyPump.lastBolusTimeFlow.mapState { if (it == 0L) null else it }
    override val lastBolusAmount: StateFlow<PumpInsulin?> = aaruyPump.lastBolusAmountFlow.mapState { if (it == 0.0) null else PumpInsulin(it) }

    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        // Already limited by the command queue and PumpPluginConstraints. The service stores the bolus info once the start was sent.
        val outcome = aaruyCommands.setBolus(detailedBolusInfo)
        val result = pumpEnactResultProvider()
        result.success = outcome == AaruyBolusOutcome.FINISHED
        result.enacted = outcome != AaruyBolusOutcome.NOT_STARTED
        result.bolusDelivered = aaruyPump.bolusDelivered
        result.comment = when (outcome) {
            AaruyBolusOutcome.FINISHED    -> rh.gs(CoreUiStrings.ok)
            AaruyBolusOutcome.CUT_SHORT   -> rh.gs(AaruyStrings.aaruy_bolus_cut_short, ch.insulinAmountString(PumpInsulin(result.bolusDelivered)))
            AaruyBolusOutcome.NOT_STARTED -> rh.gs(AaruyStrings.alarm_bolus_run_fail)
            AaruyBolusOutcome.UNKNOWN     -> rh.gs(AaruyStrings.aaruy_bolus_outcome_unknown, ch.insulinAmountString(PumpInsulin(result.bolusDelivered)))
        }
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: $outcome. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        return result
    }

    override fun stopBolusDelivering() {
        if (!isInitialized()) return

        aapsLogger.info(LTag.PUMP, "stopBolusDelivering")
        // The stop waits for the answer of the pump, and the caller can be the main thread.
        (scope ?: CoroutineScope(aapsIoDispatcher)).launch { aaruyCommands.stopBolus() }
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
        val connectionOK = aaruyCommands.setTempBasal(absoluteRate, durationInMinutes)
        return if (connectionOK
            && aaruyPump.isTempBasalInProgress
            && abs(aaruyPump.tempBasalAbsoluteRate - PumpRate(absoluteRate).iU(ch.concentration, true)) <= 0.025
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

    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult =
        pumpEnactResultProvider().success(false).enacted(false).comment("Aaruy driver does not support extended boluses")

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (!isInitialized()) return pumpEnactResultProvider().success(false).enacted(false)

        aapsLogger.info(LTag.PUMP, "cancelTempBasal - enforceNew: $enforceNew")
        val connectionOK = aaruyCommands.cancelTempBasal()
        return if (connectionOK && !aaruyPump.isTempBasalInProgress) {
            pumpEnactResultProvider().success(true).enacted(true).isTempCancel(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelTempBasal failed, connectionOK: $connectionOK, tempBasalInProgress: ${aaruyPump.isTempBasalInProgress}")
            pumpEnactResultProvider().success(false).enacted(false).comment("Aaruy cancelTempBasal failed")
        }
    }

    override suspend fun cancelExtendedBolus(): PumpEnactResult = pumpEnactResultProvider()

    /** The queue runs them with the link open, as every command. */
    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? = when (customCommand) {
        is AaruyConfirmAlarmsCommand -> pumpEnactResultProvider().also { it.success = aaruyBle.confirmAlarms() }
        // Without a link nothing is forgotten here: the screen asks whether to forget a pump that was not told.
        is AaruyUnpairCommand        -> pumpEnactResultProvider().also { it.success = aaruyBle.isConnected && aaruyBle.unbindDevice() }
        // The steps wait for the motor. The queue runs one command at a time, on its own thread.
        else                         -> runBlocking { replaceSteps.execute(customCommand, pumpEnactResultProvider()) }
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Aaruy
    override fun model(): PumpType = aaruyPump.pumpType()
    override fun serialNumber(): String = aaruyPump.pumpSN
    override val pumpDescription: PumpDescription get() = PumpDescription().fillFor(aaruyPump.pumpType()).also { it.bolusStep = aaruyPump.bolusStepUnits }
    override val isFakingTempsByExtendedBoluses: Boolean = false

    /** The day of the pump starts at midnight in China, so it gives no TDD of a local day. AAPS calculates its own. */
    override suspend fun loadTDDs(): PumpEnactResult = pumpEnactResultProvider()

    override fun canHandleDST(): Boolean = true

    /**
     * The profile goes to the pump in a moved form, so a new time zone needs it again. Asked at once, as the keep alive worker
     * does: seen on a real pump, its next check came only minutes later, and the pump ran the old times until then. The
     * queue sends the profile only when the pump does not have it.
     */
    override suspend fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.info(LTag.PUMP, "Time or time zone changed: $timeChangeType")
        commandQueue.readStatus(rh.gs(AaruyStrings.aaruy_time_changed))
        rxBus.send(EventProfileChangeRequested())
    }

    /** Room has no shared clearAllTables(), so each table is emptied by its DAO. */
    override fun clearAllTables() = runBlocking {
        aaruyHistoryDatabase.historyUnitInfoDao().deleteAll()
        aaruyHistoryDatabase.historyDailyUnitInfoDao().deleteAll()
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "aaruy_settings",
        title = AaruyStrings.aaruy_pump,
        items = listOf(
            AaruyDoublePreferenceKey.MaxBasal,
            AaruyDoublePreferenceKey.MaxBolus,
            AaruyDoublePreferenceKey.LowReservoir,
            AaruyIntPreferenceKey.BolusStep,
            AaruyIntPreferenceKey.SoundPrompt,
            AaruyIntPreferenceKey.SoundAlarm
        ),
        icon = pluginDescription.icon
    )

    // cU-domain pump limits: folded into the IU scan by ConstraintsChecker.
    // The lower of pump and setting: a lowered setting counts at once, and a raised one only when the pump has it.
    override fun applyBasalConstraints(absoluteRate: PumpRate): PumpRate =
        PumpRate(absoluteRate.cU.coerceAtMost(min(aaruyPump.maxBasal, preferences.get(AaruyDoublePreferenceKey.MaxBasal))))

    override fun applyBolusConstraints(insulin: PumpInsulin): PumpInsulin =
        PumpInsulin(insulin.cU.coerceAtMost(min(aaruyPump.maxBolus, preferences.get(AaruyDoublePreferenceKey.MaxBolus))))
    override fun applyExtendedBolusConstraints(insulin: PumpInsulin): PumpInsulin = applyBolusConstraints(insulin)
}
