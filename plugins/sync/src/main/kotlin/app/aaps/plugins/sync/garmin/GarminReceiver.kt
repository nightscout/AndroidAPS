package app.aaps.plugins.sync.garmin

/**
 * Callback interface for a @see ConnectIqClient.
 */
interface GarminReceiver {
    /**
     * Notifies that the client is ready, i.e. the app client as bound to the Garmin
     * Android app.
     */
    fun onConnect(client: GarminClient)
    fun onDisconnect(client: GarminClient)

    /**
     * Notifies that a device is connected. This will be called for all connected devices
     * initially.
     */
    fun onConnectDevice(client: GarminClient, deviceId: Long, deviceName: String)
    fun onDisconnectDevice(client: GarminClient, deviceId: Long)

    /**
     * Provides application info after a call to
     * {@link ConnectIqClient#retrieveApplicationInfo retrieveApplicationInfo}.
     */
    fun onApplicationInfo(device: GarminDevice, appId: String, isInstalled: Boolean)

    /**
     * Delivers received device app messages.
     */
    fun onReceiveMessage(client: GarminClient, deviceId: Long, appId: String, data: ByteArray)

    /**
     * Delivers status of @see ConnectIqClient#sendMessage requests.
     */
    fun onSendMessage(client: GarminClient, deviceId: Long, appId: String, errorMessage: String?)
}