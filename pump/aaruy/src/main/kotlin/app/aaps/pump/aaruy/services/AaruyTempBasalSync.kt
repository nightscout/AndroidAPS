package app.aaps.pump.aaruy.services

import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.comm.LogItem
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.abs
import kotlin.math.min

/** What one status frame says. A rate and the times only when a temp basal runs. */
data class AaruyStatusSnapshot(val status: Int, val rate: Double = 0.0, val remainingSeconds: Long = 0, val runningSeconds: Long = 0)

/**
 * Fills the database with what the pump really did, not with what was asked. Commands write nothing here, so a lost answer is no mistake.
 *
 * Seen on a real pump:
 * - A temp basal is in the log only when it ended, so a running one is known from the status only.
 * - A bolus attempt, also a refused one, ends a log entry while the temp basal goes on. One temp basal can be many entries.
 * - The status has no time for an end during a lost link. Only the log has it, and the database takes one end only.
 * - The first status frame of a read can have old counters with a new time, so the start can be late by the time since the last read.
 *
 * So: the status writes the start, and the end on a live link. The log writes the end after a lost link and adds what the database does not have.
 * All calls must come one after the other, see `AaruyBLE`.
 */
@SingleIn(AppScope::class)
@Inject
class AaruyTempBasalSync(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val aaruyPump: AaruyPump,
    private val pumpSync: PumpSync,
    private val persistenceLayer: PersistenceLayer,
    private val ch: ConcentrationHelper,
) {

    /**
     * @param live false after a reconnect until the log is read: an end seen now can be long ago.
     * @param stoppedSince after a whole read, where the log ends: a pump that is stopped now has given nothing since.
     */
    suspend fun onStatus(snapshot: AaruyStatusSnapshot, live: Boolean, stoppedSince: Long? = null) {
        val running = pumpSync.expectedPumpState().temporaryBasal
        when (snapshot.status) {
            STATUS_TEMP_BASAL -> onTempBasalRunning(snapshot, running)
            // The pump goes back to the temp basal after a bolus, so this says nothing about it.
            STATUS_BOLUS      -> Unit

            STATUS_BASAL      -> if (live && running != null) aaruyPump.syncStopTBRWithPumpId(dateUtil.now())

            else              -> if (live && running?.type != PumpSync.TemporaryBasalType.PUMP_SUSPEND) {
                // A stop during a lost link began long before this read. The gap fill finds no gap after the last entry.
                val from = stoppedSince?.takeIf { it in dateUtil.now() - SUSPEND_LENGTH_MS..dateUtil.now() } ?: dateUtil.now()
                aapsLogger.debug(LTag.PUMPCOMM, "The pump delivers nothing, status ${snapshot.status}, since ${dateUtil.dateAndTimeString(from)}")
                aaruyPump.syncSuspendWithPumpId(from, SUSPEND_LENGTH_MS)
            }
        }
        refreshDriverState()
    }

    private suspend fun onTempBasalRunning(snapshot: AaruyStatusSnapshot, running: PumpSync.PumpState.TemporaryBasal?) {
        val start = dateUtil.now() - T.secs(snapshot.runningSeconds).msecs()
        // The database holds insulin units, the pump its own. They differ when the insulin is not U100.
        val rateInDatabaseUnits = PumpRate(snapshot.rate).iU(ch.concentration, true)
        when {
            running == null || running.type == PumpSync.TemporaryBasalType.PUMP_SUSPEND || abs(running.rate - rateInDatabaseUnits) >= SAME_RATE ->
                addTempBasal(start, snapshot.rate, T.secs(snapshot.remainingSeconds + snapshot.runningSeconds).msecs())

            // A later frame corrected the old counters. A split only makes the start later, so this is never a split.
            start < running.timestamp - LATE_START_MS && running.pumpId != null ->
                aaruyPump.correctTBRStart(running.pumpId!!, start, snapshot.rate, running.duration + (running.timestamp - start), running.type)
        }
    }

    /**
     * @param nextStart when the next entry of the log starts: the exact end. Without it the end is known only to the minute,
     * because an entry counts whole minutes: a temp basal of 30 seconds says 0. The middle of that minute is never more than 30 seconds off.
     */
    suspend fun onHistoryTempBasal(item: LogItem, nextStart: Long?, statusNow: Int?) {
        val start = item.timestamp
        // The next entry can be an alarm long after this one, so it is the end only within the minutes of this entry.
        val byMinutes = start + T.mins(item.injectTime + 1L).msecs()
        val end = nextStart?.takeIf { it in start..byMinutes } ?: min(dateUtil.now(), start + T.mins(item.injectTime.toLong()).msecs() + HALF_MINUTE_MS)
        val rate = item.data / 1000.0
        val record = recordAround(start) ?: lateStart(start, end, rate)
        when {
            record == null                                                                      -> addTempBasal(start, rate, end - start)
            // Still running in the database, and the pump ended it while the link was down. With status 2 or 3 this entry is only a split.
            dateUtil.now() < record.timestamp + record.duration && statusNow != STATUS_TEMP_BASAL && statusNow != STATUS_BOLUS -> {
                aapsLogger.debug(LTag.PUMPCOMM, "The pump ended the temp basal at ${dateUtil.dateAndTimeString(end)}")
                aaruyPump.syncStopTBRWithPumpId(end)
            }
        }
        refreshDriverState()
    }

    /**
     * Every delivery of the log has a start and an end. A gap between them is time when the pump delivered nothing,
     * for a stop that no status showed. It counts as zero, or AAPS counts insulin that was never given.
     */
    suspend fun onHistoryDelivery(start: Long, end: Long) {
        val last = aaruyPump.lastDeliveryEnd
        if (last > 0 && start - last > GAP_MS) fillGap(last, start)
        if (end > last) aaruyPump.lastDeliveryEnd = end
    }

    private suspend fun fillGap(from: Long, to: Long) {
        // Not near the start of the gap: a record that ended just before it would look as if it covers the gap.
        if (persistenceLayer.getTemporaryBasalActiveAt((from + to) / 2) != null) return
        val end = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(from, to, true).firstOrNull()?.timestamp ?: to
        if (end - from <= GAP_MS) return
        aapsLogger.debug(LTag.PUMPCOMM, "The log has a gap from ${dateUtil.dateAndTimeString(from)} to ${dateUtil.dateAndTimeString(end)}")
        aaruyPump.syncSuspendWithPumpId(from, end - from)
    }

    /**
     * The status wrote this temp basal late: its first frame after a lost answer had old counters, and no later frame
     * corrected them. The log has the real start. Without this the same temp basal would be in the database twice.
     */
    private suspend fun lateStart(start: Long, end: Long, rate: Double): TB? {
        val rateInDatabaseUnits = PumpRate(rate).iU(ch.concentration, true)
        val late = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(start, end, true)
            .firstOrNull { abs(it.rate - rateInDatabaseUnits) < SAME_RATE && it.ids.pumpId != null } ?: return null
        aapsLogger.debug(LTag.PUMPCOMM, "The temp basal written at ${dateUtil.dateAndTimeString(late.timestamp)} started at ${dateUtil.dateAndTimeString(start)}")
        aaruyPump.correctTBRStart(late.ids.pumpId!!, start, rate, late.timestamp + late.duration - start, PumpSync.TemporaryBasalType.fromDbType(late.type))
        return late.copy(timestamp = start, duration = late.timestamp + late.duration - start)
    }

    /** A request found means the answer was lost, which is normal. Nothing found means AAPS never asked for this, and the user should know. */
    private suspend fun addTempBasal(start: Long, rate: Double, duration: Long) {
        // An entry of 0 minutes whose next entry has the same time. The database refuses it, and that would stop the read at every try.
        if (duration <= 0) return
        if (!aaruyPump.syncTBRWithPumpId(start, rate, duration)) {
            aapsLogger.warn(LTag.PUMPCOMM, "The pump runs a temp basal of $rate that AAPS did not ask for")
            aaruyPump.announceTempBasalNotFromAaps(ch.basalRateString(PumpRate(rate), true))
        }
    }

    /**
     * The clocks of the phone and the pump differ by some seconds, so a record can start just after the entry and even end before the next read.
     * Seen on the emulator as on a real pump: the last entry of a read comes again, and it must find its own record.
     */
    private suspend fun recordAround(time: Long): TB? =
        // The one that starts here first: the one before can end some hundred ms after this start, and it is not this one.
        persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(time - SAME_TIME_MS, time + SAME_TIME_MS, true).minByOrNull { abs(it.timestamp - time) }
            ?: persistenceLayer.getTemporaryBasalActiveAt(time)?.takeIf { it.timestamp + it.duration > time + SAME_TIME_MS }

    private suspend fun refreshDriverState() = aaruyPump.fromTemporaryBasal(pumpSync.expectedPumpState().temporaryBasal)

    private companion object {

        const val STATUS_BASAL = 1
        const val STATUS_TEMP_BASAL = 2
        const val STATUS_BOLUS = 3
        const val SAME_RATE = 0.01
        const val SAME_TIME_MS = 5_000L
        const val LATE_START_MS = 30_000L
        const val HALF_MINUTE_MS = 30_000L

        /** More than the whole minutes an entry loses at each end, and short breaks around a bolus. */
        const val GAP_MS = 3 * 60_000L
        const val SUSPEND_LENGTH_MS = 24 * 60 * 60_000L
    }
}
