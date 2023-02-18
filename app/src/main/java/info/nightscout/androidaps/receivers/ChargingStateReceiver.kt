package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class ChargingStateReceiver : DaggerBroadcastReceiver() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        rxBus.send(grabChargingState(context))
        aapsLogger.debug(
            LTag.CORE, receiverStatusStore.lastChargingEvent?.toString()
            ?: "Unknown charging state")
    }

    private fun grabChargingState(context: Context): EventChargingState {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Level
        var batteryLevel = 0
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        if (level != -1 && scale != -1)
            batteryLevel = (level.toFloat() / scale.toFloat() * 100.0f).toInt()
        // Plugged
        val isCharging: Boolean =
            plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
                plugged == BatteryManager.BATTERY_PLUGGED_DOCK

        return EventChargingState(isCharging, batteryLevel).also { receiverStatusStore.lastChargingEvent = it }
    }
}