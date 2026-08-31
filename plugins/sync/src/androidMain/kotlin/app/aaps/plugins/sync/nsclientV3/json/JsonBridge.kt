package app.aaps.plugins.sync.nsclientV3.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/**
 * Converts an `org.json` payload into a kotlinx one, at the one place the two still meet.
 *
 * `:core:nssdk` no longer speaks `org.json` - it cannot, because `org.json` is a JVM and Android API
 * and the module has to build for iOS. What still does is **socket.io**:
 * `io.socket:socket.io-client` hands every event payload over as `org.json.JSONObject`. That is the
 * library's API, so the conversion happens as the event arrives and everything downstream is kotlinx.
 *
 * This is androidMain on purpose and has no iOS counterpart. On iOS the socket delivers payloads as
 * text and the handlers parse them with kotlinx directly, so there is nothing to bridge.
 *
 * The conversion goes through text, which is exactly what the old code did internally, so nothing
 * about the parsed result changes. It is not free - do not put it on a hot path.
 *
 * There used to be a `toOrgJson` going the other way, for reading profile timestamps with the
 * `org.json`-based `JsonHelper`. `LoadProfileStoreRunner` reads them with the kotlinx
 * `safeGetLongAllowNull` / `safeGetStringAllowNull` instead, so nothing converts back any more.
 */
object JsonBridge {

    /** `org.json` tree -> kotlinx tree. Throws if the text is somehow not a JSON object. */
    fun JSONObject.toKotlinxJson(): JsonObject = Json.parseToJsonElement(toString()).jsonObject
}
