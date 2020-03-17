package info.nightscout.androidaps.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject

class TimeDateOrTZChangeReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val activePump: PumpInterface = activePlugin.getActivePump()
        aapsLogger.debug(LTag.PUMP, "Date, Time and/or TimeZone changed.")
        if (action != null) {
            aapsLogger.debug(LTag.PUMP, "Date, Time and/or TimeZone changed. Notifying pump driver.")
            activePump.timeDateOrTimeZoneChanged()
        }
    }

    fun registerBroadcasts(context: Context) {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_DATE_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        context.registerReceiver(this, filter)
    }
}