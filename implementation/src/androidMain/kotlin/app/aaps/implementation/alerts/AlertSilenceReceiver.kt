package app.aaps.implementation.alerts

import android.content.Context
import android.content.Intent
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * A silence button on the notification of a WARNING alert that lasts (5, 15 or 30 min,
 * [app.aaps.core.interfaces.alerts.Alerts.NOTIFICATION_SILENCE_MINUTES], `_docs/ALARMS.md`, step 6). A manifest
 * receiver is not injected by the system, so it asks the graph itself, as [AlertWakeReceiver] does.
 */
class AlertSilenceReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var platform: AndroidAlertPlatform

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0).takeIf { it > 0 } ?: return
        platform.silenceFromNotification(source, key, minutes)
    }

    companion object {

        /** One action per length: the extras do not tell two pending intents apart, the action does. */
        fun actionOf(minutes: Int) = "app.aaps.implementation.alerts.SILENCE_$minutes"

        const val EXTRA_SOURCE = "source"
        const val EXTRA_KEY = "key"
        const val EXTRA_MINUTES = "minutes"
    }
}
