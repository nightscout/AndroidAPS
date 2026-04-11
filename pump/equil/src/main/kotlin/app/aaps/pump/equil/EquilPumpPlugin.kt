package app.aaps.pump.equil

import android.content.Context
import android.os.SystemClock
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.compose.EquilComposeContent
import app.aaps.pump.equil.data.BolusProfile
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.events.EventEquilAlarm
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.keys.EquilBooleanKey
import app.aaps.pump.equil.keys.EquilBooleanPreferenceKey
import app.aaps.pump.equil.keys.EquilIntPreferenceKey
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.command.BaseCmd
import app.aaps.pump.equil.manager.command.CmdAlarmSet
import app.aaps.pump.equil.manager.command.CmdBasalSet
import app.aaps.pump.equil.manager.command.CmdSettingSet
import app.aaps.pump.equil.manager.command.CmdTimeSet
import app.aaps.pump.equil.manager.customCommands.CmdModeAndHistoryGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.joda.time.DateTime
import org.joda.time.Duration
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class EquilPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val context: Context,
    private val pumpSync: PumpSync,
    private val equilManager: EquilManager,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val constraintsChecker: ConstraintsChecker,
    private val notificationManager: NotificationManager,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            EquilComposeContent(
                pluginName = rh.gs(R.string.equil_name),
                protectionCheck = protectionCheck,
                blePreCheck = blePreCheck
            )
        }
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_equil_128)
        .pluginName(R.string.equil_name)
        .shortName(R.string.equil_name_short)
        .description(R.string.equil_pump_description),
    ownPreferences = listOf(
        EquilBooleanKey::class.java, EquilBooleanPreferenceKey::class.java, EquilIntPreferenceKey::class.java,
        EquilStringKey::class.java
    ),
    aapsLogger, rh, preferences, commandQueue
), Pump {

    override val pumpDescription: PumpDescription
    private val pumpType = PumpType.EQUIL
    private val bolusProfile: BolusProfile = BolusProfile()

    private var scope: CoroutineScope? = null

    override fun onStart() {
        super.onStart()
        equilManager.init()

        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        rxBus.toFlow(EventEquilDataChanged::class.java)
            .onEach { playAlarm() }
            .launchIn(newScope)

        rxBus.toFlow(EventEquilAlarm::class.java)
            .onEach { eventEquilError ->
                commandQueue.performing()?.let {
                    if (it.commandType == Command.CommandType.BOLUS) {
                        aapsLogger.info(LTag.PUMPCOMM, "eventEquilError.tips====${eventEquilError.tips}")
                        notificationManager.dismiss(NotificationId.EQUIL_ALARM)
                        notificationManager.post(NotificationId.EQUIL_ALARM, eventEquilError.tips, soundRes = app.aaps.core.ui.R.raw.alarm)
                        stopBolusDelivering()
                    }
                }
            }
            .launchIn(newScope)
        preferences.observe(EquilIntPreferenceKey.EquilTone).drop(1).onEach {
            val mode = preferences.get(EquilIntPreferenceKey.EquilTone)
            commandQueue.customCommand(
                CmdAlarmSet(mode, aapsLogger, preferences, equilManager),
                object : Callback() {
                    override fun run() {
                        if (result.success) ToastUtils.infoToast(context, rh.gs(R.string.equil_pump_updated))
                        else ToastUtils.infoToast(context, rh.gs(R.string.equil_error))
                    }
                })
        }.launchIn(newScope)
        preferences.observe(DoubleKey.SafetyMaxBolus).drop(1).onEach {
            val profile = pumpSync.expectedPumpState().profile ?: return@onEach
            commandQueue.customCommand(
                CmdSettingSet(constraintsChecker.getMaxBolusAllowed().value(), constraintsChecker.getMaxBasalAllowed(profile).value(), aapsLogger, preferences, equilManager),
                object : Callback() {
                    override fun run() {
                        if (result.success) ToastUtils.infoToast(context, rh.gs(R.string.equil_pump_updated))
                        else ToastUtils.infoToast(context, rh.gs(R.string.equil_error))
                    }
                })
        }.launchIn(newScope)
    }

    var tempActivationProgress = ActivationProgress.NONE
    var indexEquilReadStatus = 5

    init {
        pumpDescription = PumpDescription().fillFor(pumpType)
    }

    override fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMPCOMM, "EquilPumpPlugin.onStop()")
        scope?.cancel()
        scope = null
    }

    override fun isConfigured(): Boolean = true
    override fun isInitialized(): Boolean = equilManager.isActivationCompleted()
    override fun isConnected(): Boolean = true
    override fun isConnecting(): Boolean = false
    override fun isBusy(): Boolean = false

    override fun isHandshakeInProgress(): Boolean = false
    override fun connect(reason: String) {
        equilManager.connect()
    }

    override fun isSuspended(): Boolean {
        val runMode = equilManager.equilState?.runMode
        return if (equilManager.isActivationCompleted()) {
            runMode == RunMode.SUSPEND || runMode == RunMode.STOP
        } else true
    }

    override fun getPumpStatus(reason: String) {
        if (equilManager.isActivationCompleted()) commandQueue.customCommand(CmdModeAndHistoryGet(), null)
    }

    override fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "setNewBasalProfile")
        val mode = equilManager.equilState?.runMode
        if (mode === RunMode.RUN || mode === RunMode.SUSPEND) {
            val basalSchedule = BasalSchedule.mapProfileToBasalSchedule(profile)
            val pumpEnactResult = equilManager.executeCmd(CmdBasalSet(basalSchedule, profile, aapsLogger, preferences, equilManager))
            if (pumpEnactResult.success) equilManager.equilState?.basalSchedule = basalSchedule
            return pumpEnactResult
        }
        return pumpEnactResultProvider.get().enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run))
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        return if (!equilManager.isActivationCompleted()) {
            // When no Pod is active, return true here in order to prevent AAPS from setting a profile
            // When we activate a new Pod, we just use ProfileFunction to set the currently active profile
            true
        } else equilManager.equilState?.basalSchedule == BasalSchedule.mapProfileToBasalSchedule(profile)
    }

    override val lastDataTime: StateFlow<Long> = equilManager.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> = equilManager.lastBolusTimeFlow
    override val lastBolusAmount: StateFlow<PumpInsulin?> = equilManager.lastBolusAmountFlow.mapState { it?.let(::PumpInsulin) }

    override val baseBasalRate: PumpRate get() = PumpRate(if (isSuspended()) 0.0 else equilManager.equilState?.basalSchedule?.rateAt(toDuration(DateTime.now())) ?: 0.0)

    override val reservoirLevel: StateFlow<PumpInsulin> = equilManager.reservoirFlow.mapState(::PumpInsulin)
    override val batteryLevel: StateFlow<Int?> = equilManager.batteryFlow

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        if (detailedBolusInfo.insulin == 0.0) {
            // bolus requested
            aapsLogger.error("deliverTreatment: Invalid input: neither carbs nor insulin are set in treatment")
            return pumpEnactResultProvider.get().success(false).enacted(false)
                .bolusDelivered(0.0).comment("Invalid input")
        }
        val mode = equilManager.equilState?.runMode
        if (mode !== RunMode.RUN) {
            return pumpEnactResultProvider.get().enacted(false).success(false)
                .bolusDelivered(0.0).comment(rh.gs(R.string.equil_pump_not_run))
        }
        val lastInsulin = equilManager.equilState?.currentInsulin ?: 0
        return if (detailedBolusInfo.insulin > lastInsulin) {
            pumpEnactResultProvider.get().success(false).enacted(false).bolusDelivered(0.0)
                .comment(R.string.equil_not_enough_insulin)
        } else deliverBolus(detailedBolusInfo)
    }

    override fun stopBolusDelivering() {
        equilManager.stopBolus(bolusProfile)
        aapsLogger.debug(LTag.PUMPCOMM, "stopBolusDelivering=====")
    }

    override fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        enforceNew: Boolean,
        tbrType: TemporaryBasalType
    ): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalAbsolute=====$absoluteRate====$durationInMinutes===$enforceNew")
        if (durationInMinutes <= 0 || durationInMinutes % BASAL_STEP_DURATION.standardMinutes != 0L) {
            return pumpEnactResultProvider.get().success(false)
                .comment(rh.gs(R.string.equil_error_set_temp_basal_failed_validation, BASAL_STEP_DURATION.standardMinutes))
        }
        val mode = equilManager.equilState?.runMode
        if (mode !== RunMode.RUN) {
            return pumpEnactResultProvider.get().enacted(false).success(false)
                .comment(rh.gs(R.string.equil_pump_not_run))
        }
        var pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.success(false)
        pumpEnactResult = equilManager.getTempBasalPump()
        if (pumpEnactResult.success) {
            if (pumpEnactResult.enacted) {
                pumpEnactResult = cancelTempBasal(true)
            }
            if (pumpEnactResult.success) {
                SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                pumpEnactResult = equilManager.setTempBasal(absoluteRate, durationInMinutes, false)
                if (pumpEnactResult.success) {
                    pumpEnactResult.isTempCancel = false
                    pumpEnactResult.duration = durationInMinutes
                    pumpEnactResult.isPercent = false
                    pumpEnactResult.absolute = absoluteRate
                }
            }
        }
        return pumpEnactResult
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal=====$enforceNew")
        if (!isInitialized()) return pumpEnactResultProvider.get().success(false).enacted(false)
        val pumpEnactResult = equilManager.setTempBasal(0.0, 0, true)
        if (pumpEnactResult.success) {
            pumpEnactResult.isTempCancel = true
        }
        return pumpEnactResult
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Equil
    override fun model(): PumpType = PumpType.EQUIL
    override fun serialNumber(): String = equilManager.equilState?.serialNumber ?: ""

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? {
        aapsLogger.debug(LTag.PUMPCOMM, "executeCustomCommand $customCommand")
        var pumpEnactResult: PumpEnactResult? = null

        if (customCommand is BaseCmd) pumpEnactResult = equilManager.executeCmd(customCommand)
        else if (customCommand is CmdModeAndHistoryGet) pumpEnactResult = equilManager.readModeAndHistory()
        return pumpEnactResult
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.debug(LTag.PUMP, "DST and/or TimeZone changed event will be consumed by driver")
        commandQueue.customCommand(CmdTimeSet(aapsLogger, preferences, equilManager), null)
    }

    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun canHandleDST(): Boolean = false
    override fun disconnect(reason: String) {
        aapsLogger.info(LTag.PUMPCOMM, "disconnect reason=$reason")
        equilManager.closeBleAuto()
    }

    override fun stopConnecting() {}

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult =
        error("Pump doesn't support percent basal rate")

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "setExtendedBolus $insulin, $durationInMinutes")
        val pumpEnactResult = equilManager.setExtendedBolus(insulin, durationInMinutes, false)
        if (pumpEnactResult.success) {
            pumpEnactResult.isTempCancel = false
            pumpEnactResult.duration = durationInMinutes
            pumpEnactResult.isPercent = false
            pumpEnactResult.absolute = insulin
        }
        return pumpEnactResult
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelExtendedBolus")
        return equilManager.setExtendedBolus(0.0, 0, true)
    }

    override fun loadTDDs(): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "loadTDDs")
        return pumpEnactResultProvider.get().success(false).enacted(false)
    }

    override fun isBatteryChangeLoggingEnabled(): Boolean = false

    private fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "deliverBolus")
        bolusProfile.insulin = detailedBolusInfo.insulin
        return equilManager.bolus(detailedBolusInfo, bolusProfile)
    }

    fun resetData() {
        preferences.put(EquilBooleanKey.AlarmBattery10, false)
        preferences.put(EquilBooleanKey.AlarmInsulin10, false)
        preferences.put(EquilBooleanKey.AlarmInsulin5, false)
        preferences.put(EquilBooleanKey.BasalSet, false)
    }

    fun clearData() {
        resetData()
        equilManager.clearPodState()
        preferences.put(EquilStringKey.Device, "")
        preferences.put(EquilStringKey.Password, "")
    }

    private fun playAlarm() {
        val battery = equilManager.equilState?.battery ?: 100
        val insulin = equilManager.equilState?.currentInsulin ?: 0
        val alarmBattery = preferences.get(EquilBooleanPreferenceKey.EquilAlarmBattery)
        val alarmInsulin = preferences.get(EquilBooleanPreferenceKey.EquilAlarmInsulin)
        if (battery <= 10 && alarmBattery) {
            val alarmBattery10 = preferences.get(EquilBooleanKey.AlarmBattery10)
            if (!alarmBattery10) {
                notificationManager.post(
                    NotificationId.FAILED_UPDATE_PROFILE,
                    rh.gs(R.string.equil_low_battery) + battery + "%",
                    soundRes = app.aaps.core.ui.R.raw.alarm
                )
                preferences.put(EquilBooleanKey.AlarmBattery10, true)
            } else {
                if (battery < 5) {
                    notificationManager.post(
                        NotificationId.FAILED_UPDATE_PROFILE,
                        rh.gs(R.string.equil_low_battery) + battery + "%",
                        NotificationLevel.URGENT,
                        soundRes = app.aaps.core.ui.R.raw.alarm
                    )
                }
            }
        }
        if (equilManager.equilState?.runMode === RunMode.RUN && alarmInsulin && equilManager.isActivationCompleted()) {
            when {
                insulin in 6..10 -> {
                    val alarmInsulin10 =
                        preferences.get(EquilBooleanKey.AlarmInsulin10)
                    if (!alarmInsulin10) {
                        notificationManager.dismiss(NotificationId.EQUIL_ALARM_INSULIN)
                        notificationManager.post(
                            NotificationId.EQUIL_ALARM_INSULIN,
                            rh.gs(R.string.equil_low_insulin) + insulin + "U",
                            soundRes = app.aaps.core.ui.R.raw.alarm
                        )
                        preferences.put(EquilBooleanKey.AlarmInsulin10, true)
                    }
                }

                insulin in 3..5  -> {
                    val alarmInsulin5 = preferences.get(EquilBooleanKey.AlarmInsulin5)
                    if (!alarmInsulin5) {
                        notificationManager.dismiss(NotificationId.EQUIL_ALARM_INSULIN)
                        notificationManager.post(
                            NotificationId.EQUIL_ALARM_INSULIN,
                            rh.gs(R.string.equil_low_insulin) + insulin + "U",
                            soundRes = app.aaps.core.ui.R.raw.alarm
                        )
                        preferences.put(EquilBooleanKey.AlarmInsulin5, true)
                    }
                }

                insulin <= 2     -> {
                    notificationManager.dismiss(NotificationId.EQUIL_ALARM_INSULIN)
                    notificationManager.post(
                        NotificationId.EQUIL_ALARM_INSULIN,
                        rh.gs(R.string.equil_low_insulin) + insulin + "U",
                        soundRes = app.aaps.core.ui.R.raw.alarm
                    )
                }
            }
        }
    }

    companion object {

        private val BASAL_STEP_DURATION: Duration = Duration.standardMinutes(30)
        fun toDuration(dateTime: DateTime): Duration = Duration(dateTime.toLocalTime().millisOfDay.toLong())
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "equil_settings",
        titleResId = R.string.equil_name,
        items = listOf(
            EquilBooleanPreferenceKey.EquilAlarmBattery,
            EquilBooleanPreferenceKey.EquilAlarmInsulin,
            EquilIntPreferenceKey.EquilTone
        ),
        icon = pluginDescription.icon
    )

}
