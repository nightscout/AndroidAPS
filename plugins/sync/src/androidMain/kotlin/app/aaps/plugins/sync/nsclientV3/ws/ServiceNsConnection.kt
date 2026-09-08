package app.aaps.plugins.sync.nsclientV3.ws

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.services.NSClientV3Service
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [NsConnection] backed by a bound [NSClientV3Service].
 *
 * The service exists for one Android-specific reason: it holds a `PARTIAL_WAKE_LOCK` and is
 * `START_STICKY`, so the websocket survives doze and backgrounding. Binding it is therefore an
 * Android detail, and it lives here rather than in the plugin.
 *
 * A singleton, which is what makes [connected] usable by the UI: it outlives any individual bind, so
 * collectors are not torn down when the service reconnects.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ServiceNsConnection @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : NsConnection {

    private var service: NSClientV3Service? = null

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    // Reads the flow, not the service: the service reports its state into this object, so asking it
    // back would be a round trip through the thing that just told us.
    override val socketConnected: Boolean? get() = service?.let { _connected.value }

    override val hasLiveSocket: Boolean get() = service?.storageSocket != null

    /** The service reports its own websocket state through here. */
    internal fun setConnected(value: Boolean) {
        _connected.value = value
    }

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is disconnected")
            service = null
            // Process-death / crash teardown skips shutdownWebsockets, so flip the flag here
            // so UI gates don't keep showing "connected" until the service rebinds.
            _connected.value = false
        }

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is connected")
            service = (binder as NSClientV3Service.LocalBinder).serviceInstance
            // Covers the case where Android reuses an already-alive service on rebind:
            // onCreate doesn't fire, but onServiceConnected does. The idempotency guard
            // in initializeWebSockets makes this safe when both fire on a fresh bind.
            service?.initializeWebSockets("serviceConnected")
        }
    }

    override fun start(reason: String) {
        val bound = service
        if (bound != null) {
            // Already bound: just re-check the sockets. Binding again would do nothing useful.
            bound.initializeWebSockets(reason)
            return
        }
        if (preferences.get(BooleanKey.NsClient3UseWs)) {
            context.bindService(Intent(context, NSClientV3Service::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun stop() {
        try {
            // Tear down sockets synchronously before unbinding so a quick rebind
            // (e.g. via restartOnChange) doesn't race the async service onDestroy.
            service?.shutdownWebsockets()
            if (service != null) context.unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        service = null
    }

    override fun acknowledgeAlarm(alarm: NSAlarm, silenceForMillis: Long) {
        service?.handleClearAlarm(alarm, silenceForMillis)
    }
}
