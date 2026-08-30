package app.aaps.plugins.sync.nsclientV3.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/**
 * Conversions between `org.json` and kotlinx JSON, for the places where the two still meet.
 *
 * `:core:nssdk` no longer speaks `org.json` - it cannot, because `org.json` is a JVM and Android API
 * and the module has to build for iOS. Two things on the Android side still do, and neither is worth
 * changing yet:
 *
 * 1. **socket.io.** `io.socket:socket.io-client` hands every event payload over as
 *    `org.json.JSONObject`. That is the library's API, so the conversion happens as the event
 *    arrives and everything downstream is kotlinx.
 * 2. **The profile subsystem.** `ProfileStore`, `PureProfile` and `DataSyncSelector.PairProfileStore`
 *    are built on `org.json` throughout, far outside this module. Profiles are converted where they
 *    cross into or out of the client instead of migrating that subsystem here.
 *
 * Both conversions go through text, which is exactly what the old code did internally, so nothing
 * about the parsed result changes. They are not free - do not put them on a hot path.
 */
object JsonBridge {

    /** `org.json` tree -> kotlinx tree. Throws if the text is somehow not a JSON object. */
    fun JSONObject.toKotlinxJson(): JsonObject = Json.parseToJsonElement(toString()).jsonObject

    /** kotlinx tree -> `org.json` tree. */
    fun JsonObject.toOrgJson(): JSONObject = JSONObject(toString())
}
