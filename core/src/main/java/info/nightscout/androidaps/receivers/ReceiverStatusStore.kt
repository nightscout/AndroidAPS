package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverStatusStore @Inject constructor(val context: Context, val rxBus: RxBusWrapper) {

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