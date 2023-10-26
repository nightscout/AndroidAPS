package app.aaps.plugins.sync.garmin

import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface to the functionality of the looping algorithm and storage systems.
 */
@Singleton
class LoopHubImpl @Inject constructor(
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val profileFunction: ProfileFunction,
    private val persistenceLayer: PersistenceLayer,
) : LoopHub {

    val disposable = CompositeDisposable()

    @VisibleForTesting
    var clock: Clock = Clock.systemUTC()

    /** Returns the active insulin profile. */
    override val currentProfile: Profile? get() = profileFunction.getProfile()

    /** Returns the name of the active insulin profile. */
    override val currentProfileName: String
        get() = profileFunction.getProfileName()

    /** Returns the glucose unit (mg/dl or mmol/l) as selected by the user. */
    override val glucoseUnit: GlucoseUnit
        get() = profileFunction.getProfile()?.units ?: GlucoseUnit.MGDL

    /** Returns the remaining bolus insulin on board. */
    override val insulinOnboard: Double
        get() = iobCobCalculator.calculateIobFromBolus().iob

    /** Returns true if the pump is connected. */
    override val isConnected: Boolean get() = !loop.isDisconnected

    /** Returns true if the current profile is set of a limited amount of time. */
    override val isTemporaryProfile: Boolean
        get() {
            val ps = persistenceLayer.getEffectiveProfileSwitchActiveAt(clock.millis())
            return ps != null && ps.originalDuration > 0
        }

    /** Returns the factor by which the basal rate is currently raised (> 1) or lowered (< 1). */
    override val temporaryBasal: Double
        get() {
            val apsResult = loop.lastRun?.constraintsProcessed
            return if (apsResult == null) Double.NaN else apsResult.percent / 100.0
        }

    /** Retrieves the glucose values starting at from. */
    override fun getGlucoseValues(from: Instant, ascending: Boolean): List<GV> {
        return persistenceLayer.getBgReadingsDataFromTime(from.toEpochMilli(), ascending)
            .blockingGet()
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
        disposable += persistenceLayer.insertOrUpdateHeartRate(hr).subscribe()
    }
}