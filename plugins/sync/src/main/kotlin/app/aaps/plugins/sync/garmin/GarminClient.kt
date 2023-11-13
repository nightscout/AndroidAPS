package app.aaps.plugins.sync.garmin

import io.reactivex.rxjava3.disposables.Disposable

interface GarminClient: Disposable {
    /** Name of the client. */
    val name: String

    /** Asynchronously retrieves status information for the given application. */
    fun retrieveApplicationInfo(device: GarminDevice, appId: String, appName: String)

    /** Asynchronously sends a message to an application. */
    fun sendMessage(app: GarminApplication, data: ByteArray)
}