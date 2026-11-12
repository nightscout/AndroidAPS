package app.aaps.pump.omnipod.common.util

import java.util.TimeZone

/** The time zone used to build a basal command, not the time zone at a later confirmation. */
data class PodTimeZone(val id: String, val offset: Int, val updatedAt: Long) {

    companion object {

        fun now(timestamp: Long = System.currentTimeMillis()): PodTimeZone {
            val zone = TimeZone.getDefault()
            return PodTimeZone(zone.id, zone.getOffset(timestamp), timestamp)
        }
    }
}

/**
 * Shared automatic DST/time-zone policy for Dash and O5.
 *
 * The drivers check stored offsets on startup and every minute, so a missed Android time event
 * still gets handled. They block loop invocation until the basal program is confirmed. A bolus or
 * a user-suspended pod is left alone. An automatic suspend can be recovered after restart.
 *
 * Any active temp basal is lost when the pod is suspended to reprogram its clock, the same way the
 * Medtrum driver accepts a temp basal may be cancelled by its own time update: the loop sets a new
 * one next cycle rather than this driver reconstructing the old one.
 */
class OmnipodTimeChange {

    private var inProgress = false
    private var retryAt = Long.MIN_VALUE
    private var failureAlerted = false

    @Synchronized
    fun isInProgress(): Boolean = inProgress

    @Synchronized
    fun tryStart(now: Long): Boolean {
        if (inProgress || now < retryAt) return false
        inProgress = true
        return true
    }

    @Synchronized
    fun finish(now: Long) {
        inProgress = false
        retryAt = now + 60000
    }

    /** True only for the first of a run of failures, so the retry every minute does not keep sounding an alarm. */
    @Synchronized
    fun shouldSoundOnFailure(): Boolean {
        if (failureAlerted) return false
        failureAlerted = true
        return true
    }

    /** Called once the correction succeeds, so the next unrelated failure sounds again. */
    @Synchronized
    fun resetFailureAlert() {
        failureAlerted = false
    }

    companion object {

        fun needsCorrection(podOffset: Int?, phoneOffset: Int, recovering: Boolean): Boolean =
            recovering || (podOffset != null && podOffset != phoneOffset)

        fun canProgram(running: Boolean, suspended: Boolean, bolusActive: Boolean, unconfirmed: Boolean, recovering: Boolean): Boolean =
            running && !bolusActive && !unconfirmed && (!suspended || recovering)
    }
}
