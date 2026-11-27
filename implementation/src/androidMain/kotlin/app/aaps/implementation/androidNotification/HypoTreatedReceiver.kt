package app.aaps.implementation.androidNotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * Handles the "Hypo treated" action on the low glucose alarm notification: it holds the alarm for
 * the time carbs need to work and clears the current alarm, through
 * [LocalAlertUtils.snoozeHypoAfterTreatment].
 *
 * The action is on the system notification because that is the only thing the user sees during a
 * real hypo: the phone is usually locked and the app is not open.
 *
 * A manifest [BroadcastReceiver] is not injected by the system, so it asks the graph itself, the
 * same way as [AlarmMuteReceiver].
 */
class HypoTreatedReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var localAlertUtils: LocalAlertUtils

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        localAlertUtils.snoozeHypoAfterTreatment()
    }
}
