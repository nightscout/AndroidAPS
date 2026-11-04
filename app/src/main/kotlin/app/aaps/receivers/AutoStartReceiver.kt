package app.aaps.receivers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import app.aaps.persistentNotification.DummyServiceHelper
import app.aaps.settingsstore.AndroidStorage
import dev.zacsweers.metro.Inject

class AutoStartReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var dummyServiceHelper: DummyServiceHelper

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        processIntent(context, intent)
    }

    /**
     * Starts DummyService after a boot and after an update of AAPS.
     *
     * After an update nothing else would start the process until a CGM broadcast, a job or the user
     * opens the app, and the loop does not run until then. Both broadcasts are on Android's list of
     * moments when an app may start a foreground service from the background.
     *
     * Not when the settings cannot be opened: AAPS is not started then, and the service's notification
     * would say it is running.
     */
    @VisibleForTesting
    fun processIntent(context: Context, intent: Intent) {
        if (AndroidStorage.failure != null) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            dummyServiceHelper.startService(context)
    }
}