package app.aaps.pump.omnipod.omnipod5.bledriver.pod.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The expiry alerts of a pod near its end and past it. An alert whose time is over cannot be set: its timer would be
 * a negative number. It must go to the pod as off, with the other alerts of the same command still set.
 */
class O5AlertConfigBuilderTest {

    private val podStateManager: O5PodStateManager = mock()
    private val preferences: Preferences = mock()

    /** What the pod gets for one alert: whether it is on, and its timer in minutes. */
    private class SentAlert(val on: Boolean, val minutes: Int)

    /**
     * The alerts for a pod with [hoursLeft] until its end, with the reminder 4 hours and the alert 8 hours before it.
     * A pod runs 80 hours and its end is 8 hours before that, so it is the minutes since the activation that set it.
     */
    private fun alertsOfAPodWith(hoursLeft: Int): Map<Int, SentAlert> {
        whenever(podStateManager.podLifeInHours).thenReturn(80)
        whenever(podStateManager.minutesSinceActivation).thenReturn(((72 - hoursLeft) * 60).toShort())
        whenever(podStateManager.lastStatusResponseReceived).thenReturn(System.currentTimeMillis())
        whenever(preferences.get(OmnipodBooleanPreferenceKey.ExpirationReminder)).thenReturn(true)
        whenever(preferences.get(OmnipodIntPreferenceKey.ExpirationReminderHours)).thenReturn(4)
        whenever(preferences.get(OmnipodBooleanPreferenceKey.ExpirationAlarm)).thenReturn(true)
        whenever(preferences.get(OmnipodIntPreferenceKey.ExpirationAlarmHours)).thenReturn(8)

        return buildO5ExpirationAlerts(podStateManager, preferences, mock<AAPSLogger>()).map { it.encoded }.associate { bytes ->
            val first = bytes[0].toInt() and 0xff
            val minutes = (((bytes[2].toInt() and 0xff) shl 8) or (bytes[3].toInt() and 0xff)).toShort().toInt()
            (first shr 4) to SentAlert(first and 0x08 != 0, minutes)
        }
    }

    private fun Map<Int, SentAlert>.of(type: AlertType) = getValue(type.index.toInt())

    @Test
    fun `a pod with days left gets every alert with a time ahead`() {
        val alerts = alertsOfAPodWith(hoursLeft = 60)

        assertThat(alerts.values.all { it.on && it.minutes > 0 }).isTrue()
    }

    @Test
    fun `a reminder whose time is over goes to the pod as off and the alert is still set`() {
        val alerts = alertsOfAPodWith(hoursLeft = 3)

        assertThat(alerts.of(AlertType.USER_SET_EXPIRATION).on).isFalse()
        assertThat(alerts.of(AlertType.USER_SET_EXPIRATION).minutes).isEqualTo(0)
        assertThat(alerts.of(AlertType.EXPIRATION).on).isTrue()
        assertThat(alerts.of(AlertType.EXPIRATION).minutes).isGreaterThan(0)
    }

    /** In the 8 hours a pod runs after its end the alert at its end was sent as a negative timer. */
    @Test
    fun `a pod past its end gets no negative timer`() {
        val alerts = alertsOfAPodWith(hoursLeft = -1)

        assertThat(alerts.of(AlertType.EXPIRATION).on).isFalse()
        assertThat(alerts.of(AlertType.EXPIRATION).minutes).isEqualTo(0)
        assertThat(alerts.of(AlertType.EXPIRATION_IMMINENT).on).isTrue()
        assertThat(alerts.of(AlertType.EXPIRATION_IMMINENT).minutes).isGreaterThan(0)
        assertThat(alerts.values.none { it.minutes < 0 }).isTrue()
    }
}
