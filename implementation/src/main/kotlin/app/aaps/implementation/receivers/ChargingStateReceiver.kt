package app.aaps.implementation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.receivers.ReceiverStatusStore.ChargingStatus
import app.aaps.core.objects.workflow.MetroMemberInjector
import dev.zacsweers.metro.Inject

/**
 * Converted off dagger.android to Metro, as the proof that `@ContributesAndroidInjector` can be
 * replaced.
 *
 * `DaggerBroadcastReceiver` used to call `AndroidInjection.inject(this, context)` from its `onReceive`.
 * The call below does the same job through [MetroMemberInjector], which the `Application` implements.
 * The difference is invisible here and important one layer down: Metro built the injector at compile
 * time, so a missing binding for this class is a build error rather than a crash when the battery
 * state next changes.
 */
class ChargingStateReceiver : BroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as MetroMemberInjector).injectMembers(this)
        grabChargingState(context)
    }

    @VisibleForTesting
    fun grabChargingState(context: Context): ChargingStatus {
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

        return ChargingStatus(isCharging, batteryLevel).also { receiverStatusStore.setChargingStatus(it) }
    }
}
