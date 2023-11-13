package app.aaps.plugins.sync.garmin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.waitMillis
import com.garmin.android.apps.connectmobile.connectiq.IConnectIQService
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQMessage
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.jetbrains.annotations.VisibleForTesting
import java.time.Duration
import java.time.Instant
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.concurrent.TimeUnit


/** GarminClient that talks via the ConnectIQ app to a physical device. */
class GarminDeviceClient(
    private val aapsLogger: AAPSLogger,
    private val context: Context,
    private val receiver: GarminReceiver,
    private val retryWaitFactor: Long = 5L): Disposable, GarminClient {

    override val name = "Device"
    private var bindLock = Object()
    private var ciqService: IConnectIQService? = null
        get() {
            val waitUntil = Instant.now().plusSeconds(2)
            synchronized (bindLock) {
                while(field?.asBinder()?.isBinderAlive != true) {
                    field = null
                    if (state !in arrayOf(State.BINDING, State.RECONNECTING)) {
                        aapsLogger.info(LTag.GARMIN, "reconnecting to ConnectIQ service")
                        state = State.RECONNECTING
                        context.bindService(serviceIntent, ciqServiceConnection, Context.BIND_AUTO_CREATE)
                    }
                    // Wait for the connection, that is the call to onServiceConnected.
                    val wait = Duration.between(Instant.now(), waitUntil)
                    if (wait > Duration.ZERO) bindLock.waitMillis(wait.toMillis())
                    if (field == null) {
                        // The [serviceConnection] didn't have a chance to reassign ciqService,
                        // i.e. the wait timed out. Give up.
                        aapsLogger.warn(LTag.GARMIN, "no ciqservice $this")
                        return null
                    }
                }
                return field
            }
        }

    private val registeredActions = mutableSetOf<String>()
    private val broadcastReceiver = mutableListOf<BroadcastReceiver>()
    private var state = State.DISCONNECTED
    private val serviceIntent get() = Intent(CONNECTIQ_SERVICE_ACTION).apply {
        component = CONNECTIQ_SERVICE_COMPONENT }

    @VisibleForTesting
    val sendMessageAction = createAction("SEND_MESSAGE")

    private enum class State {
        BINDING,
        CONNECTED,
        DISCONNECTED,
        DISPOSED,
        RECONNECTING,
    }

    private val ciqServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            var notifyReceiver: Boolean
            val ciq: IConnectIQService
            synchronized (bindLock) {
                aapsLogger.info(LTag.GARMIN, "ConnectIQ App connected")
                ciq = IConnectIQService.Stub.asInterface(service)
                notifyReceiver = state != State.RECONNECTING
                state = State.CONNECTED
                ciqService = ciq
                bindLock.notifyAll()
            }
            if (notifyReceiver) receiver.onConnect(this@GarminDeviceClient)
            ciq.connectedDevices?.forEach { d ->
                receiver.onConnectDevice(this@GarminDeviceClient, d.deviceIdentifier, d.friendlyName) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(bindLock) {
                aapsLogger.info(LTag.GARMIN, "ConnectIQ App disconnected")
                ciqService = null
                if (state != State.DISPOSED) state = State.DISCONNECTED
            }
            broadcastReceiver.forEach { br -> context.unregisterReceiver(br) }
            broadcastReceiver.clear()
            registeredActions.clear()
            receiver.onDisconnect(this@GarminDeviceClient)
        }
    }

    init {
        aapsLogger.info(LTag.GARMIN, "binding to ConnectIQ service")
        registerReceiver(sendMessageAction, ::onSendMessage)
        state = State.BINDING
        context.bindService(serviceIntent, ciqServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun isDisposed() = state == State.DISPOSED
    override fun dispose() {
        broadcastReceiver.forEach { context.unregisterReceiver(it) }
        broadcastReceiver.clear()
        registeredActions.clear()
        try {
            context.unbindService(ciqServiceConnection)
        } catch (e: Exception) {
            aapsLogger.warn(LTag.GARMIN, "unbind CIQ failed ${e.message}")
        }
        state = State.DISPOSED
    }

    /** Creates a unique action name for ConnectIQ callbacks. */
    private fun createAction(action: String) = "${javaClass.`package`!!.name}.$action"

    /** Registers a callback [BroadcastReceiver] under the given action that will
     * used by the ConnectIQ app for callbacks.*/
    private fun registerReceiver(action: String, receive: (intent: Intent) -> Unit) {
        val recv = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) { receive(intent) }
        }
        broadcastReceiver.add(recv)
        context.registerReceiver(recv, IntentFilter(action))
    }

    override fun retrieveApplicationInfo(device: GarminDevice, appId: String, appName: String) {
        val action = createAction("APPLICATION_INFO_${device.id}_$appId")
        synchronized (registeredActions) {
            if (!registeredActions.contains(action)) {
                registerReceiver(action) { intent -> onApplicationInfo(appId, device, intent) }
            }
            registeredActions.add(action)
        }
        ciqService?.getApplicationInfo(context.packageName, action, device.toIQDevice(), appId)
    }

    /** Receives application info callbacks from ConnectIQ app.*/
    private fun onApplicationInfo(appId: String, device: GarminDevice, intent: Intent) {
        val receivedAppId = intent.getStringExtra(EXTRA_APPLICATION_ID)?.lowercase(Locale.getDefault())
        val version = intent.getIntExtra(EXTRA_APPLICATION_VERSION, -1)
        val isInstalled = receivedAppId != null && version >= 0 && version != 65535

        if (isInstalled) registerForMessages(device.id, appId)
        receiver.onApplicationInfo(device, appId, isInstalled)
    }

    private fun registerForMessages(deviceId: Long, appId: String) {
        aapsLogger.info(LTag.GARMIN, "registerForMessage $name $appId")
        val action = createAction("ON_MESSAGE_${deviceId}_$appId")
        val app = IQApp().apply { applicationID = appId; displayName = "" }
        synchronized (registeredActions) {
            if (!registeredActions.contains(action)) {
                registerReceiver(action) { intent: Intent -> onReceiveMessage(app, intent) }
                ciqService?.registerApp(app, action, context.packageName)
                registeredActions.add(action)
            } else {
                aapsLogger.info(LTag.GARMIN, "registerForMessage $action already registered")
            }
        }
    }

    @Suppress("Deprecation")
    private fun onReceiveMessage(iqApp: IQApp, intent: Intent) {
        val iqDevice = intent.getParcelableExtra(EXTRA_REMOTE_DEVICE) as IQDevice?
        val data = intent.getByteArrayExtra(EXTRA_PAYLOAD)
        if (iqDevice != null && data != null)
            receiver.onReceiveMessage(this, iqDevice.deviceIdentifier, iqApp.applicationID, data)
    }

    /** Receives callback from ConnectIQ about message transfers. */
    private fun onSendMessage(intent: Intent) {
        val status = intent.getIntExtra(EXTRA_STATUS, 0)
        val deviceId = getDevice(intent)
        val appId = intent.getStringExtra(EXTRA_APPLICATION_ID)?.lowercase()
        if (deviceId == null || appId == null) {
            aapsLogger.warn(LTag.GARMIN, "onSendMessage device='$deviceId' app='$appId'")
        } else {
            synchronized (messageQueues) {
                val queue = messageQueues[deviceId to appId]
                val msg = queue?.peek()
                if (queue == null || msg == null) {
                    aapsLogger.warn(LTag.GARMIN, "onSendMessage unknown message $deviceId, $appId, $status")
                    return
                }

                when (status) {
                    IQMessage.FAILURE_DEVICE_NOT_CONNECTED,
                    IQMessage.FAILURE_DURING_TRANSFER -> {
                        if (msg.attempt < MAX_RETRIES) {
                            val  delaySec = retryWaitFactor * msg.attempt
                            Schedulers.io().scheduleDirect({ retryMessage(deviceId, appId) }, delaySec, TimeUnit.SECONDS)
                            return
                        }
                    }

                    else -> {}
                }
                queue.remove(msg)
                val errorMessage = status
                    .takeUnless { it == IQMessage.SUCCESS }?.let { s -> "error $s" }
                receiver.onSendMessage(this, msg.app.device.id, msg.app.id, errorMessage)
                if (queue.isNotEmpty()) {
                    Schedulers.io().scheduleDirect { retryMessage(deviceId, appId) }
                }
            }
        }
    }

    @Suppress("Deprecation")
    private fun getDevice(intent: Intent): Long? {
        val rawDevice = intent.extras?.get(EXTRA_REMOTE_DEVICE)
        return if (rawDevice is Long) rawDevice else (rawDevice as IQDevice?)?.deviceIdentifier
            ?: return null
    }

    private class Message(
        val app: GarminApplication,
        val data: ByteArray) {
        var attempt: Int = 0
        var lastAttempt: Instant? = null
        val iqApp get() = IQApp().apply { applicationID = app.id; displayName = app.name }
        val iqDevice get() = app.device.toIQDevice()
    }

    private val messageQueues = mutableMapOf<Pair<Long, String>, Queue<Message>> ()

    override fun sendMessage(app: GarminApplication, data: ByteArray) {
        val msg = synchronized (messageQueues) {
            val msg = Message(app, data)
            val queue = messageQueues.getOrPut(app.device.id to app.id) { LinkedList() }
            queue.add(msg)
            // Make sure we have only one outstanding message per app, so we ensure
            // that always the first message in the queue is currently send.
            if (queue.size == 1) msg else null
        }
        if (msg != null) sendMessage(msg)
    }

    private fun retryMessage(deviceId: Long, appId: String) {
        val msg = synchronized (messageQueues) {
            messageQueues[deviceId to appId]?.peek() ?: return
        }
        sendMessage(msg)
    }

    private fun sendMessage(msg: Message) {
        msg.attempt++
        msg.lastAttempt = Instant.now()
        val iqMsg = IQMessage().apply {
            messageData = msg.data
            notificationPackage = context.packageName
            notificationAction = sendMessageAction }
        ciqService?.sendMessage(iqMsg, msg.iqDevice, msg.iqApp)
    }

    override fun toString() = "$name[$state]"

    companion object {
        const val CONNECTIQ_SERVICE_ACTION = "com.garmin.android.apps.connectmobile.CONNECTIQ_SERVICE_ACTION"
        const val EXTRA_APPLICATION_ID = "com.garmin.android.connectiq.EXTRA_APPLICATION_ID"
        const val EXTRA_APPLICATION_VERSION = "com.garmin.android.connectiq.EXTRA_APPLICATION_VERSION"
        const val EXTRA_REMOTE_DEVICE = "com.garmin.android.connectiq.EXTRA_REMOTE_DEVICE"
        const val EXTRA_PAYLOAD = "com.garmin.android.connectiq.EXTRA_PAYLOAD"
        const val EXTRA_STATUS = "com.garmin.android.connectiq.EXTRA_STATUS"
        val CONNECTIQ_SERVICE_COMPONENT = ComponentName(
            "com.garmin.android.apps.connectmobile",
            "com.garmin.android.apps.connectmobile.connectiq.ConnectIQService")

        const val MAX_RETRIES = 10
    }
}