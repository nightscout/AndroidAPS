package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.BundleLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.TimeChangeType
import java.util.*
import javax.inject.Inject

class TimeDateOrTZChangeReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePluginProvider
    val gson: Gson = Gson()

    private var isDST = false

    init {
        isDST = calculateDST()
    }

    private fun calculateDST(): Boolean {
        val timeZone = TimeZone.getDefault()
        val nowDate = Date()
        return if (timeZone.useDaylightTime()) {
            timeZone.inDaylightTime(nowDate)
        } else {
            false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val activePump: PumpInterface = activePlugin.activePump

        aapsLogger.debug(LTag.PUMP, "TimeDateOrTZChangeReceiver::Date, Time and/or TimeZone changed. [action={}]", action)
        aapsLogger.debug(LTag.PUMP, "TimeDateOrTZChangeReceiver::Intent::{}", BundleLogger.log(intent.extras))

        when {
            action == null                           -> {
                aapsLogger.error(LTag.PUMP, "TimeDateOrTZChangeReceiver::Action is null. Exiting.")
            }

            Intent.ACTION_TIMEZONE_CHANGED == action -> {
                aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::Timezone changed. Notifying pump driver.")
                activePump.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged)
            }

            Intent.ACTION_TIME_CHANGED == action     -> {
                val currentDst = calculateDST()
                if (currentDst == isDST) {
                    aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::Time changed (manual). Notifying pump driver.")
                    activePump.timezoneOrDSTChanged(TimeChangeType.TimeChanged)
                } else {
                    if (currentDst) {
                        aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::DST started. Notifying pump driver.")
                        activePump.timezoneOrDSTChanged(TimeChangeType.DST_Started)
                    } else {
                        aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::DST ended. Notifying pump driver.")
                        activePump.timezoneOrDSTChanged(TimeChangeType.DST_Ended)
                    }
                }
                isDST = currentDst
            }

            else                                     -> {
                aapsLogger.error(LTag.PUMP, "TimeDateOrTZChangeReceiver::Unknown action received [name={}]. Exiting.", action)
            }
        }
    }
}
