package app.aaps.interfaces.pump

interface VirtualPump {

    var fakeDataDetected: Boolean
    fun isEnabled(): Boolean
}