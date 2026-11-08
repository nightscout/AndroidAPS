package app.aaps.pump.equil.data

/** The running bolus, shared by the bolus command and the stop, which runs on another thread. */
class BolusProfile {

    var timestamp: Long = 0

    /** The pump answered a stop. */
    @Volatile var stop: Boolean = false

    /** When the pump answered the stop, 0 before. Set before [stop], so whoever sees [stop] sees this too. */
    @Volatile var stoppedAt: Long = 0

    /** A stop went out and got no answer: the pump may have stopped, so only its log tells what it gave. */
    @Volatile var stopNotAnswered: Boolean = false
    var insulin: Double = 0.0
}
