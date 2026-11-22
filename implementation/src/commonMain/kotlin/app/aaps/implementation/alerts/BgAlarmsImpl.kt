package app.aaps.implementation.alerts

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarms
import app.aaps.core.interfaces.alerts.BgAlert
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
import kotlin.coroutines.cancellation.CancellationException

/**
 * The core's own BG alarms (`_docs/ALARMS.md`, step 2), evaluated on every stored reading, on the master and on
 * every client, for every glucose source: a CGM driver raises no glucose alarm of its own.
 *
 * Step 2a-1 has the urgent low rule only: always on, protected, CRITICAL, all day, and it rings also in Do Not
 * Disturb. Its threshold is the master's ([UnitDoubleKey.AlertUrgentLow], synced to the clients); each phone decides
 * only whether it rings, which for a protected kind means it always does. The user's own low and high rules follow.
 *
 * **One alert per direction**, key [KEY_LOW]: a stronger low rule replaces a weaker one under the same key.
 *
 * **The occurrence is the episode.** The alert's time is the first reading of the run of low readings, so the same
 * episode is the same alert after a restart too, and a silence the user gave it still holds; a new episode rings.
 *
 * **It ends a little above the threshold** ([END_MARGIN_MGDL]), so readings that wobble around it do not ring again
 * and again. Old data neither raises nor ends it: telling the user that the data is old is the stale data alarm's
 * job. A reading from the future is not used (`_docs/CGM_DESIGN.md`, section 3).
 */
@SingleIn(AppScope::class)
@Inject
class BgAlarmsImpl(
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val alerts: Alerts,
    private val profileUtil: ProfileUtil,
    private val notificationManager: NotificationManager,
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val scope: CoroutineScope
) : BgAlarms {

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
                preferences.observe(UnitDoubleKey.AlertUrgentLow).map { }
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
        evaluateLow(readings, last)
    }

    private fun evaluateLow(readings: List<GV>, last: GV) {
        val threshold = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.AlertUrgentLow))
        val active = alerts.active.value.any { it.source == BgAlert.SOURCE && it.key == KEY_LOW }
        val low = last.value < threshold
        val stillLow = last.value < threshold + END_MARGIN_MGDL
        when {
            low || (active && stillLow) -> alerts.raise(urgentLow(episodeStart(readings, threshold + END_MARGIN_MGDL), last))
            active                      -> {
                aapsLogger.info(LTag.BGSOURCE, "Urgent low over: ${last.value} mg/dl")
                alerts.resolve(BgAlert.SOURCE, KEY_LOW)
            }
        }
    }

    /** The first reading of the run of readings below [limit] that ends with the newest one; a long gap ends the run. */
    private fun episodeStart(readings: List<GV>, limit: Double): Long {
        var start = readings.first().timestamp
        for (reading in readings.drop(1)) {
            if (reading.value >= limit || start - reading.timestamp > MAX_GAP_MS) break
            start = reading.timestamp
        }
        return start
    }

    private fun urgentLow(episodeStart: Long, last: GV) = Alert(
        kind = BgAlert.URGENT_LOW,
        key = KEY_LOW,
        severity = AlertSeverity.CRITICAL,
        title = BgAlert.URGENT_LOW.title,
        text = InterfacesStrings.bg_alarm_text.withArgs(profileUtil.fromMgdlToStringWithUnits(last.value)),
        raisedAt = episodeStart,
        lifetime = AlertLifetime.CONDITION,
        silenceMinutes = URGENT_LOW_SILENCE_MINUTES,
        breakThroughDnd = true
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

        /** Long enough for the longest "how long it must last" of a rule, and for the episode to start in it. */
        private const val LOOK_BACK_MS = 3 * 60 * 60_000L

        /** A newer reading than this is from the future: not used, neither to raise nor to end an alarm. */
        private const val FUTURE_TOLERANCE_MS = 60_000L

        /** Readings older than this are old data: the stale data alarm's job, not a level alarm's. */
        private const val FRESH_MS = 15 * 60_000L

        /** A gap this long between two readings ends an episode. */
        private const val MAX_GAP_MS = 15 * 60_000L

        /** An alarm ends this far above its threshold, so readings around it do not ring again and again. */
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
