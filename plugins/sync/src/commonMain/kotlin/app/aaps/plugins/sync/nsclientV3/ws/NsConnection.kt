package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.nsclient.NSAlarm
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the lifetime of the Nightscout websocket connection.
 *
 * One level above [NsSocket]: that is a single namespace, this is "the app has a live connection to
 * Nightscout", including whatever the platform needs to keep it alive. On Android that is a bound
 * service holding a wake lock, which is why the plugin used to bind and unbind by hand. Elsewhere
 * there is no such thing and the sockets are simply owned by a scope.
 *
 * The plugin talks to this instead of to a service, so it does not need [android.content.Context].
 */
interface NsConnection {

    /**
     * Whether the storage socket is up **and** subscribed.
     *
     * Survives a reconnect of whatever carries the sockets, so UI collectors are not torn down with
     * the transport. Null is not modelled here - see [socketConnected] for the tri-state the status
     * line needs.
     */
    val connected: StateFlow<Boolean>

    /**
     * Connected state for display: null when nothing is carrying a connection at all.
     *
     * Three-valued on purpose. The status line says "connected", "not connected", or neither, and
     * "nothing started yet" is not the same as "started and not connected".
     */
    val socketConnected: Boolean?

    /** True while a storage socket object exists, whether or not it has finished connecting. */
    val hasLiveSocket: Boolean

    /**
     * Starts the connection, or re-checks one that is already up.
     *
     * Idempotent: on Android both the service's own creation and the bind callback can ask for this,
     * and calling it on a live connection must not tear anything down. [reason] is logged.
     */
    fun start(reason: String)

    /**
     * Tears the sockets down and releases whatever was holding them.
     *
     * The sockets must be closed **before** the carrier is released, not after: a quick restart
     * would otherwise race the asynchronous teardown and find the old sockets still attached.
     */
    fun stop()

    /** Tells Nightscout an alarm was seen, so it stops re-raising it for [silenceForMillis]. */
    fun acknowledgeAlarm(alarm: NSAlarm, silenceForMillis: Long)
}
