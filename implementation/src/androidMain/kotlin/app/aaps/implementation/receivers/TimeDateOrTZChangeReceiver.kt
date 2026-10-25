package app.aaps.implementation.receivers

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTimeZoneChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.TimeZoneChange
import app.aaps.core.interfaces.utils.readTimeZoneChanges
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import app.aaps.core.utils.receivers.BundleLogger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.TimeZone

class TimeDateOrTZChangeReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil

    private var isDST = calculateDST()
    private var previousTimeZone = TimeZone.getDefault()

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
        processIntent(intent)
    }

    @VisibleForTesting
    fun processIntent(intent: Intent) {
        val action = intent.action
        val activePump: Pump = activePlugin.activePump

        aapsLogger.debug(LTag.PUMP, "TimeDateOrTZChangeReceiver::Date, Time and/or TimeZone changed. [action={}]", action)
        aapsLogger.debug(LTag.PUMP, "TimeDateOrTZChangeReceiver::Intent::{}", BundleLogger.log(intent.extras))

        when {
            action == null                           -> {
                aapsLogger.error(LTag.PUMP, "TimeDateOrTZChangeReceiver::Action is null. Exiting.")
            }

            Intent.ACTION_TIMEZONE_CHANGED == action -> {
                val zone = TimeZone.getDefault()
                if (zone.id != previousTimeZone.id) {
                    val now = dateUtil.now()
                    val change = TimeZoneChange(now, previousTimeZone.getOffset(now) / 1000, zone.getOffset(now) / 1000)
                    val history = preferences.readTimeZoneChanges(now, aapsLogger)
                    preferences.put(StringNonKey.TimeZoneChangeHistory, Json.encodeToString(history + change))
                    previousTimeZone = zone
                }
                aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::Timezone changed. Notifying pump driver.")
                appScope.launch { activePump.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }
                // The IOB calculation reads the basal profile in the current time zone, so its cached
                // values for the past are stale now.
                rxBus.send(EventTimeZoneChanged())
            }

            Intent.ACTION_TIME_CHANGED == action     -> {
                val currentDst = calculateDST()
                if (currentDst == isDST) {
                    aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::Time changed (manual). Notifying pump driver.")
                    appScope.launch { activePump.timezoneOrDSTChanged(TimeChangeType.TimeChanged) }
                } else {
                    if (currentDst) {
                        aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::DST started. Notifying pump driver.")
                        appScope.launch { activePump.timezoneOrDSTChanged(TimeChangeType.DSTStarted) }
                    } else {
                        aapsLogger.info(LTag.PUMP, "TimeDateOrTZChangeReceiver::DST ended. Notifying pump driver.")
                        appScope.launch { activePump.timezoneOrDSTChanged(TimeChangeType.DSTEnded) }
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
