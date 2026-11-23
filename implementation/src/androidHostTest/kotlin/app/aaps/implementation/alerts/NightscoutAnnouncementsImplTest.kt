package app.aaps.implementation.alerts

import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.NightscoutAlert
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class NightscoutAnnouncementsImplTest {

    private val alerts: Alerts = mock()
    private val sut = NightscoutAnnouncementsImpl(alerts)

    @Test
    fun `an announcement is a WARNING alert from Nightscout that the user ends`() {
        sut.announce(1000L, "Dinner ready")

        val captor = argumentCaptor<Alert>()
        verify(alerts).raise(captor.capture())
        val alert = captor.firstValue
        assertThat(alert.kind).isEqualTo(NightscoutAlert.ANNOUNCEMENT)
        assertThat(alert.source).isEqualTo(NightscoutAlert.SOURCE)
        assertThat(alert.origin).isEqualTo(AlertOrigin.NIGHTSCOUT)
        assertThat(alert.severity).isEqualTo(AlertSeverity.WARNING)
        assertThat(alert.lifetime).isEqualTo(AlertLifetime.EVENT)
        assertThat(alert.raisedAt).isEqualTo(1000L)
        assertThat(alert.text).isEqualTo(TextRef.Literal("Dinner ready"))
    }

    /** Keyed by its time: a second announcement does not replace the first. */
    @Test
    fun `two announcements are two alerts`() {
        sut.announce(1000L, "a")
        sut.announce(2000L, "b")

        val captor = argumentCaptor<Alert>()
        verify(alerts, times(2)).raise(captor.capture())
        assertThat(captor.allValues.map { it.key }.distinct()).hasSize(2)
    }
}
