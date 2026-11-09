package app.aaps.pump.equil.data

/** The running bolus, shared by the bolus command and the stop, which runs on another thread. */
class BolusProfile {

    var timestamp: Long = 0

    /** The pump answered a stop. */
    @Volatile var stop: Boolean = false

    /** When the pump answered the stop, 0 before. Set before [stop], so whoever sees [stop] sees this too. */
    @Volatile var stoppedAt: Long = 0

    /**
     * A stop was sent. Set before it goes out: its answer may take longer than the rest of the bolus, so at the bolus
     * end a stop without an answer yet is known, and the pump log tells what was given.
     */
    @Volatile var stopRequested: Boolean = false
    var insulin: Double = 0.0
}
