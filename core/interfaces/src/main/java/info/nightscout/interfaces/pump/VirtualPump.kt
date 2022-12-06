package info.nightscout.interfaces.pump

interface VirtualPump {
    var fakeDataDetected: Boolean
    fun isEnabled(): Boolean
}