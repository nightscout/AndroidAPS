package info.nightscout.androidaps.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import info.nightscout.androidaps.plugins.general.persistentNotification.DummyService

class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(Intent(context, DummyService::class.java))
            else
                context.startService(Intent(context, DummyService::class.java))
        }
    }
}