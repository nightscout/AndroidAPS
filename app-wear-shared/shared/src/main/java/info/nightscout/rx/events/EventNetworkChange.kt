package info.nightscout.rx.events

class EventNetworkChange(
    var mobileConnected: Boolean = false,
    var wifiConnected: Boolean = false,
    var vpnConnected: Boolean = false,
    var ssid: String = "",
    var roaming: Boolean = false,
    var metered: Boolean = false
) : Event()