package app.aaps.interfaces.receivers

import app.aaps.interfaces.rx.events.EventChargingState
import app.aaps.interfaces.rx.events.EventNetworkChange

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