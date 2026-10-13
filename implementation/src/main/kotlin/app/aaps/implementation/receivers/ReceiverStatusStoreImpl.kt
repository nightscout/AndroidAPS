package app.aaps.implementation.receivers

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventChargingState
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiverStatusStoreImpl @Inject constructor(val context: Context, val rxBus: RxBus) : ReceiverStatusStore {

    override var lastNetworkEvent: EventNetworkChange? = null

    override val isWifiConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected == true

    override val isKnownNetworkStatus: Boolean
        get() = lastNetworkEvent != null

    override val isConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected == true || lastNetworkEvent?.mobileConnected == true

    override fun updateNetworkStatus() {
        context.sendBroadcast(Intent(context, NetworkChangeReceiver::class.java))
    }

    override var lastChargingEvent: EventChargingState? = null

    override val isCharging: Boolean
        get() = lastChargingEvent?.isCharging == true

    override val batteryLevel: Int
        get() = lastChargingEvent?.batteryLevel ?: 0

    override fun broadcastChargingState() {
        lastChargingEvent?.let { rxBus.send(it) }
    }
}