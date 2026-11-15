package app.aaps.implementation.profile

import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fires [EventProfileChangeRequested] at the next moment the running profile has to change:
 *
 *  - the **end** of an active *temporary* ProfileSwitch, and
 *  - the **start** of a ProfileSwitch the user put in the future.
 *
 * Why this exists: neither moment produces a DB write of its own.
 *
 * On expiry the underlying base profile simply becomes active again at `timestamp + duration`, but the
 * EffectiveProfileSwitch query selects the latest EPS by timestamp and ignores `originalEnd`, so the base
 * profile only resumes once a *new* base-profile EPS is written.
 *
 * A future-dated switch is invisible to `getProfileSwitchActiveAt(now)` until its time comes, so the DB
 * change that inserted it is handled (correctly) as "nothing to apply yet" and nothing looks at it again.
 *
 * In both cases the only trigger was [app.aaps.implementation.receivers.KeepAliveWorker], which runs
 * ~every 5 min — so the change could lag by up to ~5 min. This scheduler removes that latency by nudging
 * the existing profile-change collector (in CommandQueueImplementation) at the precise time. KeepAlive
 * remains the backstop for the process-dead / Doze tail, and a device asleep at the scheduled moment
 * fires late rather than not at all.
 *
 * Lightweight on purpose: no WorkManager. A suspended [delay] is essentially free, the job is re-armed on
 * every PS change, and on app restart [start] re-arms from the database.
 *
 * Master-only: gated by `!config.AAPSCLIENT` to match CommandQueueImplementation.onProfileChanged,
 * which creates the EffectiveProfileSwitch on master while clients sync it over NS.
 */
@SingleIn(AppScope::class)
@Inject
class ProfileSwitchScheduler(
    private val persistenceLayer: PersistenceLayer,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    // Plain CoroutineScope, not @ApplicationScope: that qualifier is javax and cannot appear in
    // commonMain. AppCoroutineBindings.unqualifiedAppScope binds the very same scope without it.
    private val appScope: CoroutineScope
) {

    private var started = false
    private var scheduledJob: Job? = null

    fun start() {
        if (started) return
        started = true
        if (config.AAPSCLIENT) {
            aapsLogger.debug(LTag.PROFILE, "ProfileSwitchScheduler: AAPSCLIENT, not scheduling")
            return
        }
        appScope.launch {
            reschedule()
            persistenceLayer.observeChanges(PS::class).collect { reschedule() }
        }
    }

    /**
     * Arm the next moment the profile has to change. [after] is the moment that was just fired, and the next
     * target has to lie strictly beyond it: the same end time stays a candidate right after it elapses (the
     * active switch does not change in the database when it expires), so without this the hand-over below
     * would arm that identical moment again and again.
     */
    internal suspend fun reschedule(after: Long = 0L) {
        scheduledJob?.cancel()
        val now = dateUtil.now()
        val floor = maxOf(now, after)
        // The soonest of the two moments. Both are candidates at the same time: a temporary switch can be
        // running while another one is already scheduled to start before it ends.
        val next = listOfNotNull(activeSwitchEnd(now), nextSwitchStart(now)).filter { it > floor }.minOrNull() ?: return
        val delayMs = next - now
        aapsLogger.debug(LTag.PROFILE, "ProfileSwitchScheduler: scheduling profile change in ${delayMs}ms")
        scheduledJob = appScope.launch {
            delay(delayMs)
            aapsLogger.debug(LTag.PROFILE, "ProfileSwitchScheduler: profile change time reached, requesting profile change")
            rxBus.send(EventProfileChangeRequested())
            // Arm the next moment: a switch that just started may itself be temporary, and its end is only
            // armed by another reschedule. A sibling job on purpose — reschedule() cancels `scheduledJob`,
            // which is this very job, so calling it inline would cancel the call itself.
            appScope.launch { reschedule(after = next) }
        }
    }

    /** End of the active temporary switch, or null when none is active or the active one is permanent. */
    private suspend fun activeSwitchEnd(now: Long): Long? {
        // Returns the temporary PS only while it is still unexpired; a permanent PS has duration 0.
        val ps = persistenceLayer.getProfileSwitchActiveAt(now) ?: return null
        if (ps.duration <= 0L) return null
        // Overflow guard: timestamp + duration can wrap for a pathological duration.
        val end = ps.timestamp + ps.duration
        return end.takeIf { it >= ps.timestamp }
    }

    /** Start of the nearest switch the user put in the future, or null when there is none. */
    private suspend fun nextSwitchStart(now: Long): Long? =
        persistenceLayer.getProfileSwitchesFromTime(now + 1, ascending = true).firstOrNull()?.timestamp
}
