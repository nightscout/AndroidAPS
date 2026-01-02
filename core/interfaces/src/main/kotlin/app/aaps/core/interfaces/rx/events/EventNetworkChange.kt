package app.aaps.core.interfaces.rx.events

/**
 * Fired when the network connection state changes.
 *
 * @param mobileConnected True if the device is connected to a mobile network.
 * @param wifiConnected True if the device is connected to a Wi-Fi network.
 * @param vpnConnected True if the device is connected to a VPN.
 * @param ssid The SSID of the Wi-Fi network, or an empty string if not connected to Wi-Fi.
 * @param roaming True if the device is roaming.
 * @param metered True if the network connection is metered.
 */
class EventNetworkChange(
    var mobileConnected: Boolean = false,
    var wifiConnected: Boolean = false,
    var vpnConnected: Boolean = false,
    var ssid: String = "",
    var roaming: Boolean = false,
    var metered: Boolean = false
) : Event() {

    /**
     * True if the device has any network connection (mobile or Wi-Fi).
     */
    val isAnyConnection: Boolean
        get() = mobileConnected || wifiConnected
}