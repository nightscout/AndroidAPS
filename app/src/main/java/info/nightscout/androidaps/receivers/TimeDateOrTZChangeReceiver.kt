package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
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
        val activePump: PumpInterface = activePlugin.activePump
        aapsLogger.debug(LTag.PUMP, "Date, Time and/or TimeZone changed.")
        if (action != null) {
            aapsLogger.debug(LTag.PUMP, "Date, Time and/or TimeZone changed. Notifying pump driver.")
            activePump.timeDateOrTimeZoneChanged()
        }
    }
}