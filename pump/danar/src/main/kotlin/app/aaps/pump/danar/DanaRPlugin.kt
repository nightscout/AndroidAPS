package app.aaps.pump.danar

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round.ceilTo
import app.aaps.core.interfaces.utils.Round.floorTo
import app.aaps.core.interfaces.utils.Round.roundTo
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaBooleanKey
import app.aaps.pump.dana.keys.DanaIntKey
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.services.DanaRExecutionService
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Vector
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class DanaRPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    rxBus: RxBus,
    private val context: Context,
    rh: ResourceHelper,
    constraintsChecker: ConstraintsChecker,
    activePlugin: ActivePlugin,
    commandQueue: CommandQueue,
    danaPump: DanaPump,
    dateUtil: DateUtil,
    private val fabricPrivacy: FabricPrivacy,
    pumpSync: PumpSync,
    preferences: Preferences,
    uiInteraction: UiInteraction,
    danaHistoryDatabase: DanaHistoryDatabase,
    decimalFormatter: DecimalFormatter,
    instantiator: Instantiator
) : AbstractDanaRPlugin(
    danaPump,
    rh,
    constraintsChecker,
    aapsLogger,
    aapsSchedulers,
    commandQueue,
    rxBus,
    activePlugin,
    dateUtil,
    pumpSync,
    preferences,
    uiInteraction,
    danaHistoryDatabase,
    decimalFormatter,
    instantiator
) {

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            executionService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as DanaRExecutionService.LocalBinder
            executionService = mLocalBinder.serviceInstance
        }
    }

    init {
        pumpDescription.fillFor(PumpType.DANA_R)
    }

    override fun onStart() {
        val intent = Intent(context, DanaRExecutionService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (it.isChanged(DanaBooleanKey.DanaRUseExtended.key)) {
                               if (pumpSync.expectedPumpState().extendedBolus != null) {
                                   executionService?.extendedBolusStop()
                               }
                           }
                       }, fabricPrivacy::logException)
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
        get() = rh.gs(app.aaps.pump.dana.R.string.danarpump)
    override val isFakingTempsByExtendedBoluses: Boolean
        // Pump interface
        get() = preferences.get(DanaBooleanKey.DanaRUseExtended)

    override fun isInitialized(): Boolean {
        return danaPump.lastConnection > 0 && danaPump.isExtendedBolusEnabled && danaPump.maxBasal > 0 && danaPump.isPasswordOK
    }

    override fun isHandshakeInProgress(): Boolean =
        executionService?.isHandshakeInProgress == true

    override fun finishHandshaking() {
        executionService?.finishHandshaking()
    }

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        if (detailedBolusInfo.insulin == 0.0 || detailedBolusInfo.carbs > 0) {
            throw IllegalArgumentException(detailedBolusInfo.toString(), Exception())
        }
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        val t = EventOverviewBolusProgress.Treatment(0.0, 0, detailedBolusInfo.bolusType === BS.Type.SMB, detailedBolusInfo.id)
        var connectionOK = false
        if (detailedBolusInfo.insulin > 0) connectionOK =
            executionService?.bolus(
                detailedBolusInfo.insulin,
                detailedBolusInfo.carbs.toInt(),
                detailedBolusInfo.carbsTimestamp ?: detailedBolusInfo.timestamp,
                t
            ) == true
        val result = instantiator.providePumpEnactResult()
        result.success(connectionOK && abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.bolusStep)
            .bolusDelivered(t.insulin)
        if (!result.success) result.comment(
            rh.gs(
                app.aaps.pump.dana.R.string.boluserrorcode,
                detailedBolusInfo.insulin,
                t.insulin,
                danaPump.bolusStartErrorCode
            )
        ) else result.comment(app.aaps.core.ui.R.string.ok)
        aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
        detailedBolusInfo.insulin = t.insulin
        detailedBolusInfo.timestamp = System.currentTimeMillis()
        if (detailedBolusInfo.insulin > 0) pumpSync.syncBolusWithPumpId(
            detailedBolusInfo.timestamp,
            detailedBolusInfo.insulin,
            detailedBolusInfo.bolusType,
            dateUtil.now(),
            PumpType.DANA_R,
            serialNumber()
        )
        if (detailedBolusInfo.carbs > 0) pumpSync.syncCarbsWithTimestamp(
            detailedBolusInfo.carbsTimestamp ?: detailedBolusInfo.timestamp,
            detailedBolusInfo.carbs,
            null,
            PumpType.DANA_R,
            serialNumber()
        )
        return result
    }

    // This is called from APS
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        var absoluteRateReq = absoluteRate
        var result = instantiator.providePumpEnactResult()
        absoluteRateReq = constraintChecker.applyBasalConstraints(ConstraintObject(absoluteRateReq, aapsLogger), profile).value()
        var doTempOff = baseBasalRate - absoluteRateReq == 0.0 && absoluteRateReq >= 0.10
        val doLowTemp = absoluteRateReq < baseBasalRate || absoluteRateReq < 0.10
        val doHighTemp = absoluteRateReq > baseBasalRate && !preferences.get(DanaBooleanKey.DanaRUseExtended)
        val doExtendedTemp = absoluteRateReq > baseBasalRate && preferences.get(DanaBooleanKey.DanaRUseExtended)
        var percentRate = (absoluteRateReq / baseBasalRate * 100).toInt()
        // Any basal less than 0.10u/h will be dumped once per hour, not every 4 minutes. So if it's less than .10u/h, set a zero temp.
        if (absoluteRateReq < 0.10) percentRate = 0
        percentRate =
            if (percentRate < 100) ceilTo(percentRate.toDouble(), 10.0).toInt() else floorTo(percentRate.toDouble(), 10.0).toInt()
        if (percentRate > pumpDescription.maxTempPercent) {
            percentRate = pumpDescription.maxTempPercent
        }
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Calculated percent rate: $percentRate")
        if (percentRate == 100) doTempOff = true
        if (doTempOff) {
            // If extended in progress
            if (danaPump.isExtendedInProgress && preferences.get(DanaBooleanKey.DanaRUseExtended)) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doTempOff)")
                return cancelExtendedBolus()
            }
            // If temp in progress
            if (danaPump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)")
                return cancelRealTempBasal()
            }
            result.success(true).enacted(false).percent(100).isPercent(true).isTempCancel(true)
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK")
            return result
        }
        if (doLowTemp || doHighTemp) {
            // If extended in progress
            if (danaPump.isExtendedInProgress && preferences.get(DanaBooleanKey.DanaRUseExtended)) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)")
                result = cancelExtendedBolus()
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)")
                    return result
                }
            }
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress) {
                // Correct basal already set ?
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: currently running: " + danaPump.temporaryBasalToString())
                if (danaPump.tempBasalPercent == percentRate && danaPump.tempBasalRemainingMin > 4) {
                    if (enforceNew) {
                        cancelTempBasal(true)
                    } else {
                        result.success(true).percent(percentRate).enacted(false).duration(danaPump.tempBasalRemainingMin).isPercent(true).isTempCancel(false)
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)")
                        return result
                    }
                }
            }
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal $percentRate% for $durationInMinutes minutes (doLowTemp || doHighTemp)")
            return setTempBasalPercent(percentRate, durationInMinutes, profile, false, tbrType)
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doExtendedTemp)")
                result = cancelRealTempBasal()
                // Check for proper result
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)")
                    return result
                }
            }

            // Calculate # of halfHours from minutes
            val durationInHalfHours = max(durationInMinutes / 30, 1)
            // We keep current basal running so need to sub current basal
            var extendedRateToSet = absoluteRateReq - baseBasalRate
            extendedRateToSet = constraintChecker.applyBasalConstraints(ConstraintObject(extendedRateToSet, aapsLogger), profile).value()
            // needs to be rounded to 0.1
            extendedRateToSet = roundTo(extendedRateToSet, pumpDescription.extendedBolusStep * 2) // *2 because of half hours

            // What is current rate of extended bolusing in u/h?
            aapsLogger.debug(
                LTag.PUMP,
                "setTempBasalAbsolute: Extended bolus in progress: " + danaPump.isExtendedInProgress + " rate: " + danaPump.extendedBolusAbsoluteRate + "U/h duration remaining: " + danaPump.extendedBolusRemainingMinutes + "min"
            )
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h")

            // Compare with extended rate in progress
            if (danaPump.isExtendedInProgress && abs(danaPump.extendedBolusAbsoluteRate - extendedRateToSet) < pumpDescription.extendedBolusStep) {
                // correct extended already set
                result.success(true).absolute(danaPump.extendedBolusAbsoluteRate).enacted(false).duration(danaPump.extendedBolusRemainingMinutes).isPercent(false).isTempCancel(false)
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct extended already set")
                return result
            }

            // Now set new extended, no need to stop previous (if running) because it's replaced
            val extendedAmount = extendedRateToSet / 2 * durationInHalfHours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  half hours: " + durationInHalfHours)
            result = setExtendedBolus(extendedAmount, durationInMinutes)
            if (!result.success) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set extended bolus")
                return result
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Extended bolus set ok")
            result.absolute(result.absolute + baseBasalRate)
            return result
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error")
        result.success(false).comment("Internal error")
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (danaPump.isTempBasalInProgress) return cancelRealTempBasal()
        if (danaPump.isExtendedInProgress && preferences.get(DanaBooleanKey.DanaRUseExtended)) {
            return cancelExtendedBolus()
        }
        val result = instantiator.providePumpEnactResult()
        result.success(true).enacted(false).comment(app.aaps.core.ui.R.string.ok).isTempCancel(true)
        return result
    }

    override fun model(): PumpType {
        return PumpType.DANA_R
    }

    private fun cancelRealTempBasal(): PumpEnactResult {
        val result = instantiator.providePumpEnactResult()
        if (danaPump.isTempBasalInProgress) {
            executionService?.tempBasalStop()
            if (!danaPump.isTempBasalInProgress) {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    pumpDescription.pumpType,
                    serialNumber()
                )
                result.success(true).enacted(true).isTempCancel(true).comment(app.aaps.core.ui.R.string.ok)
            } else result.success(false).enacted(false).isTempCancel(true).comment(app.aaps.core.ui.R.string.canceling_eb_failed)
        } else {
            result.success(true).isTempCancel(true).comment(app.aaps.core.ui.R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK")
        }
        return result
    }

    override fun loadEvents(): PumpEnactResult =
        instantiator.providePumpEnactResult() // no history, not needed

    override fun setUserOptions(): PumpEnactResult =
        executionService?.setUserOptions() ?: throw Exception("No execution service")

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        var entries = emptyArray<CharSequence>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
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
            key = "danar_settings"
            title = rh.gs(app.aaps.pump.dana.R.string.danar_pump_settings)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveListPreference(ctx = context, stringKey = DanaStringKey.DanaRName, title = app.aaps.pump.dana.R.string.danar_bt_name_title, dialogTitle = app.aaps.pump.dana.R.string.danar_bt_name_title, entries = entries, entryValues = entries))
            addPreference(
                AdaptiveIntPreference(
                    ctx = context, intKey = DanaIntKey.DanaRPassword, title = app.aaps.pump.dana.R.string.danar_password_title,
                    validatorParams = DefaultEditTextValidator.Parameters(
                        testType = EditTextValidator.TEST_REGEXP,
                        customRegexp = rh.gs(app.aaps.core.validators.R.string.fourdigitnumber),
                        testErrorString = rh.gs(app.aaps.core.validators.R.string.error_mustbe4digitnumber)
                    )
                )
            )
            addPreference(AdaptiveListIntPreference(ctx = context, intKey = DanaIntKey.DanaBolusSpeed, title = app.aaps.pump.dana.R.string.bolusspeed, dialogTitle = app.aaps.pump.dana.R.string.bolusspeed, entries = speedEntries, entryValues = speedValues))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = DanaBooleanKey.DanaRUseExtended, title = app.aaps.pump.dana.R.string.danar_useextended_title))
        }
    }
}
