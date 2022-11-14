package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNetworkChange
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class ReceiverStatusStore @Inject constructor(val context: Context, val rxBus: RxBus) {

    var lastNetworkEvent: EventNetworkChange? = null

    val isWifiConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false

    val isConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false || lastNetworkEvent?.mobileConnected ?: false

    fun updateNetworkStatus() {
        context.sendBroadcast(Intent(context, NetworkChangeReceiver::class.java))
    }

    var lastChargingEvent: EventChargingState? = null

    val isCharging: Boolean
        get() = lastChargingEvent?.isCharging ?: false

    val batteryLevel: Int
        get() = lastChargingEvent?.batterLevel ?: 0

    fun broadcastChargingState() {
        lastChargingEvent?.let { rxBus.send(it) }
    }
}