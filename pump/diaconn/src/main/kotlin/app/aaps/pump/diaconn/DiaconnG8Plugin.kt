package app.aaps.pump.diaconn

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
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.Diaconn
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.icons.IcPluginDiaconn
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.diaconn.compose.DiaconnComposeContent
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
import kotlinx.coroutines.flow.StateFlow
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
    private val notificationManager: NotificationManager,
    private val diaconnHistoryDatabase: DiaconnHistoryDatabase,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val bolusProgressData: BolusProgressData,
    private val blePreCheck: BlePreCheck
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            DiaconnComposeContent(
                pluginName = rh.gs(R.string.diaconn_g8_pump),
                context = context,
                blePreCheck = blePreCheck
            )
        }
        .icon(IcPluginDiaconn)
        .pluginName(R.string.diaconn_g8_pump)
        .shortName(R.string.diaconn_g8_pump_shortname)
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
            .subscribe({
                           pumpSync.connectNewPump()
                           changePump()
                       }) { fabricPrivacy.logException(it) }
        changePump() // load device name on app start
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
        val newAddress = preferences.get(DiaconnStringNonKey.Address)
        val newName = preferences.get(DiaconnStringNonKey.Name)

        // Check if device actually changed by comparing addresses
        val isDeviceChanged = mDeviceAddress.isNotEmpty() && mDeviceAddress != newAddress

        // Firmware 3.58+: force disconnect since disconnect() is skipped for permanent connection
        if (isDeviceChanged && diaconnG8Pump.isPumpVersionGe3_58) {
            diaconnG8Service?.disconnect("Pump changed")
        }

        mDeviceAddress = newAddress
        mDeviceName = newName
        diaconnG8Pump.reset()

        // Use different message for app start vs actual device change
        val reason = if (isDeviceChanged)
            rh.gs(app.aaps.core.ui.R.string.device_changed)
        else
            rh.gs(R.string.gettingpumpsettings)
        commandQueue.readStatus(reason, null)
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
        // Firmware 3.58+: permanent BLE connection, skip actual disconnect
        if (diaconnG8Pump.isPumpVersionGe3_58) {
            aapsLogger.debug(LTag.PUMP, "Skipping disconnect for firmware 3.58+ (permanent connection)")
            return
        }
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
    override fun isConfigured(): Boolean =
        mDeviceAddress.isNotEmpty() && mDeviceName.isNotEmpty()

    override fun isInitialized(): Boolean =
        isConfigured() && diaconnG8Pump.lastConnection > 0 && diaconnG8Pump.maxBasal > 0

    override fun isSuspended(): Boolean =
        diaconnG8Pump.basePauseStatus == 1

    override fun isBusy(): Boolean = false

    override fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!isInitialized()) {
            notificationManager.post(NotificationId.PROFILE_NOT_SET_NOT_INITIALIZED, app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set, level = NotificationLevel.URGENT)
            result.comment = rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            return result
        } else {
            notificationManager.dismiss(NotificationId.PROFILE_NOT_SET_NOT_INITIALIZED)
        }
        return if (diaconnG8Service?.updateBasalsInPump(profile) != true) {
            notificationManager.post(NotificationId.FAILED_UPDATE_PROFILE, app.aaps.core.ui.R.string.failed_update_basal_profile, level = NotificationLevel.URGENT)
            result.comment = rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)
            result
        } else {
            notificationManager.dismiss(NotificationId.PROFILE_NOT_SET_NOT_INITIALIZED)
            notificationManager.dismiss(NotificationId.FAILED_UPDATE_PROFILE)
            notificationManager.post(NotificationId.PROFILE_SET_OK, app.aaps.core.ui.R.string.profile_set_ok, validMinutes = 60)
            result.success = true
            result.enacted = true
            result.comment = "OK"
            result
        }
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
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

    override val lastDataTime: StateFlow<Long> = diaconnG8Pump.lastConnectionFlow
    override val lastBolusTime: StateFlow<Long?> = diaconnG8Pump.lastBolusTimeFlow
    override val lastBolusAmount: StateFlow<PumpInsulin?> = diaconnG8Pump.lastBolusAmountFlow.mapState { it?.let(::PumpInsulin) }
    override val reservoirLevel: StateFlow<PumpInsulin> = diaconnG8Pump.systemRemainInsulinFlow.mapState(::PumpInsulin)
    override val batteryLevel: StateFlow<Int?> = diaconnG8Pump.systemRemainBatteryFlow

    override val baseBasalRate: PumpRate get() = PumpRate(diaconnG8Pump.baseAmount)

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
        result.bolusDelivered = bolusProgressData.state.value?.delivered ?: 0.0

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
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        val doTempOff = baseBasalRate.cU - absoluteRate == 0.0
        val doLowTemp = absoluteRate < baseBasalRate.cU
        val doHighTemp = absoluteRate > baseBasalRate.cU
        if (doTempOff) {
            // If temp in progress
            if (diaconnG8Pump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)")
                return cancelTempBasal(false)
            }
            result.success = true
            result.enacted = false
            result.absolute = baseBasalRate.cU
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
                if (diaconnG8Pump.tempBasalAbsoluteRate == absoluteRate && diaconnG8Pump.tempBasalRemainingMin > 4) {
                    if (!enforceNew) {
                        result.success = true
                        result.absolute = absoluteRate
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
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal $absoluteRate U for $durationInMinutes mins (doLowTemp || doHighTemp)")
            val connectionOK: Boolean = if (durationInMinutes == 15 || durationInMinutes == 30) {
                diaconnG8Service?.tempBasalShortDuration(absoluteRate, durationInMinutes) == true
            } else {
                val durationInHours = max(durationInMinutes / 60.0, 1.0)
                diaconnG8Service?.tempBasal(absoluteRate, durationInHours) == true
            }

            if (connectionOK && diaconnG8Pump.isTempBasalInProgress && diaconnG8Pump.tempBasalAbsoluteRate == absoluteRate) {
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
    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        error("Pump doesn't support percent basal rate")

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

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "diaconn_settings",
        titleResId = R.string.diaconn_g8_pump,
        items = listOf(
            DiaconnIntKey.BolusSpeed,
            DiaconnBooleanKey.LogInsulinChange,
            DiaconnBooleanKey.LogCannulaChange,
            DiaconnBooleanKey.LogTubeChange,
            DiaconnBooleanKey.LogBatteryChange,
            DiaconnBooleanKey.SendLogsToCloud
        ),
        icon = pluginDescription.icon
    )

}
