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
        val metered: Boolean = false,
        /**
         * Whether this platform can read the network name at all.
         *
         * Not the same question as whether [ssid] is empty, and keeping them apart is the point.
         * On Android an empty name means the read failed *this time* - usually a missing location
         * permission - and the user's "only sync on these networks" list should still be obeyed, so
         * an unknown name is a miss. Off Android the name is empty because nothing can ever produce
         * one: iOS needs the Access WiFi Information entitlement, which the app does not hold, and
         * the desktop reports a link with no name. There the list can never match, and treating that
         * as a miss stopped Nightscout sync on every network with nothing on screen to say why.
         *
         * True by default, so Android keeps the behaviour it has.
         */
        val ssidReadable: Boolean = true
    ) {

        val isAnyConnection: Boolean
            get() = mobileConnected || wifiConnected
    }
}
