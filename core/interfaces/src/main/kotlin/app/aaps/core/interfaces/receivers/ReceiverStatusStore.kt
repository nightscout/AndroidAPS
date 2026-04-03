package app.aaps.core.interfaces.receivers

import kotlinx.coroutines.flow.StateFlow

interface ReceiverStatusStore {

    val networkStatusFlow: StateFlow<NetworkStatus?>
    fun setNetworkStatus(event: NetworkStatus)

    val isWifiConnected: Boolean
    val isKnownNetworkStatus: Boolean
    val isConnected: Boolean
    fun updateNetworkStatus()

    val chargingStatusFlow: StateFlow<ChargingStatus?>
    fun setChargingStatus(event: ChargingStatus)

    val isCharging: Boolean
    val batteryLevel: Int

    data class ChargingStatus(val isCharging: Boolean, val batteryLevel: Int)

    data class NetworkStatus(
        val mobileConnected: Boolean = false,
        val wifiConnected: Boolean = false,
        val vpnConnected: Boolean = false,
        val ssid: String = "",
        val roaming: Boolean = false,
        val metered: Boolean = false
    ) {

        val isAnyConnection: Boolean
            get() = mobileConnected || wifiConnected
    }
}
