package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.plugins.sync.tidepool.elements.BasalElement
import app.aaps.plugins.sync.tidepool.elements.BaseElement
import app.aaps.plugins.sync.tidepool.elements.BloodGlucoseElement
import app.aaps.plugins.sync.tidepool.elements.BolusElement
import app.aaps.plugins.sync.tidepool.elements.ProfileElement
import app.aaps.plugins.sync.tidepool.elements.SensorGlucoseElement
import app.aaps.plugins.sync.tidepool.elements.WizardElement
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.keys.TidepoolLongNonKey
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Singleton
class UploadChunk @Inject constructor(
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) {

    private val maxUploadSize = T.days(7).msecs() // don't change this

    suspend fun getNext(session: Session?): String? {
        session ?: return null

        session.start = getLastEnd()
        // do not upload last 3h, TBR can be still running
        session.end = min(session.start + maxUploadSize, dateUtil.now() - T.hours(3).msecs())

        val result = get(session.start, session.end)
        if (result.length < 3) {
            aapsLogger.debug(LTag.TIDEPOOL, "No records in this time period, setting start to best end time")
            setLastEnd(session.end)
        }
        return result
    }

    suspend fun get(start: Long, end: Long): String {

        aapsLogger.debug(LTag.TIDEPOOL, "Syncing data between: " + dateUtil.dateAndTimeString(start) + " -> " + dateUtil.dateAndTimeString(end))
        if (end <= start) {
            aapsLogger.debug(LTag.TIDEPOOL, "End is <= start: " + dateUtil.dateAndTimeString(start) + " " + dateUtil.dateAndTimeString(end))
            return ""
        }
        if (end - start > maxUploadSize) {
            aapsLogger.debug(LTag.TIDEPOOL, "More than max range - rejecting")
            return ""
        }

        val records = LinkedList<BaseElement>()

        records.addAll(getTreatments(start, end))
        records.addAll(getBloodTests(start, end))
        records.addAll(getBasals(start, end))
        records.addAll(getBgReadings(start, end))
        records.addAll(getProfiles(start, end))

        return GsonInstance.defaultGsonInstance().toJson(records)
    }

    fun getLastEnd(): Long {
        val result = preferences.get(TidepoolLongNonKey.LastEnd)
        return max(result, dateUtil.now() - T.months(2).msecs())
    }

    fun setLastEnd(time: Long) {
        if (time > getLastEnd()) {
            preferences.put(TidepoolLongNonKey.LastEnd, time)
            val friendlyEnd = dateUtil.dateAndTimeString(time)
            rxBus.send(EventTidepoolStatus(("Marking uploaded data up to $friendlyEnd")))
            aapsLogger.debug(LTag.TIDEPOOL, "Updating last end to: " + dateUtil.dateAndTimeString(time))
        } else {
            aapsLogger.debug(LTag.TIDEPOOL, "Cannot set last end to: " + dateUtil.dateAndTimeString(time) + " vs " + dateUtil.dateAndTimeString(getLastEnd()))
        }
    }

    private suspend fun getTreatments(start: Long, end: Long): List<BaseElement> {
        val result = LinkedList<BaseElement>()
        persistenceLayer.getBolusesFromTimeToTime(start, end, true)
            .forEach { bolus ->
                result.add(BolusElement(bolus, dateUtil))
            }
        persistenceLayer.getCarbsFromTimeToTimeExpanded(start, end, true)
            .forEach { carb ->
                profileFunction.getProfile(carb.timestamp)?.let { profile ->
                    if (carb.amount > 0.0)
                        result.add(WizardElement(carb, dateUtil, profile.iCfg))
                }
            }
        return result
    }

    private suspend fun getBloodTests(start: Long, end: Long): List<BloodGlucoseElement> {
        val readings = persistenceLayer.getTherapyEventDataFromToTime(start, end)
        val selection = BloodGlucoseElement.fromCareportalEvents(readings, dateUtil, profileUtil)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} BGs selected for upload"))
        return selection

    }

    private suspend fun getBgReadings(start: Long, end: Long): List<SensorGlucoseElement> {
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true)
        val selection = SensorGlucoseElement.fromBgReadings(readings, dateUtil)
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} CGMs selected for upload"))
        return selection
    }

    private val basalSegmentFallbackStep = T.mins(1).msecs()

    private fun secondsFromMidnight(timestamp: Long): Int {
        val localDateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
        return localDateTime.hour * 3600 + localDateTime.minute * 60 + localDateTime.second
    }

    /**
     * Wall-clock time of the next basal-schedule rate change strictly after [timestamp] (DST aware), or null if none.
     * Returns null when the computed boundary would not advance past [timestamp] (e.g. an ambiguous DST fall-back hour);
     * the caller's fallback step then keeps the walk progressing.
     */
    private fun nextBasalBlockBoundary(timestamp: Long, profile: Profile): Long? {
        val seconds = secondsFromMidnight(timestamp)
        val nextSeconds = profile.getBasalValues().map { it.timeAsSeconds }.filter { it > seconds }.minOrNull() ?: 86_400
        val zone = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone).date
        val boundary =
            if (nextSeconds >= 86_400) date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            else LocalDateTime(date, LocalTime(nextSeconds / 3600, nextSeconds % 3600 / 60, nextSeconds % 60)).toInstant(zone)
        return boundary.toEpochMilliseconds().takeIf { it > timestamp }
    }

    private fun isSuspend(tbr: TB): Boolean =
        tbr.type == TB.Type.PUMP_SUSPEND || tbr.type == TB.Type.EMULATED_PUMP_SUSPEND

    /**
     * Builds a continuous, non-overlapping basal timeline for [start]..[end]:
     *  - intervals with an active temporary basal -> `automated` (or `suspend` for pump suspends)
     *  - intervals without a temporary basal      -> `scheduled` (the profile/baseline basal line)
     *
     * Segments are split where the profile (scheduled) rate changes or the active profile switches,
     * so each record carries a single correct rate. Tidepool renders the basal graph from these
     * events, so without the `scheduled` segments the profile basal line would not be visible.
     */
    private suspend fun getBasals(start: Long, end: Long): List<BasalElement> {
        if (end <= start) return emptyList()
        // Include TBRs that started before the window but may still be active at start (covers the longest allowed TBR).
        val tbrList = persistenceLayer
            .getTemporaryBasalsStartingFromTimeToTime(max(0L, start - T.days(2).msecs()), end, true)
            .filter { it.timestamp + it.duration > start }
            .sortedBy { it.timestamp }
        val profileSwitchStarts = persistenceLayer
            .getEffectiveProfileSwitchesFromTimeToTime(start, end, true)
            .map { it.timestamp }
            .filter { it in (start + 1) until end }
            .sorted()
        // Split at running-mode changes so a single segment never spans an open<->closed loop transition;
        // the delivery type of a no-TBR (profile-rate) interval depends on whether the loop was closed there.
        val runningModeStarts = persistenceLayer
            .getRunningModesFromTimeToTime(start, end, true)
            .map { it.timestamp }
            .filter { it in (start + 1) until end }
            .sorted()

        val results = LinkedList<BasalElement>()
        var cursor = start
        while (cursor < end) {
            val profile = profileFunction.getProfile(cursor)
            // Latest-starting TBR active at cursor wins (a newer TBR supersedes an overlapping older one).
            val activeTbr = tbrList.lastOrNull { cursor >= it.timestamp && cursor < it.timestamp + it.duration }
            val nextTbrStart = tbrList.firstOrNull { it.timestamp > cursor }?.timestamp ?: end

            var boundary = if (activeTbr != null) min(activeTbr.timestamp + activeTbr.duration, nextTbrStart) else nextTbrStart
            profileSwitchStarts.firstOrNull { it > cursor }?.let { boundary = min(boundary, it) }
            runningModeStarts.firstOrNull { it > cursor }?.let { boundary = min(boundary, it) }
            // The rate follows the profile only for scheduled gaps and percentage temp basals.
            if (profile != null && (activeTbr == null || (!isSuspend(activeTbr) && !activeTbr.isAbsolute)))
                nextBasalBlockBoundary(cursor, profile)?.let { boundary = min(boundary, it) }

            if (boundary <= cursor) boundary = min(cursor + basalSegmentFallbackStep, end)
            if (boundary <= cursor) break
            val duration = boundary - cursor

            when {
                activeTbr != null && isSuspend(activeTbr)        ->
                    results.add(BasalElement.pumpSuspend(cursor, duration, activeTbr.timestamp, dateUtil))

                activeTbr != null && profile != null             ->
                    results.add(BasalElement.automated(cursor, duration, activeTbr.convertedToAbsolute(cursor, profile), activeTbr.timestamp, dateUtil))

                activeTbr != null && activeTbr.isAbsolute        ->
                    results.add(BasalElement.automated(cursor, duration, activeTbr.rate, activeTbr.timestamp, dateUtil))

                activeTbr == null && profile != null             -> {
                    val rate = profile.getBasalTimeFromMidnight(secondsFromMidnight(cursor))
                    // While the loop is closed/LGS the profile-rate gap is still automated delivery; emit it as
                    // `automated` so the closed-loop session stays one band (avoids per-cycle M/A markers in Tidepool).
                    if (persistenceLayer.getRunningModeActiveAt(cursor).mode.isClosedLoopOrLgs())
                        results.add(BasalElement.loopBaseline(cursor, duration, rate, dateUtil))
                    else
                        results.add(BasalElement.scheduled(cursor, duration, rate, dateUtil))
                }
                // no profile (and not an absolute temp) -> nothing reliable to emit for this interval
            }
            cursor = boundary
        }

        if (results.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${results.size} basal records selected for upload"))
        return results
    }

    private fun newInstanceOrNull(ps: EPS): ProfileElement? = try {
        ProfileElement(ps, activePlugin.activePump.serialNumber(), dateUtil, profileUtil)
    } catch (_: Throwable) {
        null
    }

    private suspend fun getProfiles(start: Long, end: Long): List<ProfileElement> {
        val pss = persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(start, end, true)
        val selection = LinkedList<ProfileElement>()
        for (ps in pss) {
            newInstanceOrNull(ps)?.let {
                selection.add(it)
            }
        }
        if (selection.isNotEmpty())
            rxBus.send(EventTidepoolStatus("${selection.size} ProfileSwitches selected for upload"))
        return selection
    }

}