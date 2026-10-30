package app.aaps.plugins.sync.garmin

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToPercent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant

/**
 * Interface to the functionality of the looping algorithm and storage systems.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class LoopHubImpl(
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val masterActionExecutor: MasterActionExecutor,
    private val dateUtil: DateUtil,
    @ApplicationScope private val appScope: CoroutineScope
) : LoopHub {

    val disposable = CompositeDisposable()

    @VisibleForTesting
    var clock: Clock = Clock.systemUTC()

    /** Returns the active insulin profile. */
    override val currentProfile: Profile? get() = runBlocking { profileFunction.getProfile() }

    /** Returns the name of the active insulin profile. */
    override val currentProfileName: String
        get() = runBlocking { profileFunction.getProfileName() }

    /** Returns the glucose unit (mg/dl or mmol/l) as selected by the user. */
    override val glucoseUnit: GlucoseUnit
        get() = GlucoseUnit.fromText(preferences.get(StringKey.GeneralUnits))

    /** Returns the remaining bolus insulin on board. */
    override val insulinOnboard: Double
        get() = runBlocking { iobCobCalculator.calculateIobFromBolus() }.iob

    /** Returns the remaining bolus and basal insulin on board. */
    override val insulinBasalOnboard: Double
        get() = runBlocking { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended() }.basaliob

    /** Returns the remaining carbs on board. */
    override val carbsOnboard: Double?
        get() = runBlocking { iobCobCalculator.getCobInfo("LoopHubImpl") }.displayCob

    /** Returns true if the pump is connected. */
    override val isConnected: Boolean get() = runBlocking { loop.runningMode() } != RM.Mode.DISCONNECTED_PUMP

    /** Returns true if the current profile is set of a limited amount of time. */
    override val isTemporaryProfile: Boolean
        get() {
            val ps = runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(clock.millis()) }
            return ps != null && ps.originalDuration > 0
        }

    /** Returns the factor by which the basal rate is currently raised (> 1) or lowered (< 1). */
    override val temporaryBasal: Double
        get() {
            return currentProfile?.let {
                val tb = runBlocking { processedTbrEbData.getTempBasalIncludingConvertedExtended(clock.millis()) }
                tb?.convertedToPercent(clock.millis(), it)?.div(100.0)
            } ?: Double.NaN
        }

    override val lowGlucoseMark
        get() = profileUtil.convertToMgdl(
            preferences.get(UnitDoubleKey.OverviewLowMark), glucoseUnit
        )

    override val highGlucoseMark
        get() = profileUtil.convertToMgdl(
            preferences.get(UnitDoubleKey.OverviewHighMark), glucoseUnit
        )

    /** Tells the loop algorithm that the pump is physically connected. */
    override fun connectPump() {
        appScope.launch {
            persistenceLayer.cancelCurrentRunningMode(clock.millis(), Action.RECONNECT, Sources.Garmin)
            commandQueue.cancelTempBasal(enforceNew = true)
        }
    }

    /** Tells the loop algorithm that the pump will be physically disconnected
     *  for the given number of minutes. */
    override fun disconnectPump(minutes: Int) {
        currentProfile?.let { p ->
            appScope.launch {
                loop.handleRunningModeChange(
                    durationInMinutes = minutes,
                    profile = p,
                    newRM = RM.Mode.DISCONNECTED_PUMP,
                    action = Action.DISCONNECT,
                    source = Sources.Garmin,
                    listValues = listOf(ValueWithUnit.Minute(minutes))
                )
            }
        }
    }

    /** Retrieves the glucose values starting at from. */
    override fun getGlucoseValues(from: Instant, ascending: Boolean): List<GV> = runBlocking {
        persistenceLayer.getBgReadingsDataFromTime(from.toEpochMilli(), ascending)
    }

    /** Notifies the system that carbs were eaten and stores the value. */
    override fun postCarbs(carbohydrates: Int) {
        aapsLogger.info(LTag.GARMIN, "post $carbohydrates g carbohydrates")
        val carbsAfterConstraints =
            carbohydrates.coerceAtMost(constraintChecker.getMaxCarbsAllowed().value())
        // Instant carbs now ride the shared executor (one audited path); it logs the user entry + delivers.
        appScope.launch {
            masterActionExecutor.deliverCarbs(
                carbs = carbsAfterConstraints,
                note = null,
                source = Sources.Garmin,
                onError = { aapsLogger.error(LTag.GARMIN, "carbs delivery failed: ${it.comment}") }
            )
        }
    }

    /**
     * Delivers a correction bolus with the shared executor (one audited path). The executor checks the
     * running mode, writes the user entry and sends the bolus to the command queue, which applies the
     * bolus constraints. A failure is only logged. It is not reported back to the Garmin device.
     */
    override fun postBolus(bolus: Double) {
        aapsLogger.info(LTag.GARMIN, "trigger a bolus of $bolus U")
        appScope.launch {
            masterActionExecutor.deliverInsulin(
                insulin = bolus,
                note = null,
                source = Sources.Garmin,
                onError = { aapsLogger.error(LTag.GARMIN, "bolus delivery failed: ${it.comment}") }
            )
        }
    }

    /**
     * Sets a temporary target with the reason `WEAR`, or cancels the current one.
     * [target] is converted from the user's glucose unit to mg/dl.
     */
    override fun postTempTarget(target: Double, duration: Int) {
        // A target or a duration of 0 means: cancel the current temporary target
        if (target == 0.0 || duration == 0) {
            appScope.launch {
                persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                    timestamp = dateUtil.now(),
                    action = Action.TT,
                    source = Sources.TTDialog,
                    note = null,
                    listValues = listOf()
                )
            }
        } else {
            appScope.launch {
                persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                    temporaryTarget = TT(
                        timestamp = dateUtil.now(),
                        duration = T.mins(duration.toLong()).msecs(),
                        reason = TT.Reason.WEAR,
                        lowTarget = profileUtil.convertToMgdl(target, profileUtil.units),
                        highTarget = profileUtil.convertToMgdl(target, profileUtil.units)
                    ),
                    action = Action.TT,
                    source = Sources.Garmin,
                    note = null,
                    listValues = listOf(
                        ValueWithUnit.TETTReason(TT.Reason.AUTOMATION),
                        ValueWithUnit.Mgdl(target),
                        ValueWithUnit.Minute(duration)
                    ).filterNotNull()
                )
            }
        }
    }

    /** Stores hear rate readings that a taken and averaged of the given interval. */
    override fun storeHeartRate(
        samplingStart: Instant, samplingEnd: Instant,
        avgHeartRate: Int,
        device: String?
    ) {
        val hr = HR(
            timestamp = samplingStart.toEpochMilli(),
            duration = samplingEnd.toEpochMilli() - samplingStart.toEpochMilli(),
            dateCreated = clock.millis(),
            beatsPerMinute = avgHeartRate.toDouble(),
            device = device ?: "Garmin",
        )
        appScope.launch {
            persistenceLayer.insertOrUpdateHeartRates(listOf(hr))
        }
    }

    /**
     * Stores one steps count record. The record time is [samplingEnd]. The `stepsNmin` values are
     * the number of steps in the last N minutes. The write runs in the background and errors are only logged.
     */
    override fun storeStepsCount(
        samplingStart: Instant,
        samplingEnd: Instant,
        steps5min: Int,
        steps10min: Int,
        steps15min: Int,
        steps30min: Int,
        steps60min: Int,
        steps180min: Int,
        device: String?,
    ) {
        val sc = SC(
            duration = samplingEnd.toEpochMilli() - samplingStart.toEpochMilli(),
            timestamp = samplingEnd.toEpochMilli(),
            steps5min = steps5min,
            steps10min = steps10min,
            steps15min = steps15min,
            steps30min = steps30min,
            steps60min = steps60min,
            steps180min = steps180min,
            device = device ?: "Garmin",
            dateCreated = clock.millis(),
        )
        appScope.launch {
            try {
                val result: PersistenceLayer.TransactionResult<SC> = persistenceLayer.insertOrUpdateStepsCount(sc)
                val id = result.inserted.firstOrNull()?.id ?: result.updated.firstOrNull()?.id
                aapsLogger.info(
                    LTag.GARMIN,
                    "✅ Steps stored in DB: ID=$id, 5min=$steps5min, timestamp=${java.util.Date(samplingEnd.toEpochMilli())}"
                )
            } catch (error: Exception) {
                aapsLogger.error(
                    LTag.GARMIN,
                    "❌ Failed to store steps: ${error.message}"
                )
            }
        }
    }
}
