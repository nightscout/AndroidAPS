package app.aaps.plugins.sync.garmin

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import io.reactivex.rxjava3.disposables.Disposable
import org.jetbrains.annotations.VisibleForTesting

class GarminMessenger(
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    applicationIdNames: Map<String, String>,
    private val messageCallback: (app: GarminApplication, msg: Any) -> Unit,
    enableConnectIq: Boolean,
    enableSimulator: Boolean): Disposable, GarminReceiver {

    private var disposed: Boolean = false
    /** All devices that where connected since this instance was created. */
    private val devices = mutableMapOf<Long, GarminDevice>()
    @VisibleForTesting
    val liveApplications = mutableSetOf<GarminApplication>()
    private val clients = mutableListOf<GarminClient>()
    private val appIdNames = mutableMapOf<String, String>()
    init {
        aapsLogger.info(LTag.GARMIN, "init CIQ debug=$enableSimulator")
        appIdNames.putAll(applicationIdNames)
        if (enableConnectIq) startDeviceClient()
        if (enableSimulator) {
            appIdNames["SimAp"] = "SimulatorApp"
            GarminSimulatorClient(aapsLogger, this)
        }
    }

    private fun getDevice(client: GarminClient, deviceId: Long): GarminDevice {
        synchronized (devices) {
            return devices.getOrPut(deviceId) { GarminDevice(client, deviceId, "unknown") }
        }
    }

    private fun getApplication(client: GarminClient, deviceId: Long, appId: String): GarminApplication {
        synchronized (liveApplications) {
            var app = liveApplications.firstOrNull { app ->
                app.client == client && app.device.id == deviceId && app.id == appId }
            if (app == null) {
                app = GarminApplication(client, getDevice(client, deviceId), appId, appIdNames[appId])
                liveApplications.add(app)
            }
            return app
        }
    }

    private fun startDeviceClient() {
        GarminDeviceClient(aapsLogger, context, this)
    }

    override fun onConnect(client: GarminClient) {
        aapsLogger.info(LTag.GARMIN, "onConnect $client")
        clients.add(client)
    }

    override fun onDisconnect(client: GarminClient) {
        aapsLogger.info(LTag.GARMIN, "onDisconnect ${client.name}")
        clients.remove(client)
        synchronized (liveApplications) {
            liveApplications.removeIf { app -> app.client == client }
        }
        client.dispose()
        when (client.name) {
            "Device" -> startDeviceClient()
            "Sim"-> GarminSimulatorClient(aapsLogger, this)
            else -> aapsLogger.warn(LTag.GARMIN, "onDisconnect unknown client $client")
        }
    }

    /** Receives notifications that a device has connected.
     *
     * It will retrieve status information for all applications we care about (in [appIdNames]). */
    override fun onConnectDevice(client: GarminClient, deviceId: Long, deviceName: String) {
        val device = getDevice(client, deviceId).apply { name = deviceName }
        aapsLogger.info(LTag.GARMIN, "onConnectDevice $device")
        appIdNames.forEach { (id, name) -> client.retrieveApplicationInfo(device, id, name) }
    }

    /** Receives notifications about disconnection of a device. */
    override fun onDisconnectDevice(client: GarminClient, deviceId: Long) {
        val device = getDevice(client, deviceId)
        aapsLogger.info(LTag.GARMIN,"onDisconnectDevice $device")
        synchronized (liveApplications) {
            liveApplications.removeIf { app -> app.device == device }
        }
    }

    /** Receives notification about applications that are installed/uninstalled
     * on a device from the client. */
    override fun onApplicationInfo(device: GarminDevice, appId: String, isInstalled: Boolean) {
        val app = getApplication(device.client, device.id, appId)
        aapsLogger.info(LTag.GARMIN, "onApplicationInfo add $app ${if (isInstalled) "" else "un"}installed")
        if (!isInstalled) {
            synchronized (liveApplications) { liveApplications.remove(app) }
        }
    }

    override fun onReceiveMessage(client: GarminClient, deviceId: Long, appId: String, data: ByteArray) {
        val app = getApplication(client, deviceId, appId)
        val msg = GarminSerializer.deserialize(data)
        if (msg == null) {
            aapsLogger.warn(LTag.GARMIN, "receive NULL msg")
        } else {
            aapsLogger.info(LTag.GARMIN, "receive ${data.size} bytes")
            messageCallback(app, msg)
        }
    }

    /** Receives status notifications for a sent message. */
    override fun onSendMessage(client: GarminClient, deviceId: Long, appId: String, errorMessage: String?) {
        val app = getApplication(client, deviceId, appId)
        aapsLogger.info(LTag.GARMIN, "onSendMessage $app ${errorMessage ?: "OK"}")
    }

    fun sendMessage(device: GarminDevice, msg: Any) {
        liveApplications
            .filter { a -> a.device.id == device.id }
            .forEach { a -> sendMessage(a, msg) }
    }

    /** Sends a message to all applications on all devices. */
    fun sendMessage(msg: Any) {
        liveApplications.forEach { app -> sendMessage(app, msg) }
    }

    private fun sendMessage(app: GarminApplication, msg: Any) {
        // Convert msg to string for logging.
        val s = when (msg) {
            is Map<*,*> ->
                msg.entries.joinToString(", ", "(", ")") { (k, v) -> "$k=$v" }
            is List<*> ->
                msg.joinToString(", ", "(", ")")
            else ->
                msg.toString()
        }
        val data = GarminSerializer.serialize(msg)
        aapsLogger.info(LTag.GARMIN, "sendMessage $app $app ${data.size} bytes $s")
        try {
            app.client.sendMessage(app, data)
        } catch (e: IllegalStateException) {
            aapsLogger.error(LTag.GARMIN, "${app.client} not connected", e)
        }
    }

    override fun dispose() {
        if (!disposed) {
            clients.forEach { c -> c.dispose() }
            disposed = true
        }
        clients.clear()
    }

    override fun isDisposed() = disposed
}