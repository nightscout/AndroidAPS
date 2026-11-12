package app.aaps.plugins.source

import kotlin.math.abs

/**
 * Whether the start a G7 / ONE+ sensor reports should become the start AAPS uses.
 *
 * AAPS gave a sensor that was already running the time it connected to it, so its age and its end
 * alarms were wrong. The sensor knows better: it is taken once per sensor, and never over a start
 * the user corrected by hand.
 */
object DexcomOnePlusSensorReportedStart {

    /** A difference this small is the same start: delays of the readings, not a wrong age. */
    const val SAME_START_MS = 15L * 60L * 1000L

    enum class Decision {
        /** Use the start of the sensor. */
        MOVE,

        /** The start stored is already right: keep it, and stop checking for this sensor. */
        SAME,

        /** Change nothing: already settled, or a start that cannot be right. */
        IGNORE,
    }

    /**
     * @param settled the start of this sensor already came from the sensor or from the user
     * @param currentStartMs the start stored now, 0 when none
     * @param reportedStartMs the start the sensor reports
     * @param maxAgeMs oldest start still possible for a running sensor (life plus grace)
     */
    fun decide(settled: Boolean, currentStartMs: Long, reportedStartMs: Long, nowMs: Long, maxAgeMs: Long): Decision = when {
        settled                                                                    -> Decision.IGNORE
        reportedStartMs > nowMs                                                    -> Decision.IGNORE
        nowMs - reportedStartMs > maxAgeMs                                         -> Decision.IGNORE
        currentStartMs > 0L && abs(currentStartMs - reportedStartMs) <= SAME_START_MS -> Decision.SAME
        else                                                                       -> Decision.MOVE
    }
}
