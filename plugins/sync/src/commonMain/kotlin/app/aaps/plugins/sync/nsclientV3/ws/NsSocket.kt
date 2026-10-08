package app.aaps.plugins.sync.nsclientV3.ws

/**
 * One Nightscout websocket namespace, with no socket.io types in sight.
 *
 * Payloads cross this border as JSON text rather than as a parsed document. That is the one shape
 * every platform can produce, and it lets the handlers above pick their own parser: Android still
 * uses org.json today, shared code will use kotlinx.
 *
 * Nothing here starts a thread. A socket is created, driven and closed by whoever owns it, which on
 * Android is the service holding the wake lock.
 */
interface NsSocket {

    /** Id the server gave this connection, or null while it is not connected. */
    val id: String?

    /** Registers [listener] for [event]. Listeners are dropped again by [close]. */
    fun on(event: String, listener: (payload: String) -> Unit)

    fun connect()

    /** Removes every listener, then disconnects. After this the socket is not reusable. */
    fun close()

    /**
     * Sends [payload] for [event] and hands the server's answer to [ack].
     *
     * Both are JSON text. [ack] runs on whatever thread the transport answers on.
     */
    fun emitWithAck(event: String, payload: String, ack: (response: String) -> Unit)

    /**
     * Acknowledges an alarm, which keeps Nightscout from raising it again for
     * [silenceForMillis].
     *
     * Spelled out as its own method rather than a general "send these arguments" one: Nightscout
     * wants the three values as three separate arguments, not as one document, and this is the only
     * place that needs it.
     */
    fun emitAlarmAck(level: Int, group: String, silenceForMillis: Long)

    companion object {

        const val EVENT_CONNECT = "connect"
        const val EVENT_DISCONNECT = "disconnect"
    }
}

/**
 * Builds a socket for one namespace URL.
 *
 * Kept apart from [NsSocket] so the owner can make its sockets itself, at the moment it is ready for
 * them, instead of being handed ones that are already live.
 */
interface NsSocketFactory {

    /** Returns null when [url] is not a usable address. */
    fun create(url: String): NsSocket?
}
