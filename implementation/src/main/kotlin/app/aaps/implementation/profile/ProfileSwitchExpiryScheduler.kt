package app.aaps.implementation.profile

import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires [EventProfileChangeRequested] at the exact end of an active *temporary* ProfileSwitch.
 *
 * Why this exists: a temporary ProfileSwitch produces no DB write when it naturally expires — the
 * underlying base profile simply becomes active again at `timestamp + duration`, but the
 * EffectiveProfileSwitch query selects the latest EPS by timestamp and ignores `originalEnd`, so
 * the base profile only resumes once a *new* base-profile EPS is written. The only trigger for that
 * on natural expiry was [app.aaps.implementation.receivers.KeepAliveWorker], which runs ~every
 * 5 min — so the revert could lag by up to ~5 min. This scheduler removes that latency by nudging
 * the existing profile-change collector (in CommandQueueImplementation) at the precise end time.
 *
 * Lightweight on purpose: no WorkManager. A suspended [delay] is essentially free, the job is
 * re-armed on every PS change, and on app restart [start] re-arms from the current PS. KeepAlive
 * remains the backstop for the process-dead / Doze tail.
 *
 * Master-only: gated by `!config.AAPSCLIENT` to match CommandQueueImplementation.onProfileChanged,
 * which creates the EffectiveProfileSwitch on master while clients sync it over NS.
 */
@Singleton
class ProfileSwitchExpiryScheduler @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private var started = false
    private var expiryJob: Job? = null

    fun start() {
        if (started) return
        started = true
        if (config.AAPSCLIENT) {
            aapsLogger.debug(LTag.PROFILE, "ProfileSwitchExpiryScheduler: AAPSCLIENT, not scheduling")
            return
        }
        appScope.launch {
            reschedule()
            persistenceLayer.observeChanges(PS::class.java).collect { reschedule() }
        }
    }

    internal suspend fun reschedule() {
        expiryJob?.cancel()
        val now = dateUtil.now()
        // Returns the temporary PS only while it is still unexpired; a permanent PS has duration 0.
        val ps = persistenceLayer.getProfileSwitchActiveAt(now) ?: return
        if (ps.duration <= 0L) return
        // Overflow guard: timestamp + duration can wrap for a pathological duration.
        val end = ps.timestamp + ps.duration
        if (end < ps.timestamp) return
        val delayMs = end - now
        if (delayMs <= 0) return // already expired → KeepAlive / next PS change handles it
        aapsLogger.debug(LTag.PROFILE, "ProfileSwitchExpiryScheduler: scheduling profile change in ${delayMs}ms")
        expiryJob = appScope.launch {
            delay(delayMs)
            aapsLogger.debug(LTag.PROFILE, "ProfileSwitchExpiryScheduler: temporary ProfileSwitch expired, requesting profile change")
            rxBus.send(EventProfileChangeRequested())
        }
    }
}
