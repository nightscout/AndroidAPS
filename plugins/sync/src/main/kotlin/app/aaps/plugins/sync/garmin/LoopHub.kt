package app.aaps.plugins.sync.garmin

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.profile.Profile
import app.aaps.database.entities.GlucoseValue
import java.time.Instant

/** Abstraction from all the functionality we need from the AAPS app. */
interface LoopHub {

    /** Returns the active insulin profile. */
    val currentProfile: Profile?

    /** Returns the name of the active insulin profile. */
    val currentProfileName: String

    /** Returns the glucose unit (mg/dl or mmol/l) as selected by the user. */
    val glucoseUnit: GlucoseUnit

    /** Returns the remaining bolus insulin on board. */
    val insulinOnboard: Double

    /** Returns the basal insulin on board. */
    val insulinBasalOnboard: Double

    /** Returns the remaining carbs on board. */
    val carbsOnboard: Double?

    /** Returns true if the pump is connected. */
    val isConnected: Boolean

    /** Returns true if the current profile is set of a limited amount of time. */
    val isTemporaryProfile: Boolean

    /** Returns the factor by which the basal rate is currently raised (> 1) or lowered (< 1). */
    val temporaryBasal: Double

    /** Tells the loop algorithm that the pump is physically connected. */
    fun connectPump()

    /** Tells the loop algorithm that the pump will be physically disconnected
     *  for the given number of minutes. */
    fun disconnectPump(minutes: Int)

    /** Retrieves the glucose values starting at from. */
    fun getGlucoseValues(from: Instant, ascending: Boolean): List<GlucoseValue>

    /** Notifies the system that carbs were eaten and stores the value. */
    fun postCarbs(carbohydrates: Int)

    /** Stores hear rate readings that a taken and averaged of the given interval. */
    fun storeHeartRate(
        samplingStart: Instant, samplingEnd: Instant,
        avgHeartRate: Int,
        device: String?
    )
}
