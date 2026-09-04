package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What to say to a Nightscout socket when it connects, and what to do when it drops.
 *
 * The companion of [NsFrameHandler], and here for the same reason. Connecting is not a lifecycle
 * concern - *who owns the socket* is, and that stays per platform - so the subscribe message, the
 * collection list and the reaction to an auth failure do not belong in two places. They were in two
 * places: `NSClientV3Service` with `org.json`, [SocketNsConnection] with kotlinx, line for line the
 * same otherwise. That is the shape the frame handlers had before they were shared, and it ended
 * with `settings` handled on one platform only.
 *
 * The collection list matters most. It is what the app asks Nightscout to push, so a name added here
 * and not there means a platform silently stops receiving something - which is exactly how client
 * control came to be dead on iOS.
 *
 * ## What the caller still owns
 *
 * Whether the connection counts as up. Android keeps a `wsConnected` field, the shared connection a
 * `StateFlow` the UI collects, so the outcome is handed back rather than written here.
 */
@SingleIn(AppScope::class)
class NsConnectHandler @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val nsClientRepository: NSClientRepository,
    // Deferred for the reason the frame handler is: the plugin owns the connection that owns this.
    private val nsClientV3Plugin: () -> NSClientV3Plugin
) {

    /**
     * Subscribes the storage socket and reports whether it is up.
     *
     * [onSubscribed] is called with the answer, once, on the socket's own callback thread. A failed
     * subscribe is not an error to throw - Nightscout said no, the status line says so, and the next
     * connect tries again.
     */
    fun onConnectStorage(socket: NsSocket?, onSubscribed: (Boolean) -> Unit) {
        nsClientRepository.addLog("◄ WS", "connected storage ID: ${socket?.id ?: "NULL"}")
        if (socket == null) return
        val auth = buildJsonObject {
            put("accessToken", preferences.get(StringKey.NsClientAccessToken))
            put("collections", JsonArray(COLLECTIONS.map { JsonPrimitive(it) }))
        }
        nsClientRepository.addLog("► WS", "requesting auth for storage")
        socket.emitWithAck("subscribe", auth.toString()) { raw ->
            val response = NsWsPayload.parse(raw)
            val ok = response?.let { NsWsPayload.boolean(it, "success") } == true
            if (ok) {
                nsClientRepository.addLog("◄ WS", "Subscribed for: ${response?.let { NsWsPayload.text(it, "collections") }}")
                // Nothing arrives while disconnected, so the next round has to be a catch-up one.
                nsClientV3Plugin().initialLoadFinished = false
                nsClientV3Plugin().executeLoop("WS_CONNECT")
            } else {
                nsClientRepository.addLog("◄ WS", "Auth failed")
            }
            onSubscribed(ok)
            nsClientRepository.updateStatus(nsClientV3Plugin().status)
        }
    }

    /** Subscribes the alarm socket. Nothing depends on the outcome beyond the log. */
    fun onConnectAlarms(socket: NsSocket?) {
        nsClientRepository.addLog("◄ WS", "connected alarms ID: ${socket?.id ?: "NULL"}")
        if (socket == null) return
        val auth = buildJsonObject { put("accessToken", preferences.get(StringKey.NsClientAccessToken)) }
        nsClientRepository.addLog("► WS", "requesting auth for alarms")
        socket.emitWithAck("subscribe", auth.toString()) { raw ->
            val response = NsWsPayload.parse(raw)
            if (response?.let { NsWsPayload.boolean(it, "success") } == true)
                nsClientRepository.addLog("◄ WS", response.let { NsWsPayload.text(it, "message") } ?: "")
            else nsClientRepository.addLog("◄ WS", "Auth failed")
        }
    }

    /**
     * The storage socket dropped.
     *
     * [onDisconnected] mirrors [onConnectStorage]: the caller owns whether the app counts as
     * connected. The initial load is marked unfinished here because whatever arrived while the
     * socket was up is now potentially behind.
     */
    fun onDisconnectStorage(reason: String, onDisconnected: () -> Unit) {
        aapsLogger.debug(LTag.NSCLIENT, "disconnect storage reason: $reason")
        nsClientRepository.addLog("◄ WS", "disconnect storage event")
        onDisconnected()
        nsClientV3Plugin().initialLoadFinished = false
        nsClientRepository.updateStatus(nsClientV3Plugin().status)
    }

    /**
     * The alarm socket dropped.
     *
     * Only logged, which is the whole handler - but it existed on Android alone, so an alarm socket
     * that dropped on iOS or the desktop said nothing at all. Someone reading the log to work out
     * why alarms went quiet had nothing to read.
     */
    fun onDisconnectAlarm(reason: String) {
        aapsLogger.debug(LTag.NSCLIENT, "disconnect alarm reason: $reason")
        nsClientRepository.addLog("◄ WS", "disconnect alarm event")
    }

    companion object {

        /**
         * What the app asks Nightscout to push.
         *
         * One list, because the two copies of it were the reason `settings` reached Android and not
         * the other platforms. Adding a name here adds it everywhere, which is the point.
         */
        val COLLECTIONS = listOf("devicestatus", "entries", "profile", "treatments", "foods", "settings")
    }
}
