package info.nightscout.androidaps.events

class EventNetworkChange : Event() {

    var mobileConnected = false
    var wifiConnected = false
    var vpnConnected = false

    var ssid = ""
    var roaming = false
    var metered = false
}
