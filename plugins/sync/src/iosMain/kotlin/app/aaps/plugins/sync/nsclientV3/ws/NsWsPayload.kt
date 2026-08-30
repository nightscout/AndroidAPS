package app.aaps.plugins.sync.nsclientV3.ws

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reading the frames Nightscout sends over the websocket.
 *
 * Split out of [IosNsConnection] so it can be tested on its own: the connection needs the whole
 * plugin graph to construct, while this is the half that actually has to agree with the wire, and
 * it is where a mistake would be silent - a field read as null looks exactly like a field the server
 * did not send.
 *
 * Nothing here throws. A frame that cannot be read is dropped, because taking the socket down over
 * one malformed message would cost every later message too.
 */
internal object NsWsPayload {

    /** The whole frame, or null when it is not a JSON document at all. */
    fun parse(raw: String): JsonObject? =
        runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    fun string(doc: JsonObject, key: String): String? = doc[key]?.jsonPrimitive?.contentOrNull

    fun boolean(doc: JsonObject, key: String): Boolean? = doc[key]?.jsonPrimitive?.booleanOrNull

    /**
     * A whole number, accepting one written as a decimal.
     *
     * `srvModified` is a millisecond timestamp and decides which records the next load asks for. A
     * server or proxy that renders it as `1.7e12` would otherwise read as null, the high-water mark
     * would stay put, and the same records would be fetched forever.
     */
    fun long(doc: JsonObject, key: String): Long? =
        doc[key]?.jsonPrimitive?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    /** The document a create/update frame carries, or null when the frame has none. */
    fun document(frame: JsonObject): JsonObject? = frame["doc"] as? JsonObject
}
