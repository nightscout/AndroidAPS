package app.aaps.plugins.sync.nsclientV3.ws

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * [NsSocket] on top of the socket.io client.
 *
 * The socket is made in the constructor, so it exists as soon as the object does. That matters:
 * `IO.socket()` puts the socket into socket.io's process-static Manager cache straight away, and a
 * socket that is in that cache with listeners on it but unreachable by its owner leaks for the
 * lifetime of the process. Because the owner holds this object from the moment it is constructed,
 * [close] can always reach it.
 */
class SocketIoNsSocket(url: String) : NsSocket {

    private val socket: Socket = IO.socket(url)

    /** Kept so [close] can take the listeners off again; socket.io needs the same instance back. */
    private val listeners = mutableListOf<Pair<String, Emitter.Listener>>()

    override val id: String? get() = socket.id()

    override fun on(event: String, listener: (payload: String) -> Unit) {
        val wrapped = Emitter.Listener { args -> listener(firstPayload(args)) }
        listeners += event to wrapped
        socket.on(event, wrapped)
    }

    override fun connect() {
        socket.connect()
    }

    override fun close() {
        listeners.forEach { (event, listener) -> socket.off(event, listener) }
        listeners.clear()
        socket.disconnect()
    }

    override fun emitWithAck(event: String, payload: String, ack: (response: String) -> Unit) {
        socket.emit(event, JSONObject(payload), Ack { args -> ack(firstPayload(args)) })
    }

    override fun emitAlarmAck(level: Int, group: String, silenceForMillis: Long) {
        socket.emit("ack", level, group, silenceForMillis)
    }

    /**
     * socket.io hands over an argument array. Every event we listen for carries one argument at
     * most: a document, a disconnect reason, or nothing at all for connect.
     */
    private fun firstPayload(args: Array<Any>?): String = args?.firstOrNull()?.toString() ?: ""
}

@ContributesBinding(AppScope::class)
class SocketIoNsSocketFactory @Inject constructor() : NsSocketFactory {

    override fun create(url: String): NsSocket? =
        try {
            SocketIoNsSocket(url)
        } catch (_: URISyntaxException) {
            null
        }
}
