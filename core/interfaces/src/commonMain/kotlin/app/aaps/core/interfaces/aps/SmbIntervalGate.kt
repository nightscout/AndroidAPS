package app.aaps.core.interfaces.aps

import app.aaps.core.data.time.T

/**
 * The one rule for how soon after a bolus the next SMB may go out. The APS uses it to decide, the
 * loop checks it again before the SMB is queued, and the queue checks it before the pump gets it.
 *
 * The tolerance lets an SMB go out a few seconds before the full interval. Without it, a loop run
 * that comes exactly one interval after the last SMB (1-minute CGM, or an interval equal to the CGM
 * interval) finds the bolus a second too young and the SMB waits for the next run (#3426).
 *
 * All checks must use this same rule. A later check reads the clock later, so with the same rule it
 * can only refuse what the APS allowed when a new bolus came in. With a stricter rule it threw away
 * SMBs the APS had allowed (#5362).
 */
object SmbIntervalGate {

    const val TOLERANCE_MS = 6_000L

    /** True when the last bolus is too recent for another SMB. */
    fun tooSoon(lastBolusTime: Long, intervalMinutes: Int, now: Long): Boolean =
        lastBolusTime + T.mins(intervalMinutes.toLong()).msecs() - TOLERANCE_MS > now
}
