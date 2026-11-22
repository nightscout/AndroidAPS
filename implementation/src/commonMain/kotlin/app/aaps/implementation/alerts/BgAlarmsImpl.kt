package app.aaps.implementation.alerts

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgAlarms
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.alerts.BgRuleType
import app.aaps.core.interfaces.alerts.SharedSilence
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.Instant

/**
 * The core's own BG alarms (`_docs/ALARMS.md`, step 2), evaluated on every stored reading, on the master and on
 * every client, for every glucose source: a CGM driver raises no glucose alarm of its own.
 *
 * The rules:
 * - **Urgent low**: always on, protected, CRITICAL, all day, and it rings also in Do Not Disturb. Only its threshold
 *   is set ([UnitDoubleKey.AlertUrgentLow]).
 * - **The user's own rules** ([BgAlarmRules]): any number of each type ([BgRuleType]), each with a threshold, a
 *   severity, a time-of-day window and its silence length:
 *   - **level**, low and high, with how long it must last;
 *   - **persistent high**: high for a long time, with regular readings, and not coming down
 *     ([FALLING_MGDL_PER_MINUTE]);
 *   - **falling or rising fast**: a rate per minute. The average over the last [RATE_WINDOW_MS] and the last step
 *     ([STEP_WINDOW_MS]) must both be past it, so one noisy reading does not ring, and a fall that has stopped does
 *     not either. Per minute, so it works for 1-minute and 5-minute sensors alike;
 *   - **predicted low**: the loop's prediction goes below the threshold within the next minutes ([prediction]).
 *
 * The rules are the master's, synced to the clients; each phone decides only whether it rings (Ring, Silent or Off
 * per kind, and a protected kind always rings).
 *
 * **One alert per slot** ([Slot]: low, high, persistent high, falling, rising, predicted low). The strongest rule that
 * matches wins: urgent low, then the lowest low; the highest high; the fastest rate. A stronger rule replaces a weaker
 * one at once and rings. A weaker one taking over from a stronger one is the same occurrence and does not ring again,
 * while this process runs.
 *
 * **The occurrence of a level is the episode.** A level alert's time is the first reading of the run past the
 * threshold, so the same episode is the same alert after a restart too, and a silence the user gave it still holds; a
 * new episode rings. A rate or a prediction has no such start: its occurrence is the reading that raised it.
 *
 * **"How long it must last"** counts from the first reading past the threshold. **It ends a little inside the
 * threshold** ([END_MARGIN_MGDL], for a rate [KEEP_RATE_FACTOR]), so readings that wobble around it do not ring again
 * and again. Old data neither raises nor ends it: telling the user that the data is old is the stale data alarm's job.
 * A reading from the future is not used (`_docs/CGM_DESIGN.md`, section 3).
 */
@SingleIn(AppScope::class)
@Inject
class BgAlarmsImpl(
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val rules: BgAlarmRules,
    private val alerts: Alerts,
    private val profileUtil: ProfileUtil,
    private val decimalFormatter: DecimalFormatter,
    private val notificationManager: NotificationManager,
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val loop: Loop,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val scope: CoroutineScope
) : BgAlarms {

    /** The alerts the rules ring with: one each, the strongest matching rule of the slot. */
    private enum class Slot(val key: String, val kind: BgAlert) {

        LOW(KEY_LOW, BgAlert.LOW),
        HIGH(KEY_HIGH, BgAlert.HIGH),
        PERSISTENT_HIGH(KEY_PERSISTENT_HIGH, BgAlert.PERSISTENT_HIGH),
        FALLING(KEY_FALLING, BgAlert.FALLING_FAST),
        RISING(KEY_RISING, BgAlert.RISING_FAST),
        PREDICTED_LOW(KEY_PREDICTED_LOW, BgAlert.PREDICTED_LOW)
    }

    /** A rule as the evaluator sees it; urgent low is one, with its fixed properties. */
    private data class Level(
        val id: String,
        val kind: BgAlert,
        val type: BgRuleType,
        val direction: BgDirection,
        val thresholdMgdl: Double,
        val severity: AlertSeverity,
        val durationMinutes: Int,
        val silenceMinutes: Int,
        val breakThroughDnd: Boolean
    )

    /** What the rules look at in one evaluation: the readings, the newest first, and the loop's prediction. */
    private class Data(val readings: List<GV>, val prediction: List<GV>, val now: Long) {

        val last: GV get() = readings.first()
    }

    /** The level that rings now in each slot, and the time of its occurrence. */
    private val current = mutableMapOf<Slot, Pair<Level, Long>>()

    private var started = false

    /** When the BG alarms started: the stale data alarm waits a grace time after it, for the first reading to come. */
    internal var startedAt = 0L

    /** The time the stale data alert is booked for now, or null when none is booked. */
    private var staleBookedAt: Long? = null

    override fun start() {
        if (started) return
        started = true
        startedAt = dateUtil.now()
        scope.launch {
            // Not before the start sequence has run the database migrations; a slow start delays the alarms, it
            // must not lose them.
            while (!config.awaitInitialized()) aapsLogger.debug(LTag.BGSOURCE, "BG alarms wait for the app start")
            showIntroOnce()
            // The silences given for every phone (step 2f): the master's list, which it also keeps from its clients, and
            // a client's copy of it from the master's status doc. Each new one silences the same alarm here. The stored
            // ones first, before the first evaluation: an alarm raised at the start must not ring for a moment before
            // its silence is read (seen on the Pixel)
            applySharedSilences(preferences.get(StringNonKey.AlertSharedSilences))
            launch {
                preferences.observe(StringNonKey.AlertSharedSilences).collect { applySharedSilences(it) }
            }
            merge(
                persistenceLayer.observeChanges(GV::class).map { },
                preferences.observe(UnitDoubleKey.AlertUrgentLow).map { },
                rules.rules.map { },
                preferences.observe(BooleanKey.AlertMissedBgReading).map { },
                preferences.observe(IntKey.AlertsStaleDataThreshold).map { },
                // A new prediction: the loop ran on the master, a device status came on a client
                merge(rxBus.toFlow(EventLoopUpdateGui::class).map { }, rxBus.toFlow(EventNsClientStatusUpdated::class).map { })
                    .filter { usesPrediction() }
            ).onStart { emit(Unit) }.collect {
                try {
                    evaluate()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aapsLogger.error(LTag.BGSOURCE, "BG alarms not evaluated", e)
                }
            }
        }
    }

    private fun applySharedSilences(text: String) =
        SharedSilence.parse(text).filter { it.source == BgAlert.SOURCE }.forEach { alerts.applySharedSilence(it) }

    /** Raises, updates or ends the alerts from the readings now. Internal for the test. */
    internal suspend fun evaluate() {
        val now = dateUtil.now()
        val readings = persistenceLayer.getBgReadingsDataFromTime(now - LOOK_BACK_MS, ascending = false)
            .filter { it.isValid && it.timestamp <= now + FUTURE_TOLERANCE_MS }
            .sortedByDescending { it.timestamp }
        // Before the check for fresh data: old data is exactly the stale data alarm's case
        evaluateStale(readings.firstOrNull() ?: persistenceLayer.getLastGlucoseValue()?.takeIf { it.isValid && it.timestamp <= now + FUTURE_TOLERANCE_MS }, now)
        val last = readings.firstOrNull() ?: return
        if (now - last.timestamp > FRESH_MS) return
        val minuteOfDay = Instant.fromEpochMilliseconds(now).toLocalDateTime(SystemTimeZone.current()).let { it.hour * 60 + it.minute }
        val data = Data(readings, if (usesPrediction()) prediction(now) else emptyList(), now)
        for (slot in Slot.entries) evaluate(slot, levels(slot, minuteOfDay), data)
    }

    private fun usesPrediction() = rules.rules.value.any { it.enabled && it.type == BgRuleType.PREDICTED_LOW }

    /**
     * The loop's newest prediction, oldest point first, or empty when there is none or it is older than [FRESH_MS]. The
     * master's own loop, or on a client the master's, from its device status. Of the loop's curves: with carbs on
     * board the carbs curve, else the unannounced meal curve, else the insulin curve - the one the loop itself leads
     * with. Never the zero-temp curve: it is the worst case the loop guards against, not what it expects.
     */
    private fun prediction(now: Long): List<GV> {
        val result = (if (config.APS) loop.lastRun?.constraintsProcessed else processedDeviceStatusData.getAPSResult()) ?: return emptyList()
        if (now - result.date > FRESH_MS || result.date > now + FUTURE_TOLERANCE_MS) return emptyList()
        val all = result.predictionsAsGv
        return listOf(SourceSensor.COB_PREDICTION, SourceSensor.UAM_PREDICTION, SourceSensor.IOB_PREDICTION)
            .firstNotNullOfOrNull { curve -> all.filter { it.sourceSensor == curve }.takeIf { it.isNotEmpty() } }
            ?.sortedBy { it.timestamp }
            ?: emptyList()
    }

    /**
     * The stale data alarm (`_docs/ALARMS.md`, step 2b): a booked alert at the last reading plus the threshold, booked
     * again at every reading, so it rings only when no reading came in time - also when the app does not run then
     * (Android wakes it, iOS rings its notification). A new reading ends it. After a start it waits [STALE_GRACE_MS]
     * for the first reading. Off ("Alert if no BG data is received" off), or no reading at all: nothing booked.
     */
    private fun evaluateStale(last: GV?, now: Long) {
        if (!preferences.get(BooleanKey.AlertMissedBgReading) || last == null) {
            if (staleBookedAt != null) {
                alerts.unschedule(BgAlert.SOURCE, KEY_STALE)
                staleBookedAt = null
            }
            resolveIfActive(KEY_STALE)
            return
        }
        val dueAt = last.timestamp + preferences.get(IntKey.AlertsStaleDataThreshold) * 60_000L
        // A reading in time: the readings came back, the alarm of the last gap is over
        if (now < dueAt) resolveIfActive(KEY_STALE)
        val at = maxOf(dueAt, startedAt + STALE_GRACE_MS)
        if (staleBookedAt == at) return
        if (alerts.schedule(staleAlert(last, at), at)) staleBookedAt = at
        else aapsLogger.error(LTag.BGSOURCE, "Stale data alarm not booked: the phone cannot book it")
    }

    private fun resolveIfActive(key: String) {
        if (alerts.active.value.any { it.source == BgAlert.SOURCE && it.key == key }) alerts.resolve(BgAlert.SOURCE, key)
    }

    private fun staleAlert(last: GV, at: Long) = Alert(
        kind = BgAlert.STALE,
        key = KEY_STALE,
        severity = AlertSeverity.CRITICAL,
        title = BgAlert.STALE.title,
        text = InterfacesStrings.bg_alarm_stale_text.withArgs(dateUtil.timeString(last.timestamp)),
        // The time of the gap: the same gap is the same alert, so a silence of it holds
        raisedAt = at,
        lifetime = AlertLifetime.CONDITION
    )

    private fun slotOf(rule: BgAlarmRule): Slot = when (rule.type) {
        BgRuleType.LEVEL           -> if (rule.direction == BgDirection.LOW) Slot.LOW else Slot.HIGH
        BgRuleType.PERSISTENT_HIGH -> Slot.PERSISTENT_HIGH
        BgRuleType.RATE            -> if (rule.direction == BgDirection.LOW) Slot.FALLING else Slot.RISING
        BgRuleType.PREDICTED_LOW   -> Slot.PREDICTED_LOW
    }

    /** The rules of [slot] that apply now, the strongest first. */
    private fun levels(slot: Slot, minuteOfDay: Int): List<Level> {
        val own = rules.rules.value
            .filter { it.enabled && slotOf(it) == slot && it.appliesAt(minuteOfDay) }
            .map {
                Level(
                    id = it.id, kind = slot.kind, type = it.type, direction = it.direction, thresholdMgdl = it.thresholdMgdl,
                    severity = it.severity, durationMinutes = it.durationMinutes, silenceMinutes = it.silenceMinutes, breakThroughDnd = false
                )
            }
        val all = if (slot == Slot.LOW) listOf(urgentLow()) + own else own
        return all.sortedByDescending { strength(it) }
    }

    private fun urgentLow() = Level(
        id = URGENT_LOW_ID, kind = BgAlert.URGENT_LOW, type = BgRuleType.LEVEL, direction = BgDirection.LOW,
        thresholdMgdl = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.AlertUrgentLow)),
        severity = AlertSeverity.CRITICAL, durationMinutes = 0, silenceMinutes = URGENT_LOW_SILENCE_MINUTES, breakThroughDnd = true
    )

    private fun evaluate(slot: Slot, levels: List<Level>, data: Data) {
        val key = slot.key
        val active = alerts.active.value.any { it.source == BgAlert.SOURCE && it.key == key }
        val ringing = current[slot]?.takeIf { active }
        // The strongest rule whose condition holds now
        val winner = levels.firstOrNull { raises(it, data) }
        // The rule that rings now stays while it holds - as the rule is now: an edited threshold applies at once
        // (found on the Pixel: the old threshold kept the alert on)
        val keep = ringing?.first?.let { old -> levels.firstOrNull { it.id == old.id } }?.takeIf { holds(it, data) }
        val level = when {
            winner == null                     -> keep
            keep == null                       -> winner
            // A weaker rule does not take over from a stronger one while that one is still on
            strength(winner) >= strength(keep) -> winner
            else                               -> keep
        }
        if (level == null) {
            current.remove(slot)
            if (active) {
                aapsLogger.info(LTag.BGSOURCE, "BG alarm $key over: ${data.last.value} mg/dl")
                alerts.resolve(BgAlert.SOURCE, key)
            }
            return
        }
        val raisedAt = when {
            // The same occurrence: a weaker rule after a stronger one, or the same rule again
            ringing != null && strength(level) <= strength(ringing.first) -> ringing.second
            else                                                         -> since(level, data)
        }
        current[slot] = level to raisedAt
        alerts.raise(alertOf(level, key, raisedAt, data))
        smartSilence(slot, level, data)
    }

    /**
     * Smart silence (`_docs/ALARMS.md`, step 2d): a silenced low or high does not ring again while BG already moves
     * back towards the range. When the silence ends before the next reading can tell ([SMART_SILENCE_WINDOW_MS]), and
     * both the average over the last [RATE_WINDOW_MS] and the last step move back by at least
     * [SMART_SILENCE_MGDL_PER_MINUTE], the silence is made [SMART_SILENCE_EXTEND_MINUTES] longer than its end. It is
     * checked again at every reading, so the alert rings once BG stops coming back. Never for urgent low, and not for
     * a rate or a prediction: those end by themselves when BG turns.
     */
    private fun smartSilence(slot: Slot, level: Level, data: Data) {
        if (slot !in SMART_SILENCE_SLOTS || level.id == URGENT_LOW_ID) return
        val until = alerts.silencedUntil(BgAlert.SOURCE, slot.key)
        if (until == 0L || until - data.now > SMART_SILENCE_WINDOW_MS) return
        val average = averageRate(data.readings, RATE_WINDOW_MS, RATE_MIN_SPAN_MS) ?: return
        val step = averageRate(data.readings, STEP_WINDOW_MS, STEP_MIN_SPAN_MS) ?: return
        // Towards the range: up for a low, down for a high
        val towardsRange = if (level.direction == BgDirection.LOW) 1.0 else -1.0
        if (average * towardsRange < SMART_SILENCE_MGDL_PER_MINUTE || step * towardsRange < SMART_SILENCE_MGDL_PER_MINUTE) return
        val minutes = ((until - data.now) / 60_000L).toInt() + SMART_SILENCE_EXTEND_MINUTES
        aapsLogger.info(LTag.BGSOURCE, "BG alarm ${slot.key}: BG moves back to the range, silence longer by $SMART_SILENCE_EXTEND_MINUTES min")
        alerts.snooze(BgAlert.SOURCE, slot.key, minutes)
    }

    /** How strong a rule is in its slot: the further from the range or the faster, the stronger; urgent low the strongest. */
    private fun strength(level: Level): Double = when {
        level.id == URGENT_LOW_ID           -> Double.MAX_VALUE
        level.type == BgRuleType.RATE       -> level.thresholdMgdl
        level.direction == BgDirection.LOW  -> -level.thresholdMgdl
        else                                -> level.thresholdMgdl
    }

    /** Whether [level] starts ringing now. */
    private fun raises(level: Level, data: Data): Boolean = when (level.type) {
        BgRuleType.LEVEL           -> lastedLongEnough(level, data)
        BgRuleType.PERSISTENT_HIGH -> {
            val since = runStart(data.readings, level, margin = 0.0)
            since != null && lastedLongEnough(level, data) && regular(data.readings, since) &&
                (averageRate(data.readings, RATE_WINDOW_MS, RATE_MIN_SPAN_MS) ?: 0.0) > -FALLING_MGDL_PER_MINUTE
        }

        BgRuleType.RATE            -> {
            val average = averageRate(data.readings, RATE_WINDOW_MS, RATE_MIN_SPAN_MS)
            val step = averageRate(data.readings, STEP_WINDOW_MS, STEP_MIN_SPAN_MS)
            average != null && step != null && pastRate(level, average, 1.0) && pastRate(level, step, 1.0)
        }

        BgRuleType.PREDICTED_LOW   -> data.last.value >= level.thresholdMgdl && predictedBelow(level.thresholdMgdl, level, data) != null
    }

    /** Whether [level], ringing now, stays on: a little inside its threshold still holds. */
    private fun holds(level: Level, data: Data): Boolean = when (level.type) {
        BgRuleType.LEVEL, BgRuleType.PERSISTENT_HIGH -> runStart(data.readings, level, END_MARGIN_MGDL) != null
        BgRuleType.RATE                              -> averageRate(data.readings, RATE_WINDOW_MS, RATE_MIN_SPAN_MS)?.let { pastRate(level, it, KEEP_RATE_FACTOR) } == true
        // Already low: the level rules' job from now on
        BgRuleType.PREDICTED_LOW                     -> data.last.value >= level.thresholdMgdl - END_MARGIN_MGDL &&
            predictedBelow(level.thresholdMgdl + END_MARGIN_MGDL, level, data) != null
    }

    /** The time of a new occurrence of [level]. */
    private fun since(level: Level, data: Data): Long = when (level.type) {
        BgRuleType.LEVEL, BgRuleType.PERSISTENT_HIGH -> runStart(data.readings, level, END_MARGIN_MGDL) ?: data.last.timestamp
        BgRuleType.RATE, BgRuleType.PREDICTED_LOW    -> data.last.timestamp
    }

    private fun lastedLongEnough(level: Level, data: Data): Boolean {
        val since = runStart(data.readings, level, margin = 0.0) ?: return false
        return data.last.timestamp - since >= level.durationMinutes * 60_000L
    }

    /** Whether the readings from [from] to the newest came without a gap longer than [REGULAR_GAP_MS]. */
    private fun regular(readings: List<GV>, from: Long): Boolean =
        readings.takeWhile { it.timestamp >= from }.zipWithNext().all { (newer, older) -> newer.timestamp - older.timestamp <= REGULAR_GAP_MS }

    private fun pastRate(level: Level, rate: Double, factor: Double): Boolean = when (level.direction) {
        BgDirection.LOW  -> rate <= -level.thresholdMgdl * factor
        BgDirection.HIGH -> rate >= level.thresholdMgdl * factor
    }

    /**
     * The first point of the prediction below [thresholdMgdl] from now until the rule's look-ahead
     * ([BgAlarmRule.durationMinutes]), or null when there is none.
     */
    private fun predictedBelow(thresholdMgdl: Double, level: Level, data: Data): GV? =
        data.prediction.firstOrNull { it.timestamp in data.now..data.now + level.durationMinutes * 60_000L && it.value < thresholdMgdl }

    /**
     * The first reading of the run past the threshold that ends with the newest reading, or null when the newest is
     * not past it. With [margin] the run also takes readings that are only a little inside: the end margin. A long gap
     * ends the run.
     */
    private fun runStart(readings: List<GV>, level: Level, margin: Double): Long? {
        fun past(value: Double) = when (level.direction) {
            BgDirection.LOW  -> value < level.thresholdMgdl + margin
            BgDirection.HIGH -> value > level.thresholdMgdl - margin
        }
        if (!past(readings.first().value)) return null
        var start = readings.first().timestamp
        for (reading in readings.drop(1)) {
            if (!past(reading.value) || start - reading.timestamp > MAX_GAP_MS) break
            start = reading.timestamp
        }
        return start
    }

    private fun alertOf(level: Level, key: String, raisedAt: Long, data: Data): Alert {
        val bg = profileUtil.fromMgdlToStringWithUnits(data.last.value)
        val text = when (level.type) {
            BgRuleType.LEVEL, BgRuleType.PERSISTENT_HIGH -> InterfacesStrings.bg_alarm_text.withArgs(bg)
            BgRuleType.RATE                              -> averageRate(data.readings, RATE_WINDOW_MS, RATE_MIN_SPAN_MS)
                ?.let { InterfacesStrings.bg_alarm_rate_text.withArgs(bg, rateInUnits(abs(it)), profileUtil.unitLabel) }
                ?: InterfacesStrings.bg_alarm_text.withArgs(bg)

            BgRuleType.PREDICTED_LOW                     -> predictedBelow(level.thresholdMgdl + END_MARGIN_MGDL, level, data)
                ?.let { InterfacesStrings.bg_alarm_predicted_text.withArgs(dateUtil.timeString(it.timestamp), profileUtil.fromMgdlToStringWithUnits(it.value)) }
                ?: InterfacesStrings.bg_alarm_text.withArgs(bg)
        }
        return Alert(
            kind = level.kind,
            key = key,
            severity = level.severity,
            title = level.kind.title,
            text = text,
            raisedAt = raisedAt,
            lifetime = AlertLifetime.CONDITION,
            silenceMinutes = level.silenceMinutes,
            breakThroughDnd = level.breakThroughDnd
        )
    }

    /** A rate in mg/dl per minute in the user's units: mmol/l needs two decimals (3 mg/dl is 0.17 mmol/l). */
    private fun rateInUnits(mgdlPerMinute: Double): String {
        val value = profileUtil.fromMgdlToUnits(mgdlPerMinute)
        return if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to1Decimal(value) else decimalFormatter.to2Decimal(value)
    }

    /** One card after the update: AAPS has BG alarms of its own now, and urgent low is on. */
    private fun showIntroOnce() {
        if (preferences.get(BooleanNonKey.BgAlarmsIntroShown)) return
        preferences.put(BooleanNonKey.BgAlarmsIntroShown, true)
        notificationManager.post(NotificationId.BG_ALARMS_INTRO, rh.gs(InterfacesStrings.bg_alarms_intro))
    }

    companion object {

        /** The key of the low alert: one per slot, so a stronger low rule replaces a weaker one. */
        const val KEY_LOW = "low"

        /** The key of the high alert. */
        const val KEY_HIGH = "high"

        /** The key of the stale data alert. */
        const val KEY_STALE = "stale"

        /** The key of the persistent high alert. */
        const val KEY_PERSISTENT_HIGH = "persistent_high"

        /** The key of the falling fast alert. */
        const val KEY_FALLING = "falling"

        /** The key of the rising fast alert. */
        const val KEY_RISING = "rising"

        /** The key of the predicted low alert. */
        const val KEY_PREDICTED_LOW = "predicted_low"

        /** After a start, the stale data alarm waits this long for the first reading (the old pre-snooze). */
        const val STALE_GRACE_MS = 5 * 60_000L

        private const val URGENT_LOW_ID = "urgent_low"

        /** Long enough for the longest "how long it must last" of a rule, and for the episode to start in it. */
        private const val LOOK_BACK_MS = 6 * 60 * 60_000L

        /** A newer reading than this is from the future: not used, neither to raise nor to end an alarm. */
        private const val FUTURE_TOLERANCE_MS = 60_000L

        /** Readings, and a prediction, older than this are old data: the stale data alarm's job, not a level alarm's. */
        private const val FRESH_MS = 15 * 60_000L

        /** A gap this long between two readings ends an episode. */
        private const val MAX_GAP_MS = 15 * 60_000L

        /** Persistent high needs regular readings: no gap longer than this (one missed 5-minute reading is fine). */
        private const val REGULAR_GAP_MS = 11 * 60_000L

        /** An alarm ends this far inside its threshold, so readings around it do not ring again and again. */
        const val END_MARGIN_MGDL = 5.0

        /** Urgent low is silenced only briefly (`_docs/ALARMS.md`: "an urgent low 15 minutes"). */
        const val URGENT_LOW_SILENCE_MINUTES = 15

        /** The rate is the average over this time ... */
        const val RATE_WINDOW_MS = 15 * 60_000L

        /** ... over at least this long, so a few readings after a gap do not make a rate. */
        private const val RATE_MIN_SPAN_MS = 10 * 60_000L

        /** The last step: the change over this time, one step of a 5-minute sensor, five of a 1-minute one. */
        const val STEP_WINDOW_MS = 6 * 60_000L

        private const val STEP_MIN_SPAN_MS = 2 * 60_000L

        /** A rate alert stays on while the average rate is still this part of the rule's rate. */
        const val KEEP_RATE_FACTOR = 0.75

        /** Smart silence: BG must move back to the range at least this fast (15 mg/dl an hour). */
        const val SMART_SILENCE_MGDL_PER_MINUTE = 0.25

        /** Smart silence acts when the silence ends within this time: before the next 5-minute reading could tell. */
        const val SMART_SILENCE_WINDOW_MS = 6 * 60_000L

        /** Smart silence makes the silence this much longer than its end; the next reading decides again. */
        const val SMART_SILENCE_EXTEND_MINUTES = 5

        private val SMART_SILENCE_SLOTS = setOf(Slot.LOW, Slot.HIGH, Slot.PERSISTENT_HIGH)

        /** Persistent high does not start while BG comes down faster than this (30 mg/dl an hour): it is being treated. */
        const val FALLING_MGDL_PER_MINUTE = 0.5

        /**
         * The change of BG per minute over the last [windowMs] (the least squares line through the readings, newest
         * first in [readings]), or null when the readings in it span less than [minSpanMs].
         */
        internal fun averageRate(readings: List<GV>, windowMs: Long, minSpanMs: Long): Double? {
            val newest = readings.firstOrNull()?.timestamp ?: return null
            val points = readings.takeWhile { newest - it.timestamp <= windowMs }
            if (points.size < 2 || newest - points.last().timestamp < minSpanMs) return null
            val xs = points.map { (it.timestamp - newest) / 60_000.0 }
            val meanX = xs.average()
            val meanY = points.map { it.value }.average()
            val sxx = xs.sumOf { (it - meanX) * (it - meanX) }
            val sxy = xs.indices.sumOf { (xs[it] - meanX) * (points[it].value - meanY) }
            return sxy / sxx
        }
    }
}

/** The BG alarms in the alert settings, always shown. */
@ContributesIntoSet(AppScope::class, binding = binding<AlertKindCatalogue>())
@Inject
class BgAlertCatalogue : AlertKindCatalogue {

    override val title: TextRef = InterfacesStrings.alert_kinds_group_bg
    override val kinds: List<AlertKind> = BgAlert.entries
    override fun inUse(): Boolean = true
}
