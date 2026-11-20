package app.aaps.implementation.androidNotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * Handles "Acknowledge" on a CGM alarm notification: every CGM alarm that is going off stops and
 * stays quiet for its repeat time, through [LocalAlertUtils.acknowledgeCgmAlarms].
 *
 * A manifest [BroadcastReceiver] is not injected by the system, so it asks the graph itself, the
 * same way as [HypoTreatedReceiver].
 */
class CgmAlarmAcknowledgeReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var localAlertUtils: LocalAlertUtils

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        localAlertUtils.acknowledgeCgmAlarms()
    }
}
