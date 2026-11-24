package app.aaps.implementation.alerts

import app.aaps.core.interfaces.alerts.Alerts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The identifiers the silence buttons of a warning's notification are matched on (`_docs/ALARMS.md`, step 6).
 *
 * `IosAlertPlatform` posts through a notification centre a test binary does not have, but what decides *what a tapped
 * button means* is plain string work - and getting it wrong is silent: the user taps "Silence for 15 min" and nothing
 * happens.
 */
class IosAlertSilenceButtonsTest {

    @Test
    fun eachButtonMapsBackToItsMinutes() {
        for (minutes in Alerts.NOTIFICATION_SILENCE_MINUTES)
            assertEquals(minutes, IosAlertPlatform.silenceMinutesOf("${IosAlertPlatform.SILENCE_PREFIX}$minutes"))
    }

    /** A tap that is not one of ours is declined, so another handler can take it. */
    @Test
    fun anUnrelatedActionMatchesNothing() {
        assertNull(IosAlertPlatform.silenceMinutesOf("aaps-ignore-15"))
        assertNull(IosAlertPlatform.silenceMinutesOf("${IosAlertPlatform.SILENCE_PREFIX}7"))
    }

    /** The chime and the ring after a silence both name their alert, also a key with a dot. */
    @Test
    fun theNotificationNamesItsAlert() {
        assertEquals(AlertId("aaps", "carbs_required"), IosAlertPlatform.alertOf(IosAlertPlatform.chimeIdOf("aaps", "carbs_required")))
        assertEquals(AlertId("eversense", "alarm.VERY_LOW_BATTERY"), IosAlertPlatform.alertOf(IosAlertPlatform.snoozeIdOf("eversense", "alarm.VERY_LOW_BATTERY")))
    }

    @Test
    fun anotherNotificationNamesNoAlert() {
        assertNull(IosAlertPlatform.alertOf("aaps-loop"))
        assertNull(IosAlertPlatform.alertOf("${IosAlertPlatform.BOOKING_PREFIX}aaps/eat_reminder"))
    }
}
