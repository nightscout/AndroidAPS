package app.aaps.implementation.pump.alerts

import android.content.Context
import android.content.Intent
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * The silence of a pump alert has ended: the exact alarm set by [AndroidPumpAlertPlatform.snoozed] wakes the phone, and
 * every alert whose silence is over rings again. A manifest receiver is not injected by the system, so it asks the
 * graph itself, as `AlarmMuteReceiver` does.
 */
class PumpAlertWakeReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var pumpAlerts: PumpAlertsImpl

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        pumpAlerts.checkSilences()
    }
}
