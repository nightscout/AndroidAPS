package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.data.time.T
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * The housekeeping that has to happen every few minutes whatever the app is running on.
 *
 * All of this lived inside `KeepAliveWorker`, which is an Android WorkManager worker, so it was the
 * only thing that ran it. The bodies were already in `commonMain` - `LocalAlertUtils`,
 * `PersistenceLayer`, `Maintenance` are all shared interfaces - so the code compiled for iOS and
 * desktop and was entered on neither. Shared code with a single platform-specific caller looks
 * finished and is not; that is the shape this class exists to remove.
 *
 * What it cost, concretely: [BooleanKey.AlertMissedBgReading] is drawn and searchable on every
 * platform, and off Android it did nothing, so a follower whose CGM stopped was never told. The
 * local database also grew without bound, because nothing trimmed it.
 *
 * ## What is here and what is not
 *
 * Only the platform-neutral work. `KeepAliveWorker` keeps the rest, and it belongs there: the
 * WorkManager self-rescheduling, the worker-database telemetry, and the pump and APS checks, which
 * are gated on `config.APS` and so are no-ops on the two shells that hardcode it false.
 *
 * ## Who drives it
 *
 * The trigger stays per platform, because that part genuinely differs: Android has WorkManager and a
 * wake lock, and the other two do not. Android calls [runOnce] from `KeepAliveWorker` on its existing
 * schedule; iOS and desktop call [start] and get a coroutine ticker. What must NOT differ again is
 * the work itself, which is why it is one method rather than three call sites.
 */
@SingleIn(AppScope::class)
class PeriodicMaintenance @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val localAlertUtils: LocalAlertUtils,
    private val persistenceLayer: PersistenceLayer,
    private val maintenance: Maintenance,
    private val preferences: Preferences,
    private val dateUtil: DateUtil
) {

    /**
     * One pass. Safe to call from any platform and at any interval - every step decides for itself
     * whether it is due, so calling more often than needed does no extra work.
     */
    suspend fun runOnce() {
        localAlertUtils.shortenSnoozeInterval()
        localAlertUtils.checkStaleBGAlert()
        maintenance.deleteLogs(KEEP_LOG_FILES)
        cleanupDatabase()
    }

    /**
     * Runs [runOnce] every [INTERVAL] on [scope], for the platforms with no WorkManager.
     *
     * Deliberately not idempotent-guarded here: the callers are shell startup paths that run once.
     * A failure is logged and the loop continues - one bad pass must not stop the missed-reading
     * alarm for the rest of the session.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                try {
                    runOnce()
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Periodic maintenance pass failed", e)
                }
                delay(INTERVAL)
            }
        }
    }

    /** Keep six months of history, trimmed once a day. */
    private suspend fun cleanupDatabase() {
        val lastRun = preferences.get(LongNonKey.LastCleanupRun)
        if (lastRun < dateUtil.now() - T.days(1).msecs()) {
            val result = persistenceLayer.cleanupDatabase(KEEP_DAYS, deleteTrackedChanges = false)
            aapsLogger.debug(LTag.CORE, "Cleanup result: $result")
            preferences.put(LongNonKey.LastCleanupRun, dateUtil.now())
        }
    }

    companion object {

        /** Matches the interval `KeepAliveWorker` is scheduled at on Android. */
        val INTERVAL = 5.minutes
        const val KEEP_DAYS = 6L * 31
        const val KEEP_LOG_FILES = 30
    }
}
