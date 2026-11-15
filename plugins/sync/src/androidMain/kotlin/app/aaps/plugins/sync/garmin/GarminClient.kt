package app.aaps.plugins.sync.garmin

import io.reactivex.rxjava3.disposables.Disposable

interface GarminClient: Disposable {
    /** Name of the client. */
    val name: String

    val connectedDevices: List<GarminDevice>

    /** Register to receive messages from the given up. */
    fun registerForMessages(app: GarminApplication)

    /** Asynchronously sends a message to an application. */
    fun sendMessage(app: GarminApplication, data: ByteArray)
}