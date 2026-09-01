package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSAlarm
import app.aaps.plugins.sync.nsclientV3.ws.NsConnection
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * No Nightscout websocket on desktop - the client polls instead.
 *
 * ## Why there is no socket, and why that is not a gap in sync
 *
 * The websocket is how a phone hears about new data immediately. It is not how data is fetched:
 * `NSClientV3Plugin` has a refresh tick that runs the same REST load round every one to five
 * minutes, and `DesktopNsLoadExecutor` runs exactly the nine steps a phone runs. So a desktop
 * follower is fully synced, just with a delay of up to a few minutes instead of seconds.
 *
 * That makes the socket an optimisation here rather than a blocker, which is worth knowing because
 * the socket is not cheap to add: `SocketIoNsSocket` is JVM code that would share fine, but
 * `socket.io-client` pulls in Crockford's `org.json`, and Android runs against the platform's AOSP
 * one instead. The two disagree on real cases - the version catalog documents `optString` of a JSON
 * null giving `""` on one and `"null"` on the other - so sharing that class would quietly put a
 * different JSON implementation under the Nightscout wire path on desktop than on the phone. Not a
 * trade worth making for lower latency.
 *
 * ## What [supportsWebsocket] prevents
 *
 * The preference that chooses websockets over polling defaults to **on**. Without that flag a
 * desktop would take the websocket branch, get no pushes because there is no socket, and never poll
 * either - sync silently dead while every screen looked normal. With it, the tick polls regardless
 * of the preference, so a user toggling that setting cannot break this client.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DesktopNsConnection @Inject constructor(
    private val aapsLogger: AAPSLogger
) : NsConnection {

    private val _connected = MutableStateFlow(false)

    /** Never true: nothing here carries a socket. */
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Null rather than false - "nothing is carrying a connection", not "tried and failed". */
    override val socketConnected: Boolean? = null

    override val hasLiveSocket: Boolean = false

    override val supportsWebsocket: Boolean = false

    override fun start(reason: String) {
        aapsLogger.debug(LTag.NSCLIENT, "No websocket on desktop; polling instead (asked by $reason)")
    }

    override fun stop() {
        // Nothing was started, so there is nothing to stop. Not worth logging on every settings change.
    }

    /**
     * Alarms cannot be acknowledged back to Nightscout without a socket.
     *
     * Logged at error because this one does lose something a phone has: silencing an alarm here does
     * not silence it on the server or on other clients. Anything relying on that needs to know it
     * did not happen rather than assume it did.
     */
    override fun acknowledgeAlarm(alarm: NSAlarm, silenceForMillis: Long) {
        aapsLogger.error(LTag.NSCLIENT, "Cannot acknowledge alarm to Nightscout without a websocket")
    }
}
