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
     * Delivers received device app messages.
     */
    fun onReceiveMessage(client: GarminClient, deviceId: Long, appId: String, data: ByteArray)

    /**
     * Delivers status of @see ConnectIqClient#sendMessage requests.
     */
    fun onSendMessage(client: GarminClient, deviceId: Long, appId: String, errorMessage: String?)
}