package app.aaps.pump.equil.manager

import app.aaps.core.data.model.BS
import app.aaps.pump.equil.manager.command.PumpEvent
import kotlin.math.abs

/**
 * A bolus whose amount the driver could not see: its start or a stop got no answer. AAPS counts the whole amount until
 * the pump log, read on a connection made after the bolus was over, shows what the pump gave.
 *
 * Seen on a real pump (2026-11-08): a bolus logs a status entry (4,0,0) with its speed as largeRate when it starts, and
 * one with largeRate 0 when it ends or is stopped, in whole seconds of the pump clock. An empty reservoir, an empty
 * battery and "abnormal infusion stopped" stop all delivery, so they end it too, without that entry.
 *
 * A blockage does not end it: how a real pump goes on after a blockage during a bolus was never seen, and it cannot be
 * made on a real pump. If the bolus went on, its end entry comes later, and taking the blockage as the end would count
 * less than was given. With a blockage and no end entry, the whole amount stays.
 *
 * In memory only: after an app restart the whole amount stays, which never counts less than was given.
 */
internal class UncertainBolus(
    val requested: Double,
    /** The speed of the command. Not the largeRate of the log, which has 16 bits: 12 U/min does not fit in them. */
    val unitsPerSecond: Double,
    /** The newest log index the pump advertised before the command. The entries of this bolus come after it. */
    val indexBefore: Int,
    /** Phone time the command went out. */
    val sentAt: Long,
    /** A read on a connection made after this time has the whole bolus in the log. */
    val decideAfter: Long,
    val bolusType: BS.Type,
    /** The pump id of the AAPS record, to correct it. */
    val pumpId: Long,
    /** The time of the AAPS record. */
    val recordTime: Long
) {

    /**
     * "Not given" needs an advertisement from this time on. The advertisement brings the newest log index, and only a
     * read up to it can show that the start is not there. Seen on a real pump (2026-11-08): an entry was in the
     * advertisement 20 s after it was written. UNVERIFIED: how much sooner. A wrong "not given" counts less than was given.
     */
    val notGivenAfter: Long get() = sentAt + ADVERTISEMENT_FRESH_MS

    /** The time from which a read can decide every case. */
    val readAfter: Long get() = maxOf(decideAfter, notGivenAfter)

    /** A bolus as the log shows it, in pump time. [end] is null until its end entry is read. */
    class Logged(val start: Long, var end: Long? = null)

    private val logged = mutableListOf<Logged>()

    /** Called for each log entry the driver reads, in the order of the log. */
    @Synchronized
    fun onLogEntry(index: Int, port: Int, type: Int, level: Int, largeRate: Int, time: Long) {
        if (!isAfter(index, indexBefore)) return
        val open = logged.lastOrNull()?.takeIf { it.end == null }
        val status = port == PORT_STATUS && type == TYPE_STATUS
        when {
            // UNVERIFIED: a status entry during a bolus, for a basal change, keeps the bolus speed. It is no new bolus.
            status && largeRate > 0 -> if (open == null) logged += Logged(start = time)
            open == null            -> {}
            status || PumpEvent.stopsDelivery(port, type, level) -> open.end = time
        }
    }

    /**
     * The bolus of the log that is this one: the only one after [indexBefore], or the one that started closest to
     * [sentAt]. Null when the log has no bolus after [indexBefore].
     */
    @Synchronized
    fun inLog(): Logged? = logged.minByOrNull { abs(it.start - sentAt) }

    companion object {

        /** The pump counts its log from 1 to 2000 and then starts at 1 again. */
        private const val LOG_SIZE = 2000

        /** See [notGivenAfter]. */
        private const val ADVERTISEMENT_FRESH_MS = 30_000L

        private const val PORT_STATUS = 4
        private const val TYPE_STATUS = 0

        /** [index] was written after [before], also when the count started again at 1 in between. */
        fun isAfter(index: Int, before: Int): Boolean {
            val distance = ((index - before) % LOG_SIZE + LOG_SIZE) % LOG_SIZE
            return distance in 1 until LOG_SIZE / 2
        }
    }
}
