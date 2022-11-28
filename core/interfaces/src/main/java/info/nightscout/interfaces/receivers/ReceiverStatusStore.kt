package info.nightscout.interfaces.receivers

import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNetworkChange

interface ReceiverStatusStore {

    var lastNetworkEvent: EventNetworkChange?

    val isWifiConnected: Boolean

    val isConnected: Boolean
    fun updateNetworkStatus()
    var lastChargingEvent: EventChargingState?

    val isCharging: Boolean

    val batteryLevel: Int
    fun broadcastChargingState()
}