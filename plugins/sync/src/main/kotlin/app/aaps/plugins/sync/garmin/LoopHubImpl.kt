package app.aaps.plugins.sync.garmin

import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateHeartRateTransaction
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
/**
 * Interface to the functionality of the looping algorithm and storage systems.
 */
class LoopHubImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val profileFunction: ProfileFunction,
    private val repo: AppRepository,
    private val userEntryLogger: UserEntryLogger,
    private val sp: SP,
) : LoopHub {

    @VisibleForTesting
    var clock: Clock = Clock.systemUTC()

    /** Returns the active insulin profile. */
    override val currentProfile: Profile? get() = profileFunction.getProfile()

    /** Returns the name of the active insulin profile. */
    override val currentProfileName: String
        get() = profileFunction.getProfileName()

    /** Returns the glucose unit (mg/dl or mmol/l) as selected by the user. */
    override val glucoseUnit: GlucoseUnit
        get() = GlucoseUnit.fromText(sp.getString(
            app.aaps.core.utils.R.string.key_units,
            GlucoseUnit.MGDL.asText))

    /** Returns the remaining bolus insulin on board. */
    override val insulinOnboard: Double
        get() = iobCobCalculator.calculateIobFromBolus().iob

    /** Returns true if the pump is connected. */
    override val isConnected: Boolean get() = !loop.isDisconnected

    /** Returns true if the current profile is set of a limited amount of time. */
    override val isTemporaryProfile: Boolean
        get() {
            val resp = repo.getEffectiveProfileSwitchActiveAt(clock.millis())
            val ps: EffectiveProfileSwitch? =
                (resp.blockingGet() as? ValueWrapper.Existing<EffectiveProfileSwitch>)?.value
            return ps != null && ps.originalDuration > 0
        }

    /** Returns the factor by which the basal rate is currently raised (> 1) or lowered (< 1). */
    override val temporaryBasal: Double
        get() {
            val apsResult = loop.lastRun?.constraintsProcessed
            return if (apsResult == null) Double.NaN else apsResult.percent / 100.0
        }

    /** Tells the loop algorithm that the pump is physicallly connected. */
    override fun connectPump() {
        repo.runTransaction(
            CancelCurrentOfflineEventIfAnyTransaction(clock.millis())
        ).subscribe()
        commandQueue.cancelTempBasal(true, null)
        userEntryLogger.log(UserEntry.Action.RECONNECT, UserEntry.Sources.GarminDevice)
    }

    /** Tells the loop algorithm that the pump will be physically disconnected
     *  for the given number of minutes. */
    override fun disconnectPump(minutes: Int) {
        currentProfile?.let { p ->
            loop.goToZeroTemp(minutes, p, OfflineEvent.Reason.DISCONNECT_PUMP)
            userEntryLogger.log(
                UserEntry.Action.DISCONNECT,
                UserEntry.Sources.GarminDevice,
                ValueWithUnit.Minute(minutes)
            )
        }
    }

    /** Retrieves the glucose values starting at from. */
    override fun getGlucoseValues(from: Instant, ascending: Boolean): List<GlucoseValue> {
        return repo.compatGetBgReadingsDataFromTime(from.toEpochMilli(), ascending)
                   .blockingGet()
    }

    /** Notifies the system that carbs were eaten and stores the value. */
    override fun postCarbs(carbohydrates: Int) {
        aapsLogger.info(LTag.GARMIN, "post $carbohydrates g carbohydrates")
        val carbsAfterConstraints =
            carbohydrates.coerceAtMost(constraintChecker.getMaxCarbsAllowed().value())
        userEntryLogger.log(
            UserEntry.Action.CARBS,
            UserEntry.Sources.GarminDevice,
            ValueWithUnit.Gram(carbsAfterConstraints)
        )
        val detailedBolusInfo = DetailedBolusInfo().apply {
            eventType = DetailedBolusInfo.EventType.CARBS_CORRECTION
            carbs = carbsAfterConstraints.toDouble()
        }
        commandQueue.bolus(detailedBolusInfo, null)
    }

    /** Stores hear rate readings that a taken and averaged of the given interval. */
    override fun storeHeartRate(
        samplingStart: Instant, samplingEnd: Instant,
        avgHeartRate: Int,
        device: String?) {
        val hr = HeartRate(
            timestamp = samplingStart.toEpochMilli(),
            duration = samplingEnd.toEpochMilli() - samplingStart.toEpochMilli(),
            dateCreated = clock.millis(),
            beatsPerMinute = avgHeartRate.toDouble(),
            device = device ?: "Garmin",
        )
        repo.runTransaction(InsertOrUpdateHeartRateTransaction(hr)).blockingAwait()
    }
}