package app.aaps.pump.danar

import android.text.format.DateFormat
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.Dana
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round.roundTo
import app.aaps.core.keys.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.dana.DanaFragment
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaBooleanKey
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.dana.keys.DanaIntentKey
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.services.AbstractDanaRExecutionService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by mike on 28.01.2018.
 */
abstract class AbstractDanaRPlugin protected constructor(
    protected var danaPump: DanaPump,
    rh: ResourceHelper,
    protected var constraintChecker: ConstraintsChecker,
    aapsLogger: AAPSLogger,
    protected var aapsSchedulers: AapsSchedulers,
    commandQueue: CommandQueue,
    protected var rxBus: RxBus,
    protected var activePlugin: ActivePlugin,
    protected var dateUtil: DateUtil,
    protected var pumpSync: PumpSync,
    protected val preferences: Preferences,
    protected var uiInteraction: UiInteraction,
    protected var danaHistoryDatabase: DanaHistoryDatabase,
    protected var decimalFormatter: DecimalFormatter,
    protected var instantiator: Instantiator
) : PumpPluginBase(
    PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(DanaFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_danars_128)
        .pluginName(app.aaps.pump.dana.R.string.danarspump)
        .shortName(app.aaps.pump.dana.R.string.danarpump_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(app.aaps.pump.dana.R.string.description_pump_dana_r),
    aapsLogger, rh, commandQueue
), Pump, Dana, PluginConstraints, OwnDatabasePlugin {

    protected var executionService: AbstractDanaRExecutionService? = null
    protected var disposable = CompositeDisposable()
    override var pumpDescription = PumpDescription()
        protected set

    // Make plugin preferences available to AAPS
    init {
        preferences.registerPreferences(DanaStringKey::class.java)
        preferences.registerPreferences(DanaIntKey::class.java)
        preferences.registerPreferences(DanaBooleanKey::class.java)
        preferences.registerPreferences(DanaIntentKey::class.java)
    }

    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { danaPump.reset() }

        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { event: EventPreferenceChange ->
                if (event.isChanged(DanaStringKey.DanaRName.key)) {
                    danaPump.reset()
                    pumpSync.connectNewPump(true)
                    commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed), null)
                }
            }
        danaPump.serialNumber = preferences.get(DanaStringKey.DanaRName) // fill at start to allow password reset
    }

    override fun onStop() {
        super.onStop()
        disposable.clear()
    }

    override fun isSuspended(): Boolean {
        return danaPump.pumpSuspended
    }

    override fun isBusy(): Boolean =
        executionService?.isConnected == true || executionService?.isConnecting == true

    // Pump interface
    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
        if (executionService == null) {
            aapsLogger.error("setNewBasalProfile sExecutionService is null")
            result.comment("setNewBasalProfile sExecutionService is null")
            return result
        }
        if (!isInitialized()) {
            aapsLogger.error("setNewBasalProfile not initialized")
            uiInteraction.addNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set), Notification.URGENT)
            result.comment(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            return result
        } else {
            rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
        }
        if (executionService?.updateBasalsInPump(profile) != true) {
            uiInteraction.addNotification(Notification.FAILED_UPDATE_PROFILE, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), Notification.URGENT)
            result.comment(app.aaps.core.ui.R.string.failed_update_basal_profile)
        } else {
            rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
            rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))
            uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
            result.success(true).enacted(true).comment("OK")
        }
        return result
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
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

    override fun lastDataTime(): Long {
        return danaPump.lastConnection
    }

    override val baseBasalRate: Double
        get() = danaPump.currentBasal
    override val reservoirLevel: Double
        get() = danaPump.reservoirRemainingUnits
    override val batteryLevel: Int
        get() = danaPump.batteryRemaining

    override fun stopBolusDelivering() {
        if (executionService == null) {
            aapsLogger.error("stopBolusDelivering sExecutionService is null")
            return
        }
        executionService?.bolusStop()
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        var percentReq = percent
        val result = instantiator.providePumpEnactResult()
        percentReq = constraintChecker.applyBasalPercentConstraints(ConstraintObject(percentReq, aapsLogger), profile).value()
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
                danaPump.tempBasalPercent.toDouble(),
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

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        var insulinReq = insulin
        insulinReq = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(insulinReq, aapsLogger)).value()
        // needs to be rounded
        val durationInHalfHours = max(durationInMinutes / 30, 1)
        insulinReq = roundTo(insulinReq, pumpDescription.extendedBolusStep)
        val result = instantiator.providePumpEnactResult()
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
            if (!preferences.get(DanaBooleanKey.DanaRUseExtended)) result.bolusDelivered(danaPump.extendedBolusAmount)
            pumpSync.syncExtendedBolusWithPumpId(
                danaPump.extendedBolusStart,
                danaPump.extendedBolusAmount,
                danaPump.extendedBolusDuration,
                preferences.get(DanaBooleanKey.DanaRUseExtended),
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

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
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

    override fun getPumpStatus(reason: String) {
        executionService?.getPumpStatus()
        pumpDescription.basalStep = danaPump.basalStep
        pumpDescription.bolusStep = danaPump.bolusStep
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val pump = danaPump
        val now = System.currentTimeMillis()
        if (pump.lastConnection + 60 * 60 * 1000L < System.currentTimeMillis()) {
            return JSONObject()
        }
        val pumpJson = JSONObject()
        val battery = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        try {
            battery.put("percent", pump.batteryRemaining)
            status.put("status", if (pump.pumpSuspended) "suspended" else "normal")
            status.put("timestamp", dateUtil.toISOString(pump.lastConnection))
            extended.put("Version", version)
            if (pump.lastBolusTime != 0L) {
                extended.put("LastBolus", dateUtil.dateAndTimeString(pump.lastBolusTime))
                extended.put("LastBolusAmount", pump.lastBolusAmount)
            }
            val (temporaryBasal, extendedBolus) = pumpSync.expectedPumpState()
            if (temporaryBasal != null) {
                extended.put("TempBasalAbsoluteRate", temporaryBasal.convertedToAbsolute(now, profile))
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(temporaryBasal.timestamp))
                extended.put("TempBasalRemaining", temporaryBasal.plannedRemainingMinutes)
            }
            if (extendedBolus != null) {
                extended.put("ExtendedBolusAbsoluteRate", extendedBolus.rate)
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(extendedBolus.timestamp))
                extended.put("ExtendedBolusRemaining", extendedBolus.plannedRemainingMinutes)
            }
            extended.put("BaseBasalRate", baseBasalRate)
            extended.put("ActiveProfile", profileName)
            pumpJson.put("battery", battery)
            pumpJson.put("status", status)
            pumpJson.put("extended", extended)
            pumpJson.put("reservoir", pump.reservoirRemainingUnits.toInt())
            pumpJson.put("clock", dateUtil.toISOString(dateUtil.now()))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return pumpJson
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.Sooil
    }

    override fun serialNumber(): String {
        return danaPump.serialNumber
    }

    /**
     * DanaR interface
     */
    override fun loadHistory(type: Byte): PumpEnactResult =
        executionService?.loadHistory(type) ?: instantiator.providePumpEnactResult()

    /**
     * Constraint interface
     */
    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfSmaller(danaPump.maxBasal, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, danaPump.maxBasal, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        percentRate.setIfGreater(0, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, 0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        percentRate.setIfSmaller(pumpDescription.maxTempPercent, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, pumpDescription.maxTempPercent, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfSmaller(danaPump.maxBolus, rh.gs(app.aaps.core.ui.R.string.limitingbolus, danaPump.maxBolus, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return applyBolusConstraints(insulin)
    }

    override fun loadTDDs(): PumpEnactResult {
        return loadHistory(RecordTypes.RECORD_TYPE_DAILY)
    }

    // Reply for sms communicator
    override fun shortStatus(veryShort: Boolean): String {
        var ret = ""
        if (danaPump.lastConnection != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - danaPump.lastConnection
            val agoMin = (agoMilliseconds / 60.0 / 1000.0).toInt()
            ret += "LastConn: $agoMin min ago\n"
        }
        if (danaPump.lastBolusTime != 0L) {
            ret += "LastBolus: ${decimalFormatter.to2Decimal(danaPump.lastBolusAmount)}U @${DateFormat.format("HH:mm", danaPump.lastBolusTime)}\n"
        }
        val (temporaryBasal, extendedBolus) = pumpSync.expectedPumpState()
        if (temporaryBasal != null) {
            ret += "Temp: ${temporaryBasal.toStringFull(dateUtil, rh)}\n"
        }
        if (extendedBolus != null) {
            ret += "Extended: ${extendedBolus.toStringFull(dateUtil, rh)}\n"
        }
        if (!veryShort) {
            ret += "TDD: ${decimalFormatter.to0Decimal(danaPump.dailyTotalUnits)} / ${danaPump.maxDailyTotalUnits} U\n"
        }
        ret += "Reserv: ${decimalFormatter.to0Decimal(danaPump.reservoirRemainingUnits)}U\n"
        ret += "Batt: ${danaPump.batteryRemaining}\n"
        return ret
    }

    // TODO: daily total constraint
    override fun canHandleDST(): Boolean = false

    override fun clearPairing() {}
    override fun clearAllTables() {
        danaHistoryDatabase.clearAllTables()
    }
}
