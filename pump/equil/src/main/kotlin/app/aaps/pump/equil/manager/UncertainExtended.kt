package app.aaps.pump.equil.manager

import app.aaps.pump.equil.manager.UncertainBolus.Companion.ADVERTISEMENT_FRESH_MS
import app.aaps.pump.equil.manager.UncertainBolus.Companion.PORT_STATUS
import app.aaps.pump.equil.manager.UncertainBolus.Companion.TYPE_STATUS
import app.aaps.pump.equil.manager.UncertainBolus.Companion.isAfter
import kotlin.math.abs

/**
 * An extended bolus command that went out and got no answer. Until the pump log tells, AAPS keeps what counts more
 * insulin: a start is recorded as given, and a cancel leaves the extended bolus running. Found by the pump scenario
 * tests: without an answer AAPS did nothing, so the pump ran an extended bolus AAPS did not know of, or AAPS kept one
 * the pump had ended. Equil has no read of a running extended bolus, only its log.
 *
 * Seen on a real pump (2026-11-10): the start of an extended bolus logs a status entry (4,0,0) with its speed as
 * largeRate, 160 for 0.5 U in 30 min, and its cancel a status entry with largeRate 0.
 *
 * In memory only: after an app restart the record stays as it is.
 */
internal class UncertainExtended(
    /** True for a start, false for a cancel. */
    val start: Boolean,
    /** The speed of a start as the log shows it. */
    val largeRate: Int,
    /** The newest log index the pump advertised before the command. The entry of the command comes after it. */
    val indexBefore: Int,
    /** Phone time the command went out, also the time of the AAPS record of a start. */
    val sentAt: Long
) {

    /** Pump time of the log entry that shows the command was done, or null while none was read. */
    @Volatile var doneAt: Long? = null
        private set

    /** "Not done" needs an advertisement from this time on, as for [UncertainBolus.notGivenAfter]. */
    val notDoneAfter: Long get() = sentAt + ADVERTISEMENT_FRESH_MS

    /** Called for each log entry the driver reads, in the order of the log. */
    fun onLogEntry(index: Int, port: Int, type: Int, largeRate: Int, time: Long) {
        if (doneAt != null || !isAfter(index, indexBefore)) return
        if (port != PORT_STATUS || type != TYPE_STATUS) return
        // The pump may round the speed its own way.
        if (if (start) abs(largeRate - this.largeRate) <= 1 else largeRate == 0) doneAt = time
    }
}
