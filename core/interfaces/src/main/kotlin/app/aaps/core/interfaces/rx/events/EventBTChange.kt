package app.aaps.core.interfaces.rx.events

/**
 * Fired when a Bluetooth device connection state changes.
 *
 * @param state The new connection state.
 * @param deviceName The name of the device, or null if not available.
 * @param deviceAddress The address of the device, or null if not available.
 */
class EventBTChange(val state: Change, val deviceName: String?, @Suppress("unused") val deviceAddress: String? = null) : Event() {

    /**
     * Represents the connection state of a Bluetooth device.
     */
    enum class Change {

        /**
         * The device has connected.
         */
        CONNECT,

        /**
         * The device has disconnected.
         */
        DISCONNECT
    }
}