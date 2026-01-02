package app.aaps.pump.danarv2

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T.Companion.mins
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round.ceilTo
import app.aaps.core.interfaces.utils.Round.floorTo
import app.aaps.core.interfaces.utils.Round.roundTo
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaBooleanKey
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.AbstractDanaRPlugin
import app.aaps.pump.danarv2.services.DanaRv2ExecutionService
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Vector
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class DanaRv2Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    rxBus: RxBus,
    private val context: Context,
    rh: ResourceHelper,
    constraintChecker: ConstraintsChecker,
    activePlugin: ActivePlugin,
    commandQueue: CommandQueue,
    danaPump: DanaPump,
    private val detailedBolusInfoStorage: DetailedBolusInfoStorage,
    private val temporaryBasalStorage: TemporaryBasalStorage,
    dateUtil: DateUtil,
    private val fabricPrivacy: FabricPrivacy,
    pumpSync: PumpSync,
    preferences: Preferences,
    uiInteraction: UiInteraction,
    danaHistoryDatabase: DanaHistoryDatabase,
    decimalFormatter: DecimalFormatter,
    pumpEnactResultProvider: Provider<PumpEnactResult>
) : AbstractDanaRPlugin(
    danaPump,
    aapsLogger,
    rh,
    preferences,
    commandQueue,
    constraintChecker,
    aapsSchedulers,
    rxBus,
    activePlugin,
    dateUtil,
    pumpSync,
    uiInteraction,
    danaHistoryDatabase,
    decimalFormatter,
    pumpEnactResultProvider
) {

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            executionService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as DanaRv2ExecutionService.LocalBinder
            executionService = mLocalBinder.serviceInstance
        }
    }

    init {
        pluginDescription.description(R.string.description_pump_dana_r_v2)
        pumpDescription.fillFor(PumpType.DANA_RV2)
    }

    override fun onStart() {
        val intent = Intent(context, DanaRv2ExecutionService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ context.unbindService(mConnection) }, fabricPrivacy::logException)
        super.onStart()
    }

    override fun onStop() {
        context.unbindService(mConnection)
        disposable.clear()
        super.onStop()
    }

    override val name: String
        // Plugin base interface
        get() = rh.gs(R.string.danarv2pump)
    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override fun isInitialized(): Boolean {
        return danaPump.lastConnection > 0 && danaPump.maxBasal > 0 && danaPump.isPasswordOK
    }

    override fun isHandshakeInProgress(): Boolean =
        executionService?.isHandshakeInProgress == true

    override fun finishHandshaking() {
        executionService?.finishHandshaking()
    }

    // Pump interface
    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        if (detailedBolusInfo.insulin == 0.0 || detailedBolusInfo.carbs > 0) {
            throw IllegalArgumentException(detailedBolusInfo.toString(), Exception())
        }
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        // v2 stores end time for bolus, we need to adjust time
        // default delivery speed is 12 sec/U
        val preferencesSpeed = preferences.get(DanaIntKey.BolusSpeed)
        var speed = 12
        when (preferencesSpeed) {
            0 -> speed = 12
            1 -> speed = 30
            2 -> speed = 60
        }
        detailedBolusInfo.timestamp = dateUtil.now() + (speed * detailedBolusInfo.insulin * 1000).toLong()
        detailedBolusInfoStorage.add(detailedBolusInfo) // will be picked up on reading history
        var connectionOK = false
        if (detailedBolusInfo.insulin > 0) connectionOK = executionService?.bolus(detailedBolusInfo) == true
        val result = pumpEnactResultProvider.get()
        result.success(connectionOK && (abs(detailedBolusInfo.insulin - BolusProgressData.delivered) < pumpDescription.bolusStep || danaPump.bolusStopped))
            .bolusDelivered(BolusProgressData.delivered)
        if (!result.success) result.comment(
            rh.gs(
                R.string.boluserrorcode, detailedBolusInfo.insulin, BolusProgressData.delivered,
                danaPump.bolusStartErrorCode
            )
        ) else result.comment(app.aaps.core.ui.R.string.ok)
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        // remove carbs because it's get from history separately
        return result
    }

    override fun stopBolusDelivering() {
        executionService?.bolusStop() ?: error("stopBolusDelivering sExecutionService is null")
    }

    // This is called from APS
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        var absoluteRateReq = absoluteRate
        var result = pumpEnactResultProvider.get()
        absoluteRateReq = constraintChecker.applyBasalConstraints(ConstraintObject(absoluteRateReq, aapsLogger), profile).value()
        var doTempOff = baseBasalRate - absoluteRateReq == 0.0 && absoluteRateReq >= 0.10
        val doLowTemp = absoluteRateReq < baseBasalRate || absoluteRateReq < 0.10
        val doHighTemp = absoluteRateReq > baseBasalRate
        var percentRate = (absoluteRateReq / baseBasalRate * 100).toInt()
        // Any basal less than 0.10u/h will be dumped once per hour, not every 4 minutes. So if it's less than .10u/h, set a zero temp.
        if (absoluteRateReq < 0.10) percentRate = 0
        percentRate =
            if (percentRate < 100) ceilTo(percentRate.toDouble(), 10.0).toInt() else floorTo(percentRate.toDouble(), 10.0).toInt()
        if (percentRate > 500) // Special high temp 500/15min
            percentRate = 500
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Calculated percent rate: $percentRate")
        if (percentRate == 100) doTempOff = true
        if (doTempOff) {
            // If temp in progress
            if (danaPump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)")
                return cancelTempBasal(false)
            }
            result.success(true).enacted(false).percent(100).isPercent(true).isTempCancel(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK")
            return result
        }
        if (doLowTemp || doHighTemp) {
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress) {
                // Correct basal already set ?
                if (danaPump.tempBasalPercent == percentRate && danaPump.tempBasalRemainingMin > 4) {
                    if (!enforceNew) {
                        result.success(true).percent(percentRate).enacted(false).duration(danaPump.tempBasalRemainingMin).isPercent(true).isTempCancel(false)
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)")
                        return result
                    }
                }
            }
            temporaryBasalStorage.add(PumpSync.PumpState.TemporaryBasal(dateUtil.now(), mins(durationInMinutes.toLong()).msecs(), percentRate.toDouble(), false, tbrType, 0L, 0L))
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal $percentRate% for $durationInMinutes minutes (doLowTemp || doHighTemp)")
            result = if (percentRate == 0 && durationInMinutes > 30) {
                setTempBasalPercent(percentRate, durationInMinutes, profile, enforceNew, tbrType)
            } else {
                // use special APS temp basal call ... 100+/15min .... 100-/30min
                setHighTempBasalPercent(percentRate, durationInMinutes)
            }
            if (!result.success) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set high temp basal")
                return result
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: high temp basal set ok")
            return result
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error")
        result.success(false).comment("Internal error")
        return result
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        var percentReq = percent
        val pump = danaPump
        val result = pumpEnactResultProvider.get()
        percentReq = constraintChecker.applyBasalPercentConstraints(ConstraintObject(percentReq, aapsLogger), profile).value()
        if (percentReq < 0) {
            result.isTempCancel(false).enacted(false).success(false).comment(app.aaps.core.ui.R.string.invalid_input)
            aapsLogger.error("setTempBasalPercent: Invalid input")
            return result
        }
        if (percentReq > pumpDescription.maxTempPercent) percentReq = pumpDescription.maxTempPercent
        if (danaPump.isTempBasalInProgress && danaPump.tempBasalPercent == percentReq && danaPump.tempBasalRemainingMin > 4 && !enforceNew) {
            result.enacted(false).success(true).isTempCancel(false).comment(app.aaps.core.ui.R.string.ok).duration(pump.tempBasalRemainingMin).percent(pump.tempBasalPercent).isPercent(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: Correct value already set")
            return result
        }
        temporaryBasalStorage.add(PumpSync.PumpState.TemporaryBasal(dateUtil.now(), mins(durationInMinutes.toLong()).msecs(), percentReq.toDouble(), false, tbrType, 0L, 0L))
        val connectionOK: Boolean = if (durationInMinutes == 15 || durationInMinutes == 30) {
            executionService?.tempBasalShortDuration(percentReq, durationInMinutes) == true
        } else {
            val durationInHours = max(durationInMinutes / 60, 1)
            executionService?.tempBasal(percentReq, durationInHours) == true
        }
        if (connectionOK && pump.isTempBasalInProgress && pump.tempBasalPercent == percentReq) {
            result.enacted(true).success(true).comment(app.aaps.core.ui.R.string.ok).isTempCancel(false).duration(pump.tempBasalRemainingMin).percent(pump.tempBasalPercent).isPercent(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: OK")
            return result
        }
        result.enacted(false).success(false).comment(app.aaps.core.ui.R.string.temp_basal_delivery_error)
        aapsLogger.error("setTempBasalPercent: Failed to set temp basal")
        return result
    }

    private fun setHighTempBasalPercent(percent: Int, durationInMinutes: Int): PumpEnactResult {
        val pump = danaPump
        val result = pumpEnactResultProvider.get()
        val connectionOK = executionService?.highTempBasal(percent, durationInMinutes) == true
        if (connectionOK && pump.isTempBasalInProgress && pump.tempBasalPercent == percent) {
            result.enacted(true).success(true).comment(app.aaps.core.ui.R.string.ok).isTempCancel(false).duration(pump.tempBasalRemainingMin).percent(pump.tempBasalPercent).isPercent(true)
            aapsLogger.debug(LTag.PUMP, "setHighTempBasalPercent: OK")
            return result
        }
        result.enacted(false).success(false).comment(R.string.danar_valuenotsetproperly)
        aapsLogger.error("setHighTempBasalPercent: Failed to set temp basal")
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (danaPump.isTempBasalInProgress) {
            executionService?.tempBasalStop()
            result.success(true).enacted(true).isTempCancel(true)
        } else {
            result.success(true).isTempCancel(true).comment(app.aaps.core.ui.R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK")
        }
        return result
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        var insulinReq = insulin
        val pump = danaPump
        insulinReq = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(insulinReq, aapsLogger)).value()
        // needs to be rounded
        val durationInHalfHours = max(durationInMinutes / 30, 1)
        insulinReq = roundTo(insulinReq, pumpDescription.extendedBolusStep)
        val result = pumpEnactResultProvider.get()
        if (danaPump.isExtendedInProgress && abs(danaPump.extendedBolusAmount - insulinReq) < pumpDescription.extendedBolusStep) {
            result.enacted(false)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .duration(pump.extendedBolusRemainingMinutes)
                .absolute(pump.extendedBolusAbsoluteRate)
                .isPercent(false)
                .isTempCancel(false)
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + pump.extendedBolusAmount + " Asked: " + insulinReq)
            return result
        }
        val connectionOK = executionService?.extendedBolus(insulinReq, durationInHalfHours) == true
        if (connectionOK && pump.isExtendedInProgress && abs(pump.extendedBolusAmount - insulinReq) < pumpDescription.extendedBolusStep) {
            result.enacted(true)
                .success(true)
                .comment(app.aaps.core.ui.R.string.ok)
                .isTempCancel(false)
                .duration(pump.extendedBolusRemainingMinutes)
                .absolute(pump.extendedBolusAbsoluteRate)
                .isPercent(false)
            if (!preferences.get(DanaBooleanKey.UseExtended)) result.bolusDelivered(pump.extendedBolusAmount)
            aapsLogger.debug(LTag.PUMP, "setExtendedBolus: OK")
            return result
        }
        result.enacted(false).success(false).comment(R.string.danar_valuenotsetproperly)
        aapsLogger.error("setExtendedBolus: Failed to extended bolus")
        return result
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (danaPump.isExtendedInProgress) {
            executionService?.extendedBolusStop()
            result.enacted(true).success(!danaPump.isExtendedInProgress).isTempCancel(true)
        } else {
            result.success(true).enacted(false).comment(app.aaps.core.ui.R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus: OK")
        }
        return result
    }

    override fun model(): PumpType {
        return PumpType.DANA_RV2
    }

    override fun loadEvents(): PumpEnactResult =
        executionService?.loadEvents() ?: error("Execution service is null")

    override fun setUserOptions(): PumpEnactResult =
        executionService?.setUserOptions() ?: error("Execution service is null")

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        var entries = emptyArray<CharSequence>()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val devices = Vector<CharSequence>()
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bta ->
                for (dev in bta.bondedDevices)
                    dev.name?.let { name -> devices.add(name) }
            }
            entries = devices.toTypedArray()
        } else ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))

        val speedEntries = arrayOf<CharSequence>("12 s/U", "30 s/U", "60 s/U")
        val speedValues = arrayOf<CharSequence>("0", "1", "2")

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "danar_v2_settings"
            title = rh.gs(R.string.danar_pump_settings)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveListPreference(ctx = context, stringKey = DanaStringKey.RName, title = R.string.danar_bt_name_title, dialogTitle = R.string.danar_bt_name_title, entries = entries, entryValues = entries))
            addPreference(
                AdaptiveIntPreference(
                    ctx = context, intKey = DanaIntKey.Password, title = R.string.danar_password_title,
                    validatorParams = DefaultEditTextValidator.Parameters(
                        testType = EditTextValidator.TEST_REGEXP,
                        customRegexp = rh.gs(app.aaps.core.validators.R.string.fourdigitnumber),
                        testErrorString = rh.gs(app.aaps.core.validators.R.string.error_mustbe4digitnumber)
                    )
                )
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = DanaIntKey.BolusSpeed, title = R.string.bolusspeed, dialogTitle = R.string.bolusspeed, entries = speedEntries, entryValues = speedValues))
        }
    }
}
