package app.aaps.implementation.alerts

import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.NightscoutAlert
import app.aaps.core.interfaces.alerts.NightscoutAnnouncements
import app.aaps.core.keys.interfaces.TextRef
import dev.zacsweers.metro.Inject

/**
 * A careportal announcement as a WARNING alert (`_docs/ALARMS.md`, step 3). Each one is its own alert, keyed by its
 * time, so a second one does not hide the first.
 */
@Inject
class NightscoutAnnouncementsImpl(private val alerts: Alerts) : NightscoutAnnouncements {

    override fun announce(timestamp: Long, note: String) = alerts.raise(
        Alert(
            kind = NightscoutAlert.ANNOUNCEMENT,
            key = "announcement.$timestamp",
            severity = AlertSeverity.WARNING,
            title = NightscoutAlert.ANNOUNCEMENT.title,
            text = TextRef.Literal(note),
            raisedAt = timestamp,
            lifetime = AlertLifetime.EVENT
        )
    )
}
