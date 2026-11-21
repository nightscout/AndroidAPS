package app.aaps.implementation.alerts

import android.content.Context
import android.content.Intent
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * The silence of a pump alert has ended: the exact alarm set by [AndroidAlertPlatform.snoozed] wakes the phone, and
 * every alert whose silence is over rings again. A manifest receiver is not injected by the system, so it asks the
 * graph itself, as `AlarmMuteReceiver` does.
 */
class AlertWakeReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var alerts: AlertsImpl

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        alerts.checkSilences()
    }
}
