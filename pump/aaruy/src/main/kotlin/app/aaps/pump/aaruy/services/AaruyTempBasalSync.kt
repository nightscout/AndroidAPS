package app.aaps.pump.aaruy.services

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
import kotlin.math.max

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

    /** @param live false after a reconnect until the log is read: an end seen now can be long ago. */
    suspend fun onStatus(snapshot: AaruyStatusSnapshot, live: Boolean) {
        val running = pumpSync.expectedPumpState().temporaryBasal
        when (snapshot.status) {
            STATUS_TEMP_BASAL -> onTempBasalRunning(snapshot, running)
            // The pump goes back to the temp basal after a bolus, so this says nothing about it.
            STATUS_BOLUS      -> Unit

            STATUS_BASAL      -> if (live && running != null) aaruyPump.syncStopTBRWithPumpId(dateUtil.now())

            else              -> if (live && running?.type != PumpSync.TemporaryBasalType.PUMP_SUSPEND) {
                aapsLogger.debug(LTag.PUMPCOMM, "The pump delivers nothing, status ${snapshot.status}")
                aaruyPump.syncSuspendWithPumpId(dateUtil.now(), SUSPEND_LENGTH_MS)
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

    /** @param nextStart when the next entry of the log starts: a more exact end, because an entry counts only whole minutes. */
    suspend fun onHistoryTempBasal(item: LogItem, nextStart: Long?, statusNow: Int?) {
        val start = item.timestamp
        val end = max(nextStart ?: (start + T.mins(item.injectTime.toLong()).msecs()), start + MIN_LENGTH_MS)
        val record = recordAround(start)
        when {
            record == null                                                                      -> addTempBasal(start, item.data / 1000.0, end - start)
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
        if (recordAround(from + SAME_TIME_MS) != null) return
        val end = persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(from, to, true).firstOrNull()?.timestamp ?: to
        if (end - from <= GAP_MS) return
        aapsLogger.debug(LTag.PUMPCOMM, "The log has a gap from ${dateUtil.dateAndTimeString(from)} to ${dateUtil.dateAndTimeString(end)}")
        aaruyPump.syncSuspendWithPumpId(from, end - from)
    }

    /** A request found means the answer was lost, which is normal. Nothing found means AAPS never asked for this, and the user should know. */
    private suspend fun addTempBasal(start: Long, rate: Double, duration: Long) {
        if (!aaruyPump.syncTBRWithPumpId(start, rate, duration)) {
            aapsLogger.warn(LTag.PUMPCOMM, "The pump runs a temp basal of $rate that AAPS did not ask for")
            aaruyPump.announceTempBasalNotFromAaps(ch.basalRateString(PumpRate(rate), true))
        }
    }

    /** The clocks of the phone and the pump differ by some seconds, so a start is not exact. */
    private suspend fun recordAround(time: Long) =
        persistenceLayer.getTemporaryBasalActiveAt(time)
            ?: persistenceLayer.getTemporaryBasalActiveAt(time + SAME_TIME_MS)
            ?: persistenceLayer.getTemporaryBasalActiveAt(time - SAME_TIME_MS)

    private suspend fun refreshDriverState() = aaruyPump.fromTemporaryBasal(pumpSync.expectedPumpState().temporaryBasal)

    private companion object {

        const val STATUS_BASAL = 1
        const val STATUS_TEMP_BASAL = 2
        const val STATUS_BOLUS = 3
        const val SAME_RATE = 0.01
        const val SAME_TIME_MS = 5_000L
        const val LATE_START_MS = 30_000L
        const val MIN_LENGTH_MS = 1_000L

        /** More than the whole minutes an entry loses at each end, and short breaks around a bolus. */
        const val GAP_MS = 3 * 60_000L
        const val SUSPEND_LENGTH_MS = 24 * 60 * 60_000L
    }
}
