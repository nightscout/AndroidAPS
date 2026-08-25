package app.aaps.receivers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import app.aaps.persistentNotification.DummyServiceHelper
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import javax.inject.Inject

class AutoStartReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var dummyServiceHelper: DummyServiceHelper

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        processIntent(context, intent)
    }

    @VisibleForTesting
    fun processIntent(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED)
            dummyServiceHelper.startService(context)
    }
}