package app.aaps.pump.aaruy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.text.format.DateFormat
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.Aaruy
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.keys.AaruyBooleanKey
import app.aaps.pump.aaruy.keys.AaruyDoubleKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyLongKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlin.math.abs
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcPluginAaruy
import app.aaps.core.ui.compose.icons.IcPluginMedtrum
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.pump.aaruy.compose.AaruyComposeContent
import app.aaps.pump.aaruy.keys.AaruyIntentKey
import app.aaps.pump.common.driver.refresh.PumpDataRefreshType
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import dev.zacsweers.metro.IntKey as MetroIntKey

@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@PumpDriver
@MetroIntKey(1195)
@SingleIn(AppScope::class)
@Inject
class AaruyPlugin(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val context: Context,
    private val constraintChecker: ConstraintsChecker,
    private val aaruyPump: AaruyPump,
    private val detailedBolusInfoStorage: DetailedBolusInfoStorage,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val bolusProgressData: BolusProgressData,
    private val dateUtil: DateUtil,
    private val aaruyHistoryDatabase: AaruyHistoryDatabase,
    private val blePreCheck: BlePreCheck,
    private val pumpEnactResultProvider: () -> PumpEnactResult,
    notificationManager: NotificationManager
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .icon(IcPluginAaruy)
        .pluginName(TextRef.AndroidRes(R.string.aaruy_pump))
        .description(TextRef.AndroidRes(R.string.aaruy_pump_shortname))
        .composeContent { _ ->
            AaruyComposeContent(
                pluginName = rh.gs(R.string.aaruy_pump),
                context = context,
                blePreCheck = blePreCheck
            )
        },
    ownPreferences = AaruyStringKey.entries + AaruyIntKey.entries + AaruyLongKey.entries + AaruyDoubleKey.entries +
        AaruyBooleanKey.entries + AaruyIntentKey.entries,
    aapsLogger, rh, preferences, commandQueue, notificationManager
), Pump, Aaruy, PluginConstraints, OwnDatabasePlugin {

    private val disposable = CompositeDisposable()
    private var scope: CoroutineScope? = null
    private var aaruyService: AaruyService? = null
    private var mDeviceAddress = ""
    var mDeviceName = ""

    override suspend fun onStart() {
        super.onStart()
        aaruyPump.loadVarsFromSP()
        val intent = Intent(context, AaruyService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        // Own scope on IO, like the io scheduler used before, cancelled in onStop like the
        // CompositeDisposable was cleared. UNDISPATCHED because RxBus has no replay, so a scheduled
        // collector could miss an event sent before it starts.
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { context.unbindService(mConnection) }
        rxBus.toFlow(EventConfigBuilderChange::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { aaruyPump.reset() }
        changePump() // load device name on app start
    }

    override suspend fun onStop() {
        context.unbindService(mConnection)
        disposable.clear()
        super.onStop()
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            aaruyService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as AaruyService.LocalBinder
            aaruyService = mLocalBinder.serviceInstance
        }
    }

    fun changePump() {
        val newAddress = preferences.get(AaruyStringKey.MacAddress)
        val newName = preferences.get(AaruyStringKey.PumpName)

        mDeviceAddress = newAddress
        mDeviceName = newName
        aaruyPump.pumpSN = mDeviceName
        aaruyPump.reset()

        // Use different message for app start vs actual device change
        val reason =
            rh.gs(app.aaps.core.ui.R.string.device_changed)
        pluginScope.launch { commandQueue.readStatus(reason) }
    }

    fun getService(): AaruyService? {
        return aaruyService
    }

    // Pump Interface
    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Aaruy connect from: $reason")
        mDeviceAddress = preferences.get(AaruyStringKey.MacAddress)
        mDeviceName = preferences.get(AaruyStringKey.PumpName)
        aaruyPump.pumpSN = mDeviceName
        if (aaruyService != null && mDeviceAddress != "" && mDeviceName != "") {
            val success = aaruyService?.connect(reason, mDeviceAddress) ?: false
            if (!success) rxBus.send(EventShowSnackbar(rh.gs(app.aaps.core.ui.R.string.ble_not_supported), EventShowSnackbar.Type.Error))
        }
    }

    override fun isConnected(): Boolean {
        // aapsLogger.debug(LTag.PUMP, "Aaruy isConnected: ${aaruyService?.isConnected}")
        return aaruyService?.isConnected ?: false
    }
    override fun isConnecting(): Boolean {
        // aapsLogger.debug(LTag.PUMP, "Aaruy isConnecting: ${aaruyService?.isConnecting}")
        return aaruyService?.isConnecting ?: false
    }
    override fun isHandshakeInProgress(): Boolean = false

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Aaruy disconnect from: $reason")
        aaruyService?.disconnect(reason)
    }

    fun unbindPump() {
        aaruyService?.unbindDevice()
        aaruyPump.clearAlarmState()
        pluginScope.launch {commandQueue.clearAlarms()}
    }

    override fun stopConnecting() {
        aaruyService?.stopConnecting()
    }

    override suspend fun getPumpStatus(reason: String) {
        aaruyService?.readPumpStatus()
        aaruyService?.verifyConfiguration()
    }

    override fun isInitialized(): Boolean {
        return aaruyPump.pumpState != AaruyPumpState.FIRST_INIT
    }

    override fun isSuspended(): Boolean {
        return aaruyPump.pumpState < AaruyPumpState.BASALRATE_RUNNING || aaruyPump.pumpState > AaruyPumpState.PUSHFORWARD_RUNNING
    }

    override fun isBusy(): Boolean = false

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (!isInitialized()) {
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
        var result = false
        val profileBytes = aaruyPump.buildAaruyProfileArray(profile)
        if (profileBytes.size == aaruyPump.actualBasalProfile.size) {
            result = true
            for (i in profileBytes.indices) {
                if (profileBytes[i] != aaruyPump.actualBasalProfile[i]) {
                    result = false
                    break
                }
            }
        }

        return result
    }

    override val baseBasalRate: PumpRate
        get() = PumpRate(if (aaruyPump.lastBasalRate < 0.025) 0.025 else aaruyPump.lastBasalRate)
    override val reservoirLevel: StateFlow<PumpInsulin>
        get() = aaruyPump.reservoirFlow.mapState(::PumpInsulin)
    override val batteryLevel: StateFlow<Int?>
        get() = aaruyPump.remainBatteryFlow
    override val lastDataTime: StateFlow<Long> = aaruyPump.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> get() = aaruyPump.lastBolusTimeFlow
    override val lastBolusAmount: StateFlow<PumpInsulin?> get() = aaruyPump.lastBolusAmountFlow.mapState { it?.let(::PumpInsulin) }

    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        var connectionOK = false
        if (detailedBolusInfo.insulin > 0) connectionOK = aaruyService?.setBolus(detailedBolusInfo) == true
        val result = pumpEnactResultProvider()
        result.success = connectionOK
        result.bolusDelivered = bolusProgressData.state.value?.delivered?.cU ?: 0.0

        if (result.success) result.enacted = true
        if (!result.success) {
            result.comment = rh.gs(R.string.alarm_bolus_run_fail)
        } else result.comment = rh.gs(app.aaps.core.ui.R.string.ok)
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        return result
    }

    override fun stopBolusDelivering() {
        if (!isInitialized()) return

        aapsLogger.info(LTag.PUMP, "stopBolusDelivering")
        aaruyService?.stopBolus()
    }

    override suspend fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (!isInitialized()) {
            result.success = false
            result.enacted = false
            return result
        }

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - absoluteRate: $absoluteRate, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        // round rate to pump rate
        val pumpRate = absoluteRate
        temporaryBasalStorage.add(PumpSync.PumpState.TemporaryBasal(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), pumpRate, true, tbrType, 0L, 0L))
        val connectionOK = aaruyService?.setTempBasal(pumpRate, durationInMinutes) ?: false
        return if (connectionOK
            && aaruyPump.isTempBasalInProgress
            && abs(aaruyPump.tempBasalAbsoluteRate - pumpRate) <= 0.025
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
                    "tempBasalAbsoluteRate: ${aaruyPump.tempBasalAbsoluteRate}, pumpRate is $pumpRate"
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

        var insulinAfterConstraint = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        // needs to be rounded
        insulinAfterConstraint = Round.roundTo(insulinAfterConstraint, pumpDescription.extendedBolusStep)

        if (aaruyPump.isExtendedInProgress && abs(aaruyPump.extendedBolusAmount - insulinAfterConstraint) < pumpDescription.extendedBolusStep) {
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + aaruyPump.extendedBolusAmount + " Asked: " + insulinAfterConstraint)
            return  pumpEnactResultProvider().enacted(false).success(true).comment(rh.gs(app.aaps.core.ui.R.string.ok)).duration(aaruyPump.extendedBolusRemainingMinutes)
                .absolute(aaruyPump.extendedBolusAbsoluteRate).isPercent(false).isTempCancel(false)
        }
        val sendOK = aaruyService?.extendedBolus(insulinAfterConstraint, durationInMinutes)
            ?: false

        if (sendOK) {
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: OK")
            return  pumpEnactResultProvider().enacted(true).success(true).comment(rh.gs(app.aaps.core.ui.R.string.ok)).duration(aaruyPump.extendedBolusRemainingMinutes)
                .absolute(aaruyPump.extendedBolusAbsoluteRate).bolusDelivered(aaruyPump.extendedBolusAmount).isPercent(false)
        }

        result.enacted = false
        result.success = false
        aapsLogger.error("setExtendedBolus: Failed to extended bolus")
        return result
    }

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (!isInitialized()) return  pumpEnactResultProvider().success(false).enacted(false)

        aapsLogger.info(LTag.PUMP, "cancelTempBasal - enforceNew: $enforceNew")
        val connectionOK = aaruyService?.cancelTempBasal() ?: false
        return if (connectionOK && !aaruyPump.isTempBasalInProgress) {
             pumpEnactResultProvider().success(true).enacted(true).isTempCancel(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelTempBasal failed, connectionOK: $connectionOK, tempBasalInProgress: ${aaruyPump.isTempBasalInProgress}")
             pumpEnactResultProvider().success(false).enacted(false).comment("Aaruy cancelTempBasal failed")
        }
    }

    override suspend fun cancelExtendedBolus(): PumpEnactResult {
        if (!isInitialized()) return  pumpEnactResultProvider().success(false).enacted(false)

        val connectionOK = aaruyService?.cancelExtendedBolus() ?: false
        return if (connectionOK && !aaruyPump.isExtendedInProgress) {
             pumpEnactResultProvider().success(true).enacted(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelExtendedBolus failed, connectionOK: $connectionOK, isExtendedInProgress: ${aaruyPump.isExtendedInProgress}")
             pumpEnactResultProvider().success(false).enacted(false).comment("Aaruy extendedBolus failed")
        }
    }

    // override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
    //     val now = System.currentTimeMillis()
    //     if (aaruyPump.lastConnection + 60 * 60 * 1000L < System.currentTimeMillis()) {
    //         return JSONObject()
    //     }
    //     val pumpJson = JSONObject()
    //     val battery = JSONObject()
    //     val status = JSONObject()
    //     val extended = JSONObject()
    //     try {
    //         status.put(
    //             "status", if (!isSuspended()) "normal"
    //             else if (isInitialized() && isSuspended()) "suspended"
    //             else "no active patch"
    //         )
    //
    //         battery.put("percent", aaruyPump.remainBattery.toInt())
    //         status.put("timestamp", dateUtil.toISOString(aaruyPump.lastConnection))
    //         extended.put("Version", version)
    //         if (aaruyPump.lastBolusTime != 0L) {
    //             extended.put("LastBolus", dateUtil.dateAndTimeString(aaruyPump.lastBolusTime))
    //             extended.put("LastBolusAmount", aaruyPump.lastBolusAmount)
    //         }
    //         val tb = pumpSync.expectedPumpState().temporaryBasal
    //         if (tb != null) {
    //             extended.put("TempBasalAbsoluteRate", tb.convertedToAbsolute(now, profile))
    //             extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.timestamp))
    //             extended.put("TempBasalRemaining", tb.plannedRemainingMinutes)
    //         }
    //         val eb = pumpSync.expectedPumpState().extendedBolus
    //         if (eb != null) {
    //             extended.put("ExtendedBolusAbsoluteRate", eb.rate)
    //             extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.timestamp))
    //             extended.put("ExtendedBolusRemaining", eb.plannedRemainingMinutes)
    //         }
    //         extended.put("BaseBasalRate", baseBasalRate)
    //         try {
    //             extended.put("ActiveProfile", profileFunction.getProfileName())
    //         } catch (e: Exception) {
    //             aapsLogger.error("Unhandled exception", e)
    //         }
    //         pumpJson.put("battery", battery)
    //         pumpJson.put("status", status)
    //         pumpJson.put("extended", extended)
    //         pumpJson.put("reservoir", aaruyPump.reservoir.toInt())
    //         pumpJson.put("clock", dateUtil.toISOString(now))
    //     } catch (e: JSONException) {
    //         aapsLogger.error("Unhandled exception", e)
    //     }
    //     return pumpJson
    // }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Aaruy
    override fun model(): PumpType {
        return aaruyPump.pumpType()
    }

    override fun serialNumber(): String {
        return aaruyPump.pumpSN
    }

    override val pumpDescription = PumpDescription().fillFor(aaruyPump.pumpType())

    // override fun shortStatus(veryShort: Boolean): String {
    //     var ret = ""
    //     if (aaruyPump.lastConnection != 0L) {
    //         val agoMillis = System.currentTimeMillis() - aaruyPump.lastConnection
    //         val agoMin = (agoMillis / 60.0 / 1000.0).toInt()
    //         ret += "LastConn: $agoMin minago\n"
    //     }
    //     if (aaruyPump.lastBolusTime != 0L)
    //         ret += "LastBolus: ${decimalFormatter.to2Decimal(aaruyPump.lastBolusAmount)}U @${DateFormat.format("HH:mm", aaruyPump.lastBolusTime)}"
    //
    //     if (aaruyPump.isTempBasalInProgress)
    //         ret += "Temp: ${aaruyPump.temporaryBasalToString()}"
    //
    //     if (aaruyPump.isExtendedInProgress)
    //         ret += "Extended: ${aaruyPump.extendedBolusToString()}\n"
    //
    //     if (!veryShort) {
    //         ret += "TDD: ${decimalFormatter.to0Decimal(aaruyPump.dailyTotalUnits)} / ${aaruyPump.maxDailyTotalUnits} U"
    //     }
    //     ret += "Reserv: ${decimalFormatter.to0Decimal(aaruyPump.reservoir)} U"
    //     ret += "Batt: ${aaruyPump.remainBattery}"
    //     return ret
    // }

    override val isFakingTempsByExtendedBoluses: Boolean = false

    override suspend fun loadTDDs(): PumpEnactResult {
        if (!(aaruyService?.isConnected!!) || !aaruyPump.canSaveHistory) return pumpEnactResultProvider().success(false)
        aaruyService?.requestHistoryData()
        return pumpEnactResultProvider().success(true)
    }

    override fun getCustomActions(): List<CustomAction>? {
        return null
    }

    override fun executeCustomAction(customActionType: CustomActionType) {
        // Unused
    }

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? {
        return null
    }

    override fun canHandleDST(): Boolean {
        return true
    }

    // Aaruy Pump
    override fun clearAlarms(): PumpEnactResult {
        if (!isInitialized()) return  pumpEnactResultProvider().success(false).enacted(false)
        val connectionOK = aaruyService?.clearAlarms() ?: false
        return  pumpEnactResultProvider().success(connectionOK)
    }

    override fun clearAllTables() = aaruyHistoryDatabase.clearAllTables()

    // Constraints interface
    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfSmaller(aaruyPump.maxBasal, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, aaruyPump.maxBasal, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        percentRate.setIfGreater(0, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, 0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        percentRate.setIfSmaller(
            pumpDescription.maxTempPercent,
            rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, pumpDescription.maxTempPercent, rh.gs(app.aaps.core.ui.R.string.pumplimit)),
            this
        )
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfSmaller(aaruyPump.maxBolus, rh.gs(app.aaps.core.ui.R.string.limitingbolus, aaruyPump.maxBolus, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return applyBolusConstraints(insulin)
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "aaruy_pump_settings",
        titleResId = R.string.aaruy_pump,
        items = listOf(
            AaruyDoubleKey.PumpPrefMaxBasal,
            AaruyDoubleKey.PumpPrefMaxBolus,
            AaruyDoubleKey.PumpPrefLowAlert,
            AaruyIntKey.PumpPrefBolusStep,
            AaruyIntKey.PumpPrefSoundRemind,
            AaruyIntKey.PumpPrefSoundAlarm
        ),
        icon = pluginDescription.icon
    )
}