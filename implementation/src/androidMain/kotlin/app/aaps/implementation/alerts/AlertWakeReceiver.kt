package app.aaps.implementation.alerts

import android.content.Context
import android.content.Intent
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * An exact alarm of an alert woke the phone: the silence of an alert has ended ([AndroidAlertPlatform.snoozed]), or
 * a booked alert is due ([AndroidAlertPlatform.book], [ACTION_BOOKING]). Both are checked every time, as either may be
 * due, and each check does nothing when nothing is. A manifest receiver is not injected by the system, so it asks the
 * graph itself, as `AlarmMuteReceiver` does.
 */
class AlertWakeReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var alerts: AlertsImpl

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        alerts.checkSilences()
        alerts.fireDue()
    }

    companion object {

        /** The action of a booking's alarm, so its intent is not the same as the silence end of the same alert. */
        const val ACTION_BOOKING = "app.aaps.implementation.alerts.BOOKING"
    }
}
