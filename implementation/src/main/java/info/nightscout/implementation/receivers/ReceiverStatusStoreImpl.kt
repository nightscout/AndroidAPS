package info.nightscout.implementation.receivers

import android.content.Context
import android.content.Intent
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNetworkChange
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class ReceiverStatusStoreImpl @Inject constructor(val context: Context, val rxBus: RxBus) : ReceiverStatusStore {

    override var lastNetworkEvent: EventNetworkChange? = null

    override val isWifiConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false

    override val isConnected: Boolean
        get() = lastNetworkEvent?.wifiConnected ?: false || lastNetworkEvent?.mobileConnected ?: false

    override fun updateNetworkStatus() {
        context.sendBroadcast(Intent(context, NetworkChangeReceiver::class.java))
    }

    override var lastChargingEvent: EventChargingState? = null

    override val isCharging: Boolean
        get() = lastChargingEvent?.isCharging ?: false

    override val batteryLevel: Int
        get() = lastChargingEvent?.batterLevel ?: 0

    override fun broadcastChargingState() {
        lastChargingEvent?.let { rxBus.send(it) }
    }
}