package app.aaps.pump.diaconn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.Diaconn
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.diaconn.activities.DiaconnG8BLEScanActivity
import app.aaps.pump.diaconn.database.DiaconnHistoryDatabase
import app.aaps.pump.diaconn.events.EventDiaconnG8DeviceChange
import app.aaps.pump.diaconn.keys.DiaconnBooleanKey
import app.aaps.pump.diaconn.keys.DiaconnIntKey
import app.aaps.pump.diaconn.keys.DiaconnIntNonKey
import app.aaps.pump.diaconn.keys.DiaconnIntentKey
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.pump.diaconn.service.DiaconnG8Service
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class DiaconnG8Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val context: Context,
    private val constraintChecker: ConstraintsChecker,
    private val diaconnG8Pump: DiaconnG8Pump,
    private val pumpSync: PumpSync,
    private val detailedBolusInfoStorage: DetailedBolusInfoStorage,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val aapsSchedulers: AapsSchedulers,
    private val uiInteraction: UiInteraction,
    private val diaconnHistoryDatabase: DiaconnHistoryDatabase,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(DiaconnG8Fragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_diaconn_g8)
        .pluginName(R.string.diaconn_g8_pump)
        .shortName(R.string.diaconn_g8_pump_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_pump_diaconn_g8),
    ownPreferences = listOf(
        DiaconnIntentKey::class.java, DiaconnIntKey::class.java, DiaconnBooleanKey::class.java,
        DiaconnStringNonKey::class.java, DiaconnIntNonKey::class.java,
    ),
    aapsLogger, rh, preferences, commandQueue
), Pump, Diaconn, PluginConstraints, OwnDatabasePlugin {

    private val disposable = CompositeDisposable()
    private var diaconnG8Service: DiaconnG8Service? = null
    private var mDeviceAddress = ""
    var mDeviceName = ""
    override val pumpDescription = PumpDescription().fillFor(PumpType.DIACONN_G8)

    override fun onStart() {
        super.onStart()
        val intent = Intent(context, DiaconnG8Service::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ context.unbindService(mConnection) }) { fabricPrivacy.logException(it) }

        disposable += rxBus
            .toObservable(EventConfigBuilderChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { diaconnG8Pump.reset() }
        disposable += rxBus
            .toObservable(EventDiaconnG8DeviceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ changePump() }) { fabricPrivacy.logException(it) }
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
            diaconnG8Service = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as DiaconnG8Service.LocalBinder
            diaconnG8Service = mLocalBinder.serviceInstance
        }
    }

    fun changePump() {
        mDeviceAddress = preferences.get(DiaconnStringNonKey.Address)
        mDeviceName = preferences.get(DiaconnStringNonKey.Name)
        diaconnG8Pump.reset()
        commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.device_changed), null)
    }

    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Diaconn G8 connect from: $reason")
        if (diaconnG8Service != null && mDeviceAddress != "" && mDeviceName != "") {
            val success = diaconnG8Service?.connect(reason, mDeviceAddress) == true
            if (!success) ToastUtils.errorToast(context, app.aaps.core.ui.R.string.ble_not_supported)
        }
    }

    override fun isConnected(): Boolean = diaconnG8Service?.isConnected == true
    override fun isConnecting(): Boolean = diaconnG8Service?.isConnecting == true
    override fun isHandshakeInProgress(): Boolean = false

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Diaconn G8 disconnect from: $reason")
        diaconnG8Service?.disconnect(reason)
    }

    override fun stopConnecting() {
        diaconnG8Service?.stopConnecting()
    }

    override fun getPumpStatus(reason: String) {
        diaconnG8Service?.readPumpStatus()
        pumpDescription.basalStep = diaconnG8Pump.basalStep
        pumpDescription.bolusStep = diaconnG8Pump.bolusStep
        pumpDescription.basalMaximumRate = diaconnG8Pump.maxBasalPerHours
    }

    // Diaconn Pump Interface
    override fun loadHistory(): PumpEnactResult {
        return diaconnG8Service?.loadHistory() ?: pumpEnactResultProvider.get().success(false)
    }

    override fun setUserOptions(): PumpEnactResult {
        return diaconnG8Service?.setUserSettings() ?: pumpEnactResultProvider.get().success(false)
    }

    // Constraints interface
    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfSmaller(diaconnG8Pump.maxBasal, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, diaconnG8Pump.maxBasal, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
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
        insulin.setIfSmaller(diaconnG8Pump.maxBolus, rh.gs(app.aaps.core.ui.R.string.limitingbolus, diaconnG8Pump.maxBolus, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return applyBolusConstraints(insulin)
    }

    // Pump interface
    override fun isInitialized(): Boolean =
        diaconnG8Pump.lastConnection > 0 && diaconnG8Pump.maxBasal > 0

    override fun isSuspended(): Boolean =
        diaconnG8Pump.basePauseStatus == 1

    override fun isBusy(): Boolean = false

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!isInitialized()) {
            uiInteraction.addNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set), Notification.URGENT)
            result.comment = rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            return result
        } else {
            rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
        }
        return if (diaconnG8Service?.updateBasalsInPump(profile) != true) {
            uiInteraction.addNotification(Notification.FAILED_UPDATE_PROFILE, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), Notification.URGENT)
            result.comment = rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)
            result
        } else {
            rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
            rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))
            uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
            result.success = true
            result.enacted = true
            result.comment = "OK"
            result
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        if (!isInitialized()) return true
        if (diaconnG8Pump.pumpProfiles == null) return true
        val basalValues = 24
        val basalIncrement = 60 * 60
        for (h in 0 until basalValues) {
            val pumpValue = diaconnG8Pump.pumpProfiles!![diaconnG8Pump.activeProfile][h]
            val profileValue = profile.getBasalTimeFromMidnight(h * basalIncrement)
            if (abs(pumpValue - profileValue) > pumpDescription.basalStep) {
                aapsLogger.debug(LTag.PUMP, "Diff found. Hour: $h Pump: $pumpValue Profile: $profileValue")
                return false
            }
        }
        return true
    }

    override val lastBolusTime: Long? get() = diaconnG8Pump.lastBolusTime
    override val lastBolusAmount: Double? get() = diaconnG8Pump.lastBolusAmount
    override val lastDataTime: Long get() = diaconnG8Pump.lastConnection
    override val baseBasalRate: Double get() = diaconnG8Pump.baseAmount
    override val reservoirLevel: Double get() = diaconnG8Pump.systemRemainInsulin
    override val batteryLevel: Int? get() = diaconnG8Pump.systemRemainBattery

    @Synchronized
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        var connectionOK = false
        if (detailedBolusInfo.insulin > 0) connectionOK = diaconnG8Service?.bolus(detailedBolusInfo) == true
        val result = pumpEnactResultProvider.get()
        result.success = connectionOK
        result.bolusDelivered = BolusProgressData.delivered

        if (result.success) result.enacted = true
        if (!result.success) {
            setErrorMsg(diaconnG8Pump.resultErrorCode, result)
        } else result.comment = rh.gs(app.aaps.core.ui.R.string.ok)
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        return result
    }

    override fun stopBolusDelivering() {
        diaconnG8Service?.bolusStop()
    }

    // This is called from APS
    @Synchronized
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val absoluteAfterConstrain = constraintChecker.applyBasalConstraints(ConstraintObject(absoluteRate, aapsLogger), profile).value()
        val doTempOff = baseBasalRate - absoluteAfterConstrain == 0.0
        val doLowTemp = absoluteAfterConstrain < baseBasalRate
        val doHighTemp = absoluteAfterConstrain > baseBasalRate
        if (doTempOff) {
            // If temp in progress
            if (diaconnG8Pump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)")
                return cancelTempBasal(false)
            }
            result.success = true
            result.enacted = false
            result.absolute = baseBasalRate
            result.isPercent = false
            result.isTempCancel = true
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK")
            return result
        }

        if (doLowTemp || doHighTemp) {
            // Check if some temp is already in progress
            //if(absoluteAfterConstrain > 6.0) absoluteAfterConstrain = 6.0  // pumpLimit
            //val activeTemp = activePluginProvider.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())
            if (diaconnG8Pump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: currently running")
                // Correct basal already set ?
                if (diaconnG8Pump.tempBasalAbsoluteRate == absoluteAfterConstrain && diaconnG8Pump.tempBasalRemainingMin > 4) {
                    if (!enforceNew) {
                        result.success = true
                        result.absolute = absoluteAfterConstrain
                        result.enacted = false
                        result.duration = diaconnG8Pump.tempBasalRemainingMin
                        result.isPercent = false
                        result.isTempCancel = false
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)")
                        return result
                    }
                }
            }
            temporaryBasalStorage.add(PumpSync.PumpState.TemporaryBasal(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), absoluteRate, true, tbrType, 0L, 0L))
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal $absoluteAfterConstrain U for $durationInMinutes mins (doLowTemp || doHighTemp)")
            val connectionOK: Boolean = if (durationInMinutes == 15 || durationInMinutes == 30) {
                diaconnG8Service?.tempBasalShortDuration(absoluteAfterConstrain, durationInMinutes) == true
            } else {
                val durationInHours = max(durationInMinutes / 60.0, 1.0)
                diaconnG8Service?.tempBasal(absoluteAfterConstrain, durationInHours) == true
            }

            if (connectionOK && diaconnG8Pump.isTempBasalInProgress && diaconnG8Pump.tempBasalAbsoluteRate == absoluteAfterConstrain) {
                result.enacted = true
                result.success = true
                result.comment = rh.gs(app.aaps.core.ui.R.string.ok)
                result.isTempCancel = false
                result.duration = diaconnG8Pump.tempBasalRemainingMin
                result.absolute = diaconnG8Pump.tempBasalAbsoluteRate
                result.isPercent = false
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: OK")
                return result
            }
        }

        result.enacted = false
        result.success = false
        result.comment = rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error)
        aapsLogger.error("setTempBasalAbsolute: Failed to set temp basal")
        return result
    }

    @Synchronized
    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        return if (percent == 0) {
            setTempBasalAbsolute(0.0, durationInMinutes, profile, enforceNew, tbrType)
        } else {
            var absoluteValue = profile.getBasal() * (percent / 100.0)
            absoluteValue = pumpDescription.pumpType.determineCorrectBasalSize(absoluteValue)
            aapsLogger.warn(
                LTag.PUMP,
                "setTempBasalPercent [DiaconnG8Plugin] - You are trying to use setTempBasalPercent with percent other then 0% ($percent). This will start setTempBasalAbsolute, with calculated value ($absoluteValue). Result might not be 100% correct."
            )
            setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew, tbrType)
        }

    }

    @Synchronized
    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        var insulinAfterConstraint = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        // needs to be rounded
        insulinAfterConstraint = Round.roundTo(insulinAfterConstraint, pumpDescription.extendedBolusStep)
        val result = pumpEnactResultProvider.get()

        if (diaconnG8Pump.isExtendedInProgress && abs(diaconnG8Pump.extendedBolusAmount - insulinAfterConstraint) < pumpDescription.extendedBolusStep) {
            result.enacted = false
            result.success = true
            result.comment = rh.gs(app.aaps.core.ui.R.string.ok)
            result.duration = diaconnG8Pump.extendedBolusRemainingMinutes
            result.absolute = diaconnG8Pump.extendedBolusAbsoluteRate
            result.isPercent = false
            result.isTempCancel = false
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + diaconnG8Pump.extendedBolusAmount + " Asked: " + insulinAfterConstraint)
            return result
        }
        val connectionOK = diaconnG8Service?.extendedBolus(insulinAfterConstraint, durationInMinutes) == true

        if (connectionOK) {
            result.enacted = true
            result.success = true
            result.comment = rh.gs(app.aaps.core.ui.R.string.ok)
            result.isTempCancel = false
            result.duration = diaconnG8Pump.extendedBolusRemainingMinutes
            result.absolute = diaconnG8Pump.extendedBolusAbsoluteRate
            result.bolusDelivered = diaconnG8Pump.extendedBolusAmount
            result.isPercent = false
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: OK")
            return result
        }

        result.enacted = false
        result.success = false
        setErrorMsg(diaconnG8Pump.resultErrorCode, result)
        aapsLogger.error("setExtendedBolus: Failed to extended bolus")
        return result
    }

    @Synchronized
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (diaconnG8Pump.isTempBasalInProgress) {
            diaconnG8Service?.tempBasalStop()
            result.success = !diaconnG8Pump.isTempBasalInProgress
            result.enacted = true
            result.isTempCancel = true
            if (!result.success) setErrorMsg(diaconnG8Pump.resultErrorCode, result)
        } else {
            result.success = true
            result.enacted = false
            result.isTempCancel = true
            result.comment = rh.gs(app.aaps.core.ui.R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK")
        }
        return result
    }

    @Synchronized override fun cancelExtendedBolus(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (diaconnG8Pump.isExtendedInProgress) {
            diaconnG8Service?.extendedBolusStop()
            result.success = !diaconnG8Pump.isExtendedInProgress
            result.enacted = true
            if (!result.success) {
                setErrorMsg(diaconnG8Pump.resultErrorCode, result)
                diaconnG8Service?.readPumpStatus()
            }

        } else {
            result.success = true
            result.enacted = false
            result.comment = rh.gs(app.aaps.core.ui.R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus: OK")
        }
        return result
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.G2e
    override fun model(): PumpType = PumpType.DIACONN_G8
    override fun serialNumber(): String = diaconnG8Pump.serialNo.toString()
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun loadTDDs(): PumpEnactResult = loadHistory()
    override fun getCustomActions(): List<CustomAction>? = null
    override fun executeCustomAction(customActionType: CustomActionType) {}
    override fun canHandleDST(): Boolean = false

    override fun isBatteryChangeLoggingEnabled(): Boolean {
        return preferences.get(DiaconnBooleanKey.LogBatteryChange)
    }

    @Synchronized
    fun setErrorMsg(errorCode: Int, result: PumpEnactResult) {
        when (errorCode) {
            1    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_1)
            2    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_2)
            3    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_3)
            4    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_4)
            6    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_6)
            7    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_7)
            8    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_8)
            9    -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_9)
            10   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_10)
            11   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_11)
            12   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_12)
            13   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_13)
            14   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_14)
            15   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_15)
            32   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_32)
            33   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_33)
            34   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_34)
            35   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_35)
            36   -> result.comment = rh.gs(R.string.diaconn_g8_errorcode_36)
            else -> result.comment = "not defined Error code: $errorCode"
        }
    }

    override fun clearAllTables() = diaconnHistoryDatabase.clearAllTables()

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val speedEntries = arrayOf<CharSequence>("1 U/min", "2 U/min", "3 U/min", "4 U/min", "5 U/min", "6 U/min", "7 U/min", "8 U/min")
        val speedValues = arrayOf<CharSequence>("1", "2", "3", "4", "5", "6", "7", "8")

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "diaconn_settings"
            title = rh.gs(R.string.diaconn_g8_pump)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveIntentPreference(ctx = context, intentKey = DiaconnIntentKey.BtSelector, title = R.string.selectedpump, intent = Intent(context, DiaconnG8BLEScanActivity::class.java)))
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = DiaconnIntKey.BolusSpeed, title = R.string.bolusspeed, entries = speedEntries, entryValues = speedValues))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = DiaconnBooleanKey.LogInsulinChange, title = R.string.diaconn_g8_loginsulinchange_title, summary = R.string.diaconn_g8_loginsulinchange_summary))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = DiaconnBooleanKey.LogCannulaChange, title = R.string.diaconn_g8_logcanulachange_title, summary = R.string.diaconn_g8_logcanulachange_summary))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = DiaconnBooleanKey.LogTubeChange, title = R.string.diaconn_g8_logtubechange_title, summary = R.string.diaconn_g8_logtubechange_summary))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = DiaconnBooleanKey.LogBatteryChange, title = R.string.diaconn_g8_logbatterychange_title, summary = R.string.diaconn_g8_logbatterychange_summary))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = DiaconnBooleanKey.SendLogsToCloud, title = R.string.diaconn_g8_cloudsend_title, summary = R.string.diaconn_g8_cloudsend_summary))
        }
    }
}
