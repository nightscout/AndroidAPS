package app.aaps.implementation.alerts

import app.aaps.core.data.model.GV
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgAlarms
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * The core's own BG alarms (`_docs/ALARMS.md`, step 2), evaluated on every stored reading, on the master and on
 * every client, for every glucose source: a CGM driver raises no glucose alarm of its own.
 *
 * The levels:
 * - **Urgent low**: always on, protected, CRITICAL, all day, and it rings also in Do Not Disturb. Only its threshold
 *   is set ([UnitDoubleKey.AlertUrgentLow]).
 * - **The user's own rules** ([BgAlarmRules]): any number of low and high rules, each with a threshold, a severity, a
 *   time-of-day window, how long it must last, and its silence length.
 *
 * The rules are the master's, synced to the clients; each phone decides only whether it rings (Ring, Silent or Off
 * per kind, and a protected kind always rings).
 *
 * **One alert per direction** ([KEY_LOW], [KEY_HIGH]). The strongest rule that matches wins: urgent low, then the
 * lowest low; the highest high. A stronger rule replaces a weaker one at once and rings. A weaker one taking over from a
 * stronger one is the same occurrence and does not ring again, while this process runs.
 *
 * **The occurrence is the episode.** The alert's time is the first reading of the run past the threshold, so the same
 * episode is the same alert after a restart too, and a silence the user gave it still holds; a new episode rings.
 *
 * **"How long it must last"** counts from the first reading past the threshold. **It ends a little inside the
 * threshold** ([END_MARGIN_MGDL]), so readings that wobble around it do not ring again and again. Old data neither
 * raises nor ends it: telling the user that the data is old is the stale data alarm's job. A reading from the future
 * is not used (`_docs/CGM_DESIGN.md`, section 3).
 */
@SingleIn(AppScope::class)
@Inject
class BgAlarmsImpl(
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val rules: BgAlarmRules,
    private val alerts: Alerts,
    private val profileUtil: ProfileUtil,
    private val notificationManager: NotificationManager,
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val scope: CoroutineScope
) : BgAlarms {

    /** A level rule as the evaluator sees it; urgent low is one, with its fixed properties. */
    private data class Level(
        val id: String,
        val kind: BgAlert,
        val direction: BgDirection,
        val thresholdMgdl: Double,
        val severity: AlertSeverity,
        val durationMinutes: Int,
        val silenceMinutes: Int,
        val breakThroughDnd: Boolean
    )

    /** The level that rings now in each direction, and the time of its occurrence. */
    private val current = mutableMapOf<BgDirection, Pair<Level, Long>>()

    private var started = false

    override fun start() {
        if (started) return
        started = true
        scope.launch {
            // Not before the start sequence has run the database migrations; a slow start delays the alarms, it
            // must not lose them.
            while (!config.awaitInitialized()) aapsLogger.debug(LTag.BGSOURCE, "BG alarms wait for the app start")
            showIntroOnce()
            merge(
                persistenceLayer.observeChanges(GV::class).map { },
                preferences.observe(UnitDoubleKey.AlertUrgentLow).map { },
                rules.rules.map { }
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

    /** Raises, updates or ends the level alerts from the readings now. Internal for the test. */
    internal suspend fun evaluate() {
        val now = dateUtil.now()
        val readings = persistenceLayer.getBgReadingsDataFromTime(now - LOOK_BACK_MS, ascending = false)
            .filter { it.isValid && it.timestamp <= now + FUTURE_TOLERANCE_MS }
            .sortedByDescending { it.timestamp }
        val last = readings.firstOrNull() ?: return
        if (now - last.timestamp > FRESH_MS) return
        val minuteOfDay = Instant.fromEpochMilliseconds(now).toLocalDateTime(SystemTimeZone.current()).let { it.hour * 60 + it.minute }
        for (direction in BgDirection.entries) evaluate(direction, levels(direction, minuteOfDay), readings)
    }

    /** The levels of [direction] that apply now, the strongest first. */
    private fun levels(direction: BgDirection, minuteOfDay: Int): List<Level> {
        val own = rules.rules.value
            .filter { it.enabled && it.direction == direction && it.appliesAt(minuteOfDay) }
            .map {
                Level(
                    id = it.id, kind = if (direction == BgDirection.LOW) BgAlert.LOW else BgAlert.HIGH, direction = direction,
                    thresholdMgdl = it.thresholdMgdl, severity = it.severity, durationMinutes = it.durationMinutes,
                    silenceMinutes = it.silenceMinutes, breakThroughDnd = false
                )
            }
        return when (direction) {
            BgDirection.LOW  -> listOf(urgentLow()) + own.sortedBy { it.thresholdMgdl }
            BgDirection.HIGH -> own.sortedByDescending { it.thresholdMgdl }
        }
    }

    private fun urgentLow() = Level(
        id = URGENT_LOW_ID, kind = BgAlert.URGENT_LOW, direction = BgDirection.LOW,
        thresholdMgdl = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.AlertUrgentLow)),
        severity = AlertSeverity.CRITICAL, durationMinutes = 0, silenceMinutes = URGENT_LOW_SILENCE_MINUTES, breakThroughDnd = true
    )

    private fun evaluate(direction: BgDirection, levels: List<Level>, readings: List<GV>) {
        val key = keyOf(direction)
        val last = readings.first()
        val active = alerts.active.value.any { it.source == BgAlert.SOURCE && it.key == key }
        val ringing = current[direction]?.takeIf { active }
        // The strongest level whose condition holds now, long enough
        val winner = levels.firstOrNull { level ->
            val since = runStart(readings, level, margin = 0.0) ?: return@firstOrNull false
            last.timestamp - since >= level.durationMinutes * 60_000L
        }
        // The level that rings now stays while the readings are within its end margin
        val keep = ringing?.first?.takeIf { level -> levels.any { it.id == level.id } && runStart(readings, level, END_MARGIN_MGDL) != null }
        val level = when {
            winner == null                 -> keep
            keep == null                   -> winner
            // A weaker rule does not take over from a stronger one while that one is still on
            strength(winner) >= strength(keep) -> winner
            else                           -> keep
        }
        if (level == null) {
            current.remove(direction)
            if (active) {
                aapsLogger.info(LTag.BGSOURCE, "BG alarm $key over: ${last.value} mg/dl")
                alerts.resolve(BgAlert.SOURCE, key)
            }
            return
        }
        val raisedAt = when {
            // The same occurrence: a weaker rule after a stronger one, or the same rule again
            ringing != null && strength(level) <= strength(ringing.first) -> ringing.second
            else                                                         -> runStart(readings, level, END_MARGIN_MGDL) ?: last.timestamp
        }
        current[direction] = level to raisedAt
        alerts.raise(alertOf(level, key, raisedAt, last))
    }

    /** How strong a level is in its direction: the further from the range, the stronger; urgent low the strongest. */
    private fun strength(level: Level): Double = when {
        level.id == URGENT_LOW_ID           -> Double.MAX_VALUE
        level.direction == BgDirection.LOW  -> -level.thresholdMgdl
        else                                -> level.thresholdMgdl
    }

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

    private fun alertOf(level: Level, key: String, raisedAt: Long, last: GV) = Alert(
        kind = level.kind,
        key = key,
        severity = level.severity,
        title = level.kind.title,
        text = InterfacesStrings.bg_alarm_text.withArgs(profileUtil.fromMgdlToStringWithUnits(last.value)),
        raisedAt = raisedAt,
        lifetime = AlertLifetime.CONDITION,
        silenceMinutes = level.silenceMinutes,
        breakThroughDnd = level.breakThroughDnd
    )

    /** One card after the update: AAPS has BG alarms of its own now, and urgent low is on. */
    private fun showIntroOnce() {
        if (preferences.get(BooleanNonKey.BgAlarmsIntroShown)) return
        preferences.put(BooleanNonKey.BgAlarmsIntroShown, true)
        notificationManager.post(NotificationId.BG_ALARMS_INTRO, rh.gs(InterfacesStrings.bg_alarms_intro))
    }

    companion object {

        /** The key of the low alert: one per direction, so a stronger low rule replaces a weaker one. */
        const val KEY_LOW = "low"

        /** The key of the high alert. */
        const val KEY_HIGH = "high"

        fun keyOf(direction: BgDirection): String = if (direction == BgDirection.LOW) KEY_LOW else KEY_HIGH

        private const val URGENT_LOW_ID = "urgent_low"

        /** Long enough for the longest "how long it must last" of a rule, and for the episode to start in it. */
        private const val LOOK_BACK_MS = 6 * 60 * 60_000L

        /** A newer reading than this is from the future: not used, neither to raise nor to end an alarm. */
        private const val FUTURE_TOLERANCE_MS = 60_000L

        /** Readings older than this are old data: the stale data alarm's job, not a level alarm's. */
        private const val FRESH_MS = 15 * 60_000L

        /** A gap this long between two readings ends an episode. */
        private const val MAX_GAP_MS = 15 * 60_000L

        /** An alarm ends this far inside its threshold, so readings around it do not ring again and again. */
        const val END_MARGIN_MGDL = 5.0

        /** Urgent low is silenced only briefly (`_docs/ALARMS.md`: "an urgent low 15 minutes"). */
        const val URGENT_LOW_SILENCE_MINUTES = 15
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
