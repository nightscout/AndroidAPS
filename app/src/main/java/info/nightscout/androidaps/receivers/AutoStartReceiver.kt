package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.plugins.general.persistentNotification.DummyServiceHelper
import javax.inject.Inject

class AutoStartReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var dummyServiceHelper: DummyServiceHelper

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED)
            dummyServiceHelper.startService(context)
    }
}