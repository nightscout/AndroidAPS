package app.aaps.core.interfaces.queue

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Holds back new SMBs while the user confirms a bolus (#5381).
 *
 * A bolus is given in two steps: the master calculates the dose and shows it, then the user confirms it. An SMB given
 * between the two steps is not in the calculated dose, so the user would get both. And an SMB that is being delivered
 * when the bolus reaches the queue makes the queue refuse the bolus (#4546), after the dialog has closed, and what the
 * user entered is lost. So the first step starts a pause: no new SMB until the bolus is confirmed or dropped, or until
 * [MAX_DURATION] has passed.
 *
 * Kept in memory only, and every pause ends by itself. A pause that never ended would stop the SMBs of the loop
 * without anyone noticing.
 */
interface SmbPause {

    /** Starts a pause and returns its id. */
    fun begin(): Long

    /** Ends the pause [id] now: its bolus was dropped, or the pump does not give it. */
    fun end(id: Long)

    /**
     * The bolus of the pause [id] was confirmed and is being put into the queue. From there a queued bolus stops new
     * SMBs by itself, so the pause stays only [HAND_OVER] longer, to cover the moment before the bolus is in the queue.
     */
    fun handOver(id: Long)

    /** True while the pause [id] has not ended or run out. */
    fun isActive(id: Long): Boolean

    /** True while any pause has not ended or run out: the loop gives no SMB then. */
    fun isActive(): Boolean

    companion object {

        /** The longest one pause lasts. A confirmation closes by itself when this time has passed. */
        val MAX_DURATION: Duration = 2.minutes

        /** See [handOver]. Putting the bolus into the queue takes milliseconds; this is a wide margin. */
        val HAND_OVER: Duration = 10.seconds
    }
}
