package app.aaps.pump.equil

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.text.format.DateFormat
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.Preferences
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.data.AlarmMode
import app.aaps.pump.equil.data.BolusProfile
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.command.BaseCmd
import app.aaps.pump.equil.manager.command.CmdAlarmSet
import app.aaps.pump.equil.manager.command.CmdBasalSet
import app.aaps.pump.equil.manager.command.CmdSettingSet
import app.aaps.pump.equil.manager.command.CmdStatusGet
import app.aaps.pump.equil.manager.command.CmdTimeSet
import app.aaps.pump.equil.manager.command.PumpEvent
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.joda.time.DateTime
import org.joda.time.Duration
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class EquilPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val context: Context,
    private val sp: SP,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val pumpSync: PumpSync,
    private val equilManager: EquilManager,
    private val decimalFormatter: DecimalFormatter,
    private val instantiator: Instantiator,
    private val preferences: Preferences
) : PumpPluginBase(
    PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(EquilFragment::class.java.name)
        .pluginIcon(R.drawable.ic_equil_128)
        .pluginName(R.string.equil_name)
        .shortName(R.string.equil_name_short)
        .preferencesId(R.xml.pref_equil)
        .description(R.string.equil_pump_description), aapsLogger, rh, commandQueue
), Pump {

    override val pumpDescription: PumpDescription
    private val pumpType = PumpType.EQUIL
    private val bolusProfile: BolusProfile = BolusProfile()

    private val disposable = CompositeDisposable()
    val handler = Handler(HandlerThread(this::class.java.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var statusChecker: Runnable
    override fun onStart() {
        super.onStart()
        handler.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MILLIS)
        disposable += rxBus
            .toObservable(EventEquilDataChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ playAlarm() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                           if (event.isChanged(rh.gs(R.string.key_equil_tone))) {
                               val mode = AlarmMode.fromInt(sp.getString(R.string.key_equil_tone, "3").toInt())
                               commandQueue.customCommand(CmdAlarmSet(mode.command), object : Callback() {
                                   override fun run() {
                                       if (result.success) ToastUtils.infoToast(context, rh.gs(R.string.equil_pump_updated))
                                       else ToastUtils.infoToast(context, rh.gs(R.string.equil_error))
                                   }
                               })
                           } else if (event.isChanged(rh.gs(app.aaps.core.keys.R.string.key_equil_maxbolus))) {
                               val data = preferences.get(DoubleKey.EquilMaxBolus)
                               commandQueue.customCommand(CmdSettingSet(data), object : Callback() {
                                   override fun run() {
                                       if (result.success) ToastUtils.infoToast(context, rh.gs(R.string.equil_pump_updated))
                                       else ToastUtils.infoToast(context, rh.gs(R.string.equil_error))
                                   }
                               })
                           }
                       }, fabricPrivacy::logException)
    }

    var tempActivationProgress = ActivationProgress.NONE

    init {
        pumpDescription = PumpDescription().fillFor(pumpType)
        statusChecker = Runnable {
            if (commandQueue.size() == 0 && commandQueue.performing() == null) {
                if (equilManager.isActivationCompleted) commandQueue.customCommand(CmdStatusGet(), null)
            } else {
                aapsLogger.debug(LTag.PUMPCOMM, "Skipping Pod status check because command queue is not empty")
            }
            handler.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MILLIS)
        }
        PumpEvent.init(rh)
    }

    override fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMPCOMM, "EquilPumpPlugin.onStop()")
        handler.removeCallbacksAndMessages(null)
        disposable.clear()
    }

    override fun isInitialized(): Boolean = true
    override fun isConnected(): Boolean = true
    override fun isConnecting(): Boolean = false
    override fun isBusy(): Boolean = false

    override fun isHandshakeInProgress(): Boolean = false
    override fun connect(reason: String) {}

    override fun isSuspended(): Boolean {
        val runMode = equilManager.runMode
        return if (equilManager.isActivationCompleted) {
            runMode == RunMode.SUSPEND || runMode == RunMode.STOP
        } else true
    }

    override fun getPumpStatus(reason: String) {}
    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "setNewBasalProfile")
        val mode = equilManager.runMode
        if (mode === RunMode.RUN || mode === RunMode.SUSPEND) {
            val basalSchedule = BasalSchedule.mapProfileToBasalSchedule(profile)
            val pumpEnactResult = equilManager.executeCmd(CmdBasalSet(basalSchedule, profile))
            if (pumpEnactResult.success) {
                equilManager.basalSchedule = basalSchedule
            }
            return pumpEnactResult
        }
        return instantiator.providePumpEnactResult().enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run))
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        return if (!equilManager.isActivationCompleted) {
            // When no Pod is active, return true here in order to prevent AAPS from setting a profile
            // When we activate a new Pod, we just use ProfileFunction to set the currently active profile
            true
        } else equilManager.basalSchedule == BasalSchedule.mapProfileToBasalSchedule(profile)
    }

    override fun lastDataTime(): Long {
        aapsLogger.debug(LTag.PUMPCOMM, "lastDataTime: ${dateUtil.dateAndTimeAndSecondsString(equilManager.lastDataTime)}")
        return equilManager.lastDataTime
    }

    override val baseBasalRate: Double
        get() = if (isSuspended()) 0.0 else equilManager.basalSchedule?.rateAt(toDuration(DateTime.now())) ?: 0.0
    override val reservoirLevel: Double
        get() = equilManager.currentInsulin.toDouble()
    override val batteryLevel: Int
        get() = equilManager.battery

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        if (detailedBolusInfo.insulin == 0.0) {
            // bolus requested
            aapsLogger.error("deliverTreatment: Invalid input: neither carbs nor insulin are set in treatment")
            return instantiator.providePumpEnactResult().success(false).enacted(false).bolusDelivered(0.0).comment("Invalid input")
        }
        val maxBolus = preferences.get(DoubleKey.EquilMaxBolus)
        if (detailedBolusInfo.insulin > preferences.get(DoubleKey.EquilMaxBolus)) {
            val formattedValue = "%.2f".format(maxBolus)
            val comment = rh.gs(R.string.equil_maxbolus_tips, formattedValue)
            return instantiator.providePumpEnactResult().success(false).enacted(false).bolusDelivered(0.0).comment(comment)

        }
        val mode = equilManager.runMode
        if (mode !== RunMode.RUN) {
            return instantiator.providePumpEnactResult().enacted(false).success(false).bolusDelivered(0.0).comment(rh.gs(R.string.equil_pump_not_run))
        }
        val lastInsulin = equilManager.currentInsulin
        return if (detailedBolusInfo.insulin > lastInsulin) {
            instantiator.providePumpEnactResult().success(false).enacted(false).bolusDelivered(0.0).comment(R.string.equil_not_enough_insulin)
        } else deliverBolus(detailedBolusInfo)
    }

    override fun stopBolusDelivering() {
        equilManager.stopBolus(bolusProfile)
        aapsLogger.debug(LTag.PUMPCOMM, "stopBolusDelivering=====")
    }

    override fun setTempBasalAbsolute(
        absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType
    ): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalAbsolute=====$absoluteRate====$durationInMinutes===$enforceNew")
        if (durationInMinutes <= 0 || durationInMinutes % BASAL_STEP_DURATION.standardMinutes != 0L) {
            return instantiator.providePumpEnactResult().success(false).comment(rh.gs(R.string.equil_error_set_temp_basal_failed_validation, BASAL_STEP_DURATION.standardMinutes))
        }
        val mode = equilManager.runMode
        if (mode !== RunMode.RUN) {
            return instantiator.providePumpEnactResult().enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run))
        }
        var pumpEnactResult = instantiator.providePumpEnactResult()
        pumpEnactResult.success(false)
        pumpEnactResult = equilManager.tempBasalPump
        if (pumpEnactResult.success) {
            if (pumpEnactResult.enacted) {
                pumpEnactResult = cancelTempBasal(true)
            }
            if (pumpEnactResult.success) {
                pumpEnactResult = equilManager.setTempBasal(
                    absoluteRate, durationInMinutes, false
                )
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
        val pumpEnactResult = equilManager.setTempBasal(0.0, 0, true)
        if (pumpEnactResult.success) {
            pumpEnactResult.isTempCancel = true
        }
        return pumpEnactResult
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        if (!isConnected()) return JSONObject().put("status", JSONObject().put("status", "no active Pod"))

        val json = JSONObject()
        val battery = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        return try {
            battery.put("percent", batteryLevel)
            status.put("status", if (isSuspended()) "suspended" else "normal")
            status.put("timestamp", dateUtil.toISOString(lastDataTime()))
            extended.put("Version", version)
            pumpSync.expectedPumpState().bolus?.let { bolus ->
                extended.put("LastBolus", dateUtil.dateAndTimeString(bolus.timestamp))
                extended.put("LastBolusAmount", bolus.amount)
            }
            pumpSync.expectedPumpState().temporaryBasal?.let { temporaryBasal ->
                extended.put("TempBasalAbsoluteRate", temporaryBasal.convertedToAbsolute(dateUtil.now(), profile))
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(temporaryBasal.timestamp))
                extended.put("TempBasalRemaining", temporaryBasal.plannedRemainingMinutes)
            }
            pumpSync.expectedPumpState().extendedBolus?.let { extendedBolus ->
                extended.put("ExtendedBolusAbsoluteRate", extendedBolus.rate)
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(extendedBolus.timestamp))
                extended.put("ExtendedBolusRemaining", extendedBolus.plannedRemainingMinutes)
            }
            extended.put("BaseBasalRate", baseBasalRate)
            extended.put("ActiveProfile", profileName)
            json.put("battery", battery)
            json.put("status", status)
            json.put("extended", extended)
            json.put("reservoir", reservoirLevel)
            json.put("clock", dateUtil.toISOString(dateUtil.now()))
            json
        } catch (e: JSONException) {
            json.put("status", JSONObject().put("status", "error" + e.message))
            aapsLogger.error("Unhandled exception", e)
            json
        }
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Equil
    override fun model(): PumpType = PumpType.EQUIL
    override fun serialNumber(): String = equilManager.serialNumber

    override fun shortStatus(veryShort: Boolean): String {
        if (!equilManager.isActivationCompleted) {
            return rh.gs(R.string.equil_init_insulin_error)
        }
        var ret = ""
        if (lastDataTime() != 0L) {
            val agoMsec = System.currentTimeMillis() - lastDataTime()
            val agoMin = (agoMsec / 60.0 / 1000.0).toInt()
            ret += rh.gs(R.string.equil_common_short_status_last_connection, agoMin) + "\n"
        }
        if (equilManager.bolusRecord != null) {
            ret += rh.gs(
                R.string.equil_common_short_status_last_bolus, decimalFormatter.to2Decimal(equilManager.bolusRecord.amount), DateFormat.format(
                    "HH:mm", equilManager.bolusRecord.startTime
                )
            ) + "\n"
        }
        val (temporaryBasal, extendedBolus, _, profile) = pumpSync.expectedPumpState()
        if (temporaryBasal != null && profile != null) {
            ret += rh.gs(R.string.equil_common_short_status_temp_basal, temporaryBasal.toStringFull(dateUtil, decimalFormatter) + "\n")
        }
        if (extendedBolus != null) {
            ret += rh.gs(R.string.equil_common_short_status_extended_bolus, extendedBolus.toStringFull(dateUtil, decimalFormatter) + "\n")
        }
        ret += rh.gs(R.string.equil_common_short_status_reservoir, reservoirLevel)
        return ret.trim { it <= ' ' }
    }

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? {
        aapsLogger.debug(LTag.PUMPCOMM, "executeCustomCommand $customCommand")
        var pumpEnactResult: PumpEnactResult? = null

        if (customCommand is BaseCmd) {
            pumpEnactResult = equilManager.executeCmd(customCommand)
        }
        if (customCommand is CmdStatusGet) {
            pumpEnactResult = equilManager.readEquilStatus()
        }
        return pumpEnactResult
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.debug(LTag.PUMP, "DST and/or TimeZone changed event will be consumed by driver")
        commandQueue.customCommand(CmdTimeSet(), null)
    }

    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun canHandleDST(): Boolean = false
    override fun disconnect(reason: String) {
        aapsLogger.info(LTag.PUMPCOMM, "disconnect reason=$reason")
        equilManager.closeBleAuto()
    }

    override fun stopConnecting() {}

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent $percent $durationInMinutes ")
        return if (percent == 0) {
            setTempBasalAbsolute(0.0, durationInMinutes, profile, enforceNew, tbrType)
        } else {
            var absoluteValue = profile.getBasal() * (percent / 100.0)
            absoluteValue = pumpDescription.pumpType.determineCorrectBasalSize(absoluteValue)
            setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew, tbrType)
        }
    }

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
        return instantiator.providePumpEnactResult().success(false).enacted(false)
    }

    override fun isBatteryChangeLoggingEnabled(): Boolean = false

    private fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        aapsLogger.debug(LTag.PUMPCOMM, "deliverBolus")
        bolusProfile.insulin = detailedBolusInfo.insulin
        return equilManager.bolus(detailedBolusInfo, bolusProfile)
    }

    fun showToast(s: String) {
        ToastUtils.showToastInUiThread(context, s)
    }

    fun resetData() {
        sp.putBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, false)
        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, false)
        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, false)
        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, false)
        sp.putBoolean(EquilConst.Prefs.EQUIL_BASAL_SET, false)
    }

    fun clearData() {
        resetData()
        equilManager.clearPodState()
        sp.putString(EquilConst.Prefs.EQUIL_DEVICES, "")
        sp.putString(EquilConst.Prefs.EQUIL_PASSWORD, "")
    }

    private fun playAlarm() {
        val battery = equilManager.battery
        val insulin = equilManager.currentInsulin
        val alarmBattery = sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_BATTERY, true)
        val alarmInsulin = sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN, true)
        if (battery <= 10 && alarmBattery) {
            val alarmBattery10 = sp.getBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, false)
            if (!alarmBattery10) {
                equilManager.showNotification(
                    Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.equil_low_battery) + battery + "%", Notification.NORMAL, app.aaps.core.ui.R.raw.alarm
                )
                sp.putBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, true)
            } else {
                if (battery < 5) {
                    equilManager.showNotification(
                        Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.equil_low_battery) + battery + "%", Notification.URGENT, app.aaps.core.ui.R.raw.alarm
                    )
                }
            }
        }
        if (equilManager.runMode === RunMode.RUN && alarmInsulin && equilManager.isActivationCompleted) {
            when {
                insulin in 6..10 -> {
                    val alarmInsulin10 = sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, false)
                    if (!alarmInsulin10) {
                        equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.equil_low_insulin) + insulin + "U", Notification.NORMAL, app.aaps.core.ui.R.raw.alarm)
                        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, true)
                    }
                }

                insulin in 3..5  -> {
                    val alarmInsulin5 = sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, false)
                    if (!alarmInsulin5) {
                        equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.equil_low_insulin) + insulin + "U", Notification.NORMAL, app.aaps.core.ui.R.raw.alarm)
                        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, true)
                    }
                }

                insulin <= 2     -> {
                    equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.equil_low_insulin) + insulin + "U", Notification.URGENT, app.aaps.core.ui.R.raw.alarm)
                }
            }
        }
    }

    companion object {

        private const val STATUS_CHECK_INTERVAL_MILLIS = 60 * 3000L // 1 minute
        private val BASAL_STEP_DURATION: Duration = Duration.standardMinutes(30)
        fun toDuration(dateTime: DateTime?): Duration {
            requireNotNull(dateTime) { "dateTime can not be null" }
            return Duration(dateTime.toLocalTime().millisOfDay.toLong())
        }
    }
}
