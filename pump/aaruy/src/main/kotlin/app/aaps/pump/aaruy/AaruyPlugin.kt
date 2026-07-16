package app.aaps.pump.aaruy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.text.format.DateFormat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
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
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import dagger.android.HasAndroidInjector
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.keys.AaruyBooleanKey
import app.aaps.pump.aaruy.keys.AaruyDoubleKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyLongKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyService
import app.aaps.pump.aaruy.ui.AaruyOverviewFragment
import app.aaps.pump.aaruy.ui.event.EventAaruyDeviceChange
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.aaruy.keys.AaruyIntentKey
import app.aaps.pump.aaruy.ui.AaruyBLEScanActivity

@Singleton
class AaruyPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val context: Context,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val aaruyPump: AaruyPump,
    private val pumpSync: PumpSync,
    private val detailedBolusInfoStorage: DetailedBolusInfoStorage,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val aaruyHistoryDatabase: AaruyHistoryDatabase,
    private val decimalFormatter: DecimalFormatter,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(AaruyOverviewFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_aaruy_128)
        .pluginName(R.string.aaruy_pump)
        .shortName(R.string.aaruy_pump_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.aaruy_pump_description),
    ownPreferences = listOf(AaruyStringKey::class.java, AaruyIntKey::class.java, AaruyLongKey::class.java, AaruyDoubleKey::class.java,
                            AaruyBooleanKey::class.java, AaruyIntentKey::class.java),
    aapsLogger, rh, preferences, commandQueue
), Pump, Aaruy, PluginConstraints, OwnDatabasePlugin {

    private val disposable = CompositeDisposable()
    private var aaruyService: AaruyService? = null
    private var mDeviceAddress = ""
    var mDeviceName = ""

    override fun onStart() {
        super.onStart()
        aaruyPump.loadVarsFromSP()
        val intent = Intent(context, AaruyService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        disposable.add(rxBus
                           .toObservable(EventAppExit::class.java)
                           .observeOn(aapsSchedulers.io)
                           .subscribe({ context.unbindService(mConnection) }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
                           .toObservable(EventConfigBuilderChange::class.java)
                           .observeOn(aapsSchedulers.io)
                           .subscribe {
                               // reset
                           }
        )
        disposable.add(rxBus
                           .toObservable(EventAaruyDeviceChange::class.java)
                           .observeOn(aapsSchedulers.io)
                           .subscribe({ changePump() }) { fabricPrivacy.logException(it) }
        )
        changePump() // load device name
    }

    override fun onStop() {
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
        mDeviceAddress = preferences.get(AaruyStringKey.MacAddress)
        mDeviceName = preferences.get(AaruyStringKey.PumpName)
        aaruyPump.pumpSN = mDeviceName
        aaruyPump.reset()
        commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed), null)
    }

    fun getService(): AaruyService? {
        return aaruyService
    }

    // Pump Interface
    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Aaruy connect from: $reason")
        mDeviceAddress = preferences.get(AaruyStringKey.MacAddress)
        mDeviceName = preferences.get(AaruyStringKey.PumpName)
        if (aaruyService != null && mDeviceAddress != "" && mDeviceName != "") {
            val success = aaruyService?.connect(reason, mDeviceAddress) ?: false
            if (!success) ToastUtils.errorToast(context, app.aaps.core.ui.R.string.ble_not_supported)
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

    override fun stopConnecting() {
        aaruyService?.stopConnecting()
    }

    override fun getPumpStatus(reason: String) {
        aaruyService?.readPumpStatus()
    }

    override fun isInitialized(): Boolean {
        return aaruyPump.pumpState != AaruyPumpState.FIRST_INIT
    }

    override fun isSuspended(): Boolean {
        return aaruyPump.pumpState < AaruyPumpState.BASALRATE_RUNNING || aaruyPump.pumpState > AaruyPumpState.PUSHFORWARD_RUNNING
    }

    override fun isBusy(): Boolean = false

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!isInitialized()) {
            uiInteraction.addNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set), Notification.URGENT)
            result.comment = rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            return result
        }

        return if (aaruyService?.updateBasalsInPump(profile) == true) {
            rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
            rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))
            uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
            result.success = true
            result.enacted = true
            result.comment = "OK"
            result
        } else {
            uiInteraction.addNotification(Notification.FAILED_UPDATE_PROFILE, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), Notification.URGENT)
            result.comment = rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)
            result
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
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

    override val baseBasalRate: Double
        get() = if (aaruyPump.lastBasalRate < 0.025) 0.025 else aaruyPump.lastBasalRate
    override val reservoirLevel: Double
        get() = aaruyPump.reservoir
    override val batteryLevel: Int
        get() = aaruyPump.remainBattery.toInt()
    override val lastDataTime: Long get() = aaruyPump.lastConnection
    override val lastBolusTime: Long? get() = aaruyPump.lastBolusTime
    override val lastBolusAmount: Double? get() = aaruyPump.lastBolusAmount

    @Synchronized
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        var connectionOK = false
        if (detailedBolusInfo.insulin > 0) connectionOK = aaruyService?.setBolus(detailedBolusInfo) == true
        val result = pumpEnactResultProvider.get()
        result.success = connectionOK
        result.bolusDelivered = BolusProgressData.delivered

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

    @Synchronized
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!isInitialized()) {
            result.success = false
            result.enacted = false
            return result
        }

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - absoluteRate: $absoluteRate, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        // round rate to pump rate
        val pumpRate = constraintChecker.applyBasalConstraints(ConstraintObject(absoluteRate, aapsLogger), profile).value()
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

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setTempBasalPercent - percent: $percent, durationInMinutes: $durationInMinutes, enforceNew: $enforceNew")
        val result = pumpEnactResultProvider.get()
        result.success = false
        result.enacted = false
        result.comment = "Aaruy driver does not support percentage temp basals"
        return result
    }

    @Synchronized
    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
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
            return  pumpEnactResultProvider.get().enacted(false).success(true).comment(rh.gs(app.aaps.core.ui.R.string.ok)).duration(aaruyPump.extendedBolusRemainingMinutes)
                .absolute(aaruyPump.extendedBolusAbsoluteRate).isPercent(false).isTempCancel(false)
        }
        val sendOK = aaruyService?.extendedBolus(insulinAfterConstraint, durationInMinutes)
            ?: false

        if (sendOK) {
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: OK")
            return  pumpEnactResultProvider.get().enacted(true).success(true).comment(rh.gs(app.aaps.core.ui.R.string.ok)).duration(aaruyPump.extendedBolusRemainingMinutes)
                .absolute(aaruyPump.extendedBolusAbsoluteRate).bolusDelivered(aaruyPump.extendedBolusAmount).isPercent(false)
        }

        result.enacted = false
        result.success = false
        aapsLogger.error("setExtendedBolus: Failed to extended bolus")
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (!isInitialized()) return  pumpEnactResultProvider.get().success(false).enacted(false)

        aapsLogger.info(LTag.PUMP, "cancelTempBasal - enforceNew: $enforceNew")
        val connectionOK = aaruyService?.cancelTempBasal() ?: false
        return if (connectionOK && !aaruyPump.isTempBasalInProgress) {
             pumpEnactResultProvider.get().success(true).enacted(true).isTempCancel(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelTempBasal failed, connectionOK: $connectionOK, tempBasalInProgress: ${aaruyPump.isTempBasalInProgress}")
             pumpEnactResultProvider.get().success(false).enacted(false).comment("Aaruy cancelTempBasal failed")
        }
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        if (!isInitialized()) return  pumpEnactResultProvider.get().success(false).enacted(false)

        val connectionOK = aaruyService?.cancelExtendedBolus() ?: false
        return if (connectionOK && !aaruyPump.isExtendedInProgress) {
             pumpEnactResultProvider.get().success(true).enacted(true)
        } else {
            aapsLogger.error(LTag.PUMP, "cancelExtendedBolus failed, connectionOK: $connectionOK, isExtendedInProgress: ${aaruyPump.isExtendedInProgress}")
             pumpEnactResultProvider.get().success(false).enacted(false).comment("Aaruy extendedBolus failed")
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

    override fun loadTDDs(): PumpEnactResult {
        if (!(aaruyService?.isConnected!!) || !aaruyPump.canSaveHistory) return pumpEnactResultProvider.get().success(false)
        aaruyService?.requestHistoryData()
        return pumpEnactResultProvider.get().success(true)
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

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        val maxBasalPreference: Preference? = preferenceFragment.findPreference<AdaptiveStringPreference>(AaruyStringKey.PumpPrefMaxBasal.key)
        val maxBolusPreference: Preference? = preferenceFragment.findPreference(AaruyStringKey.PumpPrefMaxBolus.key)
        val lowAlertPreference: Preference? = preferenceFragment.findPreference(AaruyStringKey.PumpPrefLowAlert.key)
        val bolusStepPreference: Preference? = preferenceFragment.findPreference(AaruyIntKey.PumpPrefBolusStep.key)
        val soundRemindPreference: Preference? = preferenceFragment.findPreference(AaruyIntKey.PumpPrefSoundRemind.key)
        val soundAlarmPreference: Preference? = preferenceFragment.findPreference(AaruyIntKey.PumpPrefSoundAlarm.key)
        maxBasalPreference?.setOnPreferenceChangeListener {_, newValue->
            aaruyPump.maxBasal = newValue.toString().toDouble()
            if (aaruyPump.pumpState.isCanDoRefresh() && aaruyPump.connectionState == ConnectionState.CONNECTED) {
                aaruyService?.aaruyBle?.syncAndLoadBasicParam()
            }
            true
        }
        maxBolusPreference?.setOnPreferenceChangeListener {_, newValue->
            aaruyPump.maxBolus = newValue.toString().toDouble()
            if (aaruyPump.pumpState.isCanDoRefresh() && aaruyPump.connectionState == ConnectionState.CONNECTED) {
                aaruyService?.aaruyBle?.syncAndLoadBasicParam()
            }
            true
        }
        lowAlertPreference?.setOnPreferenceChangeListener {_, newValue->
            aaruyPump.lowAlert = newValue.toString().toDouble()
            if (aaruyPump.pumpState.isCanDoRefresh() && aaruyPump.connectionState == ConnectionState.CONNECTED) {
                aaruyService?.aaruyBle?.syncAndLoadBasicParam()
            }
            true
        }
        bolusStepPreference?.setOnPreferenceChangeListener {_, newValue->
            aaruyPump.bolusStep = 1
            if (newValue.toString() == "1")
                aaruyPump.bolusStep = 2
            if (aaruyPump.pumpState.isCanDoRefresh() && aaruyPump.connectionState == ConnectionState.CONNECTED) {
                aaruyService?.aaruyBle?.syncAndLoadBasicParam()
            }
            true
        }
        soundRemindPreference?.setOnPreferenceChangeListener {_, newValue->
            var soundRemindLocal = 0
            if (newValue.toString() == "1")
                soundRemindLocal = 1
            aaruyPump.closeInjectSound = (aaruyPump.closeInjectSound or 0x01) and (0xfffe or soundRemindLocal)
            if (aaruyPump.pumpState.isCanDoRefresh() && aaruyPump.connectionState == ConnectionState.CONNECTED) {
                aaruyService?.aaruyBle?.syncAndLoadBasicParam()
            }
            true
        }
        soundAlarmPreference?.setOnPreferenceChangeListener {_, newValue->
            var soundAlarmLocal = 0
            if (newValue.toString() == "1")
                soundAlarmLocal = 1
            aaruyPump.closeInjectSound = (aaruyPump.closeInjectSound or 0x02) and (0xfffd or (soundAlarmLocal shl 1))
            if (aaruyPump.pumpState.isCanDoRefresh() && aaruyPump.connectionState == ConnectionState.CONNECTED) {
                aaruyService?.aaruyBle?.syncAndLoadBasicParam()
            }
            true
        }
    }

    // Aaruy Pump
    override fun clearAlarms(): PumpEnactResult {
        if (!isInitialized()) return  pumpEnactResultProvider.get().success(false).enacted(false)
        val connectionOK = aaruyService?.clearAlarms() ?: false
        return  pumpEnactResultProvider.get().success(connectionOK)
    }

    override fun clearAllTables() = aaruyHistoryDatabase.clearAllTables()

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return


        val openOrCloseEntries = arrayOf<CharSequence>(rh.gs(R.string.setting_aaruy_on), rh.gs(R.string.setting_aaruy_off))
        val openOrCloseValues = arrayOf<CharSequence>("0", "1")

        val speedEntries = arrayOf<CharSequence>("0.025", "0.05")
        val speedValues = arrayOf<CharSequence>("0", "1")

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "aaruy_settings"
            title = rh.gs(R.string.aaruy_pump)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveIntentPreference(ctx = context, intentKey = AaruyIntentKey.BtSelector, title = R.string.selectedpump, intent = Intent(context, AaruyBLEScanActivity ::class.java)))
            addPreference(AdaptiveStringPreference(ctx = context, stringKey = AaruyStringKey.PumpPrefMaxBasal, title = R.string.setting_insulin_max_basal,
                                                   validatorParams = DefaultEditTextValidator.Parameters(
                                                       testType = EditTextValidator.TEST_NUMERIC,
                                                       minNumber = 1,
                                                       maxNumber = 35
                                                   )))
            addPreference(AdaptiveStringPreference(ctx = context, stringKey = AaruyStringKey.PumpPrefMaxBolus, title = R.string.setting_insulin_max_bolus,
                                                   validatorParams = DefaultEditTextValidator.Parameters(
                                                       testType = EditTextValidator.TEST_NUMERIC,
                                                       minNumber = 1,
                                                       maxNumber = 25
                                                   )))
            addPreference(AdaptiveStringPreference(ctx = context, stringKey = AaruyStringKey.PumpPrefLowAlert, title = R.string.setting_insulin_low_reservoir,
                                                   validatorParams = DefaultEditTextValidator.Parameters(
                                                       testType = EditTextValidator.TEST_NUMERIC,
                                                       minNumber = 10,
                                                       maxNumber = 50
                                                   )))
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = AaruyIntKey.PumpPrefBolusStep, title = R.string.setting_insulin_bolus_rate, entries = speedEntries, entryValues = speedValues))
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = AaruyIntKey.PumpPrefSoundRemind, title = R.string.setting_inject_sound_remind, entries = openOrCloseEntries, entryValues = openOrCloseValues))
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = AaruyIntKey.PumpPrefSoundAlarm, title = R.string.setting_inject_sound_alarm, entries = openOrCloseEntries, entryValues = openOrCloseValues))
        }
    }

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
}