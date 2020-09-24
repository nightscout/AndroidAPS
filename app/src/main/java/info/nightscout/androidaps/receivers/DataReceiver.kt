package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import androidx.legacy.content.*
import dagger.android.AndroidInjection
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.services.DataService
import javax.inject.Inject

// We are not ready to switch to JobScheduler
@Suppress("DEPRECATION")
open class DataReceiver : WakefulBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)
        aapsLogger.debug(LTag.DATASERVICE, "onReceive $intent")
        startWakefulService(context, Intent(context, DataService::class.java)
            .setAction(intent.action)
            .putExtras(intent))
    }
}