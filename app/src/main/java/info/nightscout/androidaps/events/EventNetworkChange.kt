package info.nightscout.androidaps.events

import info.nightscout.androidaps.utils.StringUtils

class EventNetworkChange : Event() {

    var mobileConnected = false
    var wifiConnected = false

    var ssid = ""
    var roaming = false

    fun connectedSsid(): String {
        return StringUtils.removeSurroundingQuotes(ssid)
    }
}
