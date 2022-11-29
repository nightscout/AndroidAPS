package info.nightscout.androidaps.danaRKorean

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.danaRKorean.services.DanaRKoreanExecutionService
import info.nightscout.androidaps.danar.AbstractDanaRPlugin
import info.nightscout.androidaps.danar.R
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.PumpSync.TemporaryBasalType
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.Round
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventOverviewBolusProgress
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
@OpenForTesting
class DanaRKoreanPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    aapsSchedulers: AapsSchedulers,
    rxBus: RxBus,
    private val context: Context,
    rh: ResourceHelper,
    constraintChecker: Constraints,
    activePlugin: ActivePlugin,
    sp: SP,
    commandQueue: CommandQueue,
    danaPump: DanaPump,
    dateUtil: DateUtil,
    private val fabricPrivacy: FabricPrivacy,
    pumpSync: PumpSync,
    uiInteraction: UiInteraction,
    danaHistoryDatabase: DanaHistoryDatabase
) : AbstractDanaRPlugin(injector, danaPump, rh, constraintChecker, aapsLogger, aapsSchedulers, commandQueue, rxBus, activePlugin, sp, dateUtil, pumpSync, uiInteraction, danaHistoryDatabase) {

    init {
        pluginDescription.description(R.string.description_pump_dana_r_korean)
        useExtendedBoluses = sp.getBoolean(R.string.key_danar_useextended, false)
        pumpDescription.fillFor(PumpType.DANA_R_KOREAN)
    }

    override fun onStart() {
        context.bindService(Intent(context, DanaRKoreanExecutionService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                if (isEnabled()) {
                    val previousValue = useExtendedBoluses
                    useExtendedBoluses = sp.getBoolean(R.string.key_danar_useextended, false)
                    if (useExtendedBoluses != previousValue && pumpSync.expectedPumpState().extendedBolus != null) {
                        sExecutionService.extendedBolusStop()
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

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            sExecutionService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as DanaRKoreanExecutionService.LocalBinder
            sExecutionService = mLocalBinder.serviceInstance
        }
    }

    // Plugin base interface
    override val name: String
        get() = rh.gs(R.string.danarkoreanpump)
    override val preferencesId: Int
        get() = R.xml.pref_danarkorean

    // Pump interface
    override val isFakingTempsByExtendedBoluses: Boolean
        get() = useExtendedBoluses

    override fun isInitialized(): Boolean =
        danaPump.lastConnection > 0 && danaPump.maxBasal > 0 && !danaPump.isConfigUD && !danaPump.isEasyModeEnabled && danaPump.isExtendedBolusEnabled && danaPump.isPasswordOK

    override fun isHandshakeInProgress(): Boolean =
        sExecutionService != null && sExecutionService.isHandshakeInProgress

    override fun finishHandshaking() {
        sExecutionService.finishHandshaking()
    }

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(Constraint(detailedBolusInfo.insulin)).value()
        if (detailedBolusInfo.carbs > 0) throw IllegalArgumentException()
        return if (detailedBolusInfo.insulin > 0) {
            val t = EventOverviewBolusProgress.Treatment(0.0, 0, detailedBolusInfo.bolusType == DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.id)
            var connectionOK = false
            if (detailedBolusInfo.insulin > 0)
                connectionOK = sExecutionService.bolus(
                    detailedBolusInfo.insulin, detailedBolusInfo.carbs.toInt(), detailedBolusInfo.carbsTimestamp
                        ?: detailedBolusInfo.timestamp, t
                )
            val result = PumpEnactResult(injector)
            result.success(connectionOK && abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.bolusStep)
                .bolusDelivered(t.insulin)
            if (!result.success) result.comment(rh.gs(R.string.boluserrorcode, detailedBolusInfo.insulin, t.insulin, danaPump.bolusStartErrorCode)) else result.comment(R.string.ok)
            aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered)
            detailedBolusInfo.insulin = t.insulin
            detailedBolusInfo.timestamp = dateUtil.now()
            if (detailedBolusInfo.insulin > 0) pumpSync.syncBolusWithPumpId(
                detailedBolusInfo.timestamp,
                detailedBolusInfo.insulin,
                detailedBolusInfo.bolusType,
                dateUtil.now(),
                PumpType.DANA_R_KOREAN,
                serialNumber()
            )
            result
        } else {
            val result = PumpEnactResult(injector)
            result.success(false).bolusDelivered(0.0).carbsDelivered(0.0).comment(R.string.invalid_input)
            aapsLogger.error("deliverTreatment: Invalid input")
            result
        }
    }

    // This is called from APS
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        val absoluteRateAfterConstraint = constraintChecker.applyBasalConstraints(Constraint(absoluteRate), profile).value()
        var doTempOff = baseBasalRate - absoluteRateAfterConstraint == 0.0 && absoluteRateAfterConstraint >= 0.10
        val doLowTemp = absoluteRateAfterConstraint < baseBasalRate || absoluteRateAfterConstraint < 0.10
        val doHighTemp = absoluteRateAfterConstraint > baseBasalRate && !useExtendedBoluses
        val doExtendedTemp = absoluteRateAfterConstraint > baseBasalRate && useExtendedBoluses

        var percentRate: Int = java.lang.Double.valueOf(absoluteRateAfterConstraint / baseBasalRate * 100).toInt()
        // Any basal less than 0.10u/h will be dumped once per hour, not every 4 minutes. So if it's less than .10u/h, set a zero temp.
        if (absoluteRateAfterConstraint < 0.10) percentRate = 0
        percentRate = if (percentRate < 100) Round.ceilTo(percentRate.toDouble(), 10.0).toInt() else Round.floorTo(percentRate.toDouble(), 10.0).toInt()
        if (percentRate > pumpDescription.maxTempPercent) {
            percentRate = pumpDescription.maxTempPercent
        }
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Calculated percent rate: $percentRate")

        if (percentRate == 100) doTempOff = true

        if (doTempOff) {
            // If extended in progress
            if (danaPump.isExtendedInProgress && useExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doTempOff)")
                return cancelExtendedBolus()
            }
            // If temp in progress
            if (danaPump.isTempBasalInProgress) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)")
                return cancelRealTempBasal()
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK")
            return PumpEnactResult(injector).success(true).enacted(false).percent(100).isPercent(true).isTempCancel(true)
        }
        if (doLowTemp || doHighTemp) {
            // If extended in progress
            if (danaPump.isExtendedInProgress && useExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)")
                val result = cancelExtendedBolus()
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
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)")
                        return PumpEnactResult(injector).success(true).percent(percentRate).enacted(false).duration(danaPump.tempBasalRemainingMin).isPercent(true).isTempCancel(false)
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
                val result = cancelRealTempBasal()
                // Check for proper result
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)")
                    return result
                }
            }

            // Calculate # of halfHours from minutes
            val durationInHalfHours = max(durationInMinutes / 30, 1)
            // We keep current basal running so need to sub current basal
            var extendedRateToSet: Double = absoluteRateAfterConstraint - baseBasalRate
            extendedRateToSet = constraintChecker.applyBasalConstraints(Constraint(extendedRateToSet), profile).value()
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, pumpDescription.extendedBolusStep * 2) // *2 because of half hours

            // What is current rate of extended bolusing in u/h?
            aapsLogger.debug(
                LTag.PUMP,
                "setTempBasalAbsolute: Extended bolus in progress: " + danaPump.isExtendedInProgress + " rate: " + danaPump.extendedBolusAbsoluteRate + "U/h duration remaining: " + danaPump.extendedBolusRemainingMinutes + "min"
            )
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h")

            // Compare with extended rate in progress
            if (danaPump.isExtendedInProgress && abs(danaPump.extendedBolusAbsoluteRate - extendedRateToSet) < pumpDescription.extendedBolusStep) {
                // correct extended already set
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct extended already set")
                return PumpEnactResult(injector).success(true).absolute(danaPump.extendedBolusAbsoluteRate).enacted(false).duration(danaPump.extendedBolusRemainingMinutes).isPercent(false)
                    .isTempCancel(false)
            }

            // Now set new extended, no need to to stop previous (if running) because it's replaced
            val extendedAmount = extendedRateToSet / 2 * durationInHalfHours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  half hours: " + durationInHalfHours)
            val result = setExtendedBolus(extendedAmount, durationInMinutes)
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
        return PumpEnactResult(injector).success(false).comment("Internal error")
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        if (danaPump.isTempBasalInProgress) return cancelRealTempBasal()
        if (danaPump.isExtendedInProgress && useExtendedBoluses) {
            return cancelExtendedBolus()
        }
        val result = PumpEnactResult(injector)
        result.success(true).enacted(false).comment(R.string.ok).isTempCancel(true)
        return result
    }

    override fun model(): PumpType = PumpType.DANA_R_KOREAN

    private fun cancelRealTempBasal(): PumpEnactResult {
        val result = PumpEnactResult(injector)
        if (danaPump.isTempBasalInProgress) {
            sExecutionService.tempBasalStop()
            if (!danaPump.isTempBasalInProgress) {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    pumpDescription.pumpType,
                    serialNumber()
                )
                result.success(true).enacted(true).isTempCancel(true)
            } else result.success(false).enacted(false).isTempCancel(true)
        } else {
            result.success(true).isTempCancel(true).comment(R.string.ok)
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK")
        }
        return result
    }

    override fun loadEvents(): PumpEnactResult = PumpEnactResult(injector) // no history, not needed
    override fun setUserOptions(): PumpEnactResult = PumpEnactResult(injector)
}