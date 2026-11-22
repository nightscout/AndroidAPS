package app.aaps.implementation.alerts

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive
import platform.UserNotifications.UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The missed-readings alarm, booked with iOS in advance, so it rings even when the app cannot run.
 *
 * The alarm itself is `LocalAlertUtils.checkStaleBGAlert`, which the keep-alive pass runs from inside
 * the app. On Android the app keeps running, so that is enough. On iOS it is not: a suspended or ended
 * app runs no code at all, and when the readings stop, nothing is left to wake it. The user would be
 * told nothing, which is exactly the case this alarm exists for.
 *
 * So after every change - a new reading, a change of the setting, a snooze - one local notification is
 * booked for the moment the in-app alarm would ring ([ringAt]), with the same text. The next booking
 * replaces it, so it only rings when no reading came in time. iOS shows it from the system, whether
 * the app runs, is suspended or was ended. It works for every glucose source, Nightscout included.
 *
 * Limits:
 *
 * - While the app does run, the in-app alarm rings as well, so the user may see the message twice.
 *   Twice is better than never.
 * - It follows the user's notification settings for the app, like every iOS notification. The
 *   permission sheet shows when notifications are refused.
 *
 * Started by the app (`startAapsApp`), not by the graph: `UNUserNotificationCenter` needs an app bundle,
 * which a test binary does not have.
 */
@SingleIn(AppScope::class)
@Inject
class IosMissedReadingAlarm(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val rh: TextResolver,
    private val clock: Clock,
    private val scope: CoroutineScope
) {

    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }

    fun start() {
        scope.launch {
            // Not before the start sequence has run the database migrations. It gives up after 30 s,
            // so it is asked again: a slow start must delay the alarm, not lose it.
            while (!config.awaitInitialized()) aapsLogger.debug(LTag.NOTIFICATION, "Missed readings alarm waits for the app start")
            merge(
                persistenceLayer.observeChanges(GV::class).map { },
                preferences.observe(BooleanKey.AlertMissedBgReading).map { },
                preferences.observe(IntKey.AlertsStaleDataThreshold).map { },
                // A snooze, and the in-app alarm itself, which moves this when it rings.
                preferences.observe(LocalAlertLongKey.NextMissedReadingsAlarm).map { }
            ).onStart { emit(Unit) }.collect {
                try {
                    book()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aapsLogger.error(LTag.NOTIFICATION, "Cannot book the missed readings alarm", e)
                }
            }
        }
    }

    private suspend fun book() {
        // The condition of the last booking is over either way, so its notification goes too.
        val ids = listOf(IDENTIFIER)
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)

        val nowMs = clock.now().toEpochMilliseconds()
        val ringAtMs = ringAt(
            lastReadingMs = persistenceLayer.getLastGlucoseValue()?.timestamp,
            nowMs = nowMs,
            // `get`, not the observed value: see `KeepScreenOnEffect` in the iOS shell for why.
            enabled = preferences.get(BooleanKey.AlertMissedBgReading),
            thresholdMinutes = preferences.get(IntKey.AlertsStaleDataThreshold),
            snoozedUntilMs = preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)
        ) ?: return

        val content = UNMutableNotificationContent().apply {
            setTitle(rh.gs(CoreUiStrings.urgent_alarm))
            setBody(rh.gs(CoreUiStrings.missed_bg_readings))
            // The app is not running to play anything when this rings, so iOS plays the sound.
            setSound(UNNotificationSound.defaultSound())
            setInterruptionLevel(
                if (preferences.get(BooleanKey.AlertOverrideDoNotDisturb)) UNNotificationInterruptionLevelTimeSensitive
                else UNNotificationInterruptionLevelActive
            )
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = ((ringAtMs - nowMs) / 1000.0).coerceAtLeast(MIN_DELAY_SECONDS),
            repeats = false
        )
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content, trigger)) { error ->
            if (error != null) aapsLogger.error(LTag.NOTIFICATION, "Cannot book the missed readings alarm: $error")
        }
        aapsLogger.debug(LTag.NOTIFICATION, "Missed readings alarm booked in ${(ringAtMs - nowMs) / 60_000} min")
    }

    internal companion object {

        /**
         * Not under the `aaps-` prefix: `IosSystemNotificationPlatform` treats ids it can parse under that
         * prefix as its own, and this one is not.
         */
        const val IDENTIFIER = "missed-bg-readings"

        /** iOS refuses a trigger interval that is not positive. */
        private const val MIN_DELAY_SECONDS = 1.0

        /**
         * When the alarm should ring, in epoch milliseconds, or null when nothing is to be booked.
         *
         * The same rule as `checkStaleBGAlert`: the last reading plus the threshold, and not before the
         * snooze ends. Null when the alarm is off, when there is no reading yet (the in-app alarm stays
         * quiet then too), or when the moment has already passed - then the in-app alarm is the one that
         * rings, and a notification booked for "now" would only ring at once on top of it.
         */
        fun ringAt(lastReadingMs: Long?, nowMs: Long, enabled: Boolean, thresholdMinutes: Int, snoozedUntilMs: Long): Long? {
            if (!enabled || thresholdMinutes <= 0 || lastReadingMs == null) return null
            val ringAtMs = maxOf(lastReadingMs + thresholdMinutes * 60_000L, snoozedUntilMs)
            return ringAtMs.takeIf { it > nowMs }
        }
    }
}
