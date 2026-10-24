package app.aaps.core.interfaces.pump

interface VirtualPump {

    var fakeDataDetected: Boolean
    fun isEnabled(): Boolean
}