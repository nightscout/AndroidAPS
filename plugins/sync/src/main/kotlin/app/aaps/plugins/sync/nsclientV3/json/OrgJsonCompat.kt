package app.aaps.plugins.sync.nsclientV3.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Accessors on kotlinx [JsonObject] that behave exactly like the `org.json` ones they replace.
 *
 * `org.json` is a JVM and Android API, so it cannot go to iOS. The NSClientV3 wire layer is moving
 * to kotlinx [JsonObject], but the two libraries disagree about a missing key and nothing about that
 * disagreement shows up at compile time:
 *
 * ```
 * doc.optString("k")                  // org.json : "" when absent, never null
 * doc["k"]?.jsonPrimitive?.content    // kotlinx  : null when absent
 * ```
 *
 * A straight translation would silently flip every downstream `isEmpty()` check and `?:` fallback.
 * These functions keep the old behaviour so the type swap changes nothing that can be observed,
 * and `OrgJsonCompatTest` checks each one against the real `org.json` over a shared matrix of
 * inputs.
 *
 * **This is a migration shim, not a design.** It deliberately copies quirks that are probably bugs -
 * see [optString] returning the four letter text `null`. They are kept so the move off `org.json`
 * is provably behaviour preserving. Fixing them is a separate, visible change.
 *
 * It lives in `:plugins:sync` on purpose: this module is Android only (WorkManager, socket.io
 * Service) and will never build for iOS, so the `org.json` quirks stay out of the shared modules.
 */
object OrgJsonCompat {

    /**
     * Same as `org.json.JSONObject.optString(name)`.
     *
     * - missing key -> `""`
     * - explicit JSON null -> the four letter text `"null"`, because Android's `org.json` renders
     *   its `JSONObject.NULL` sentinel through `String.valueOf`
     * - string -> the string itself, unquoted
     * - number or boolean -> its text form
     * - object or array -> its JSON text
     *
     * Never returns Kotlin null. Callers written against `org.json` rely on that, sometimes without
     * meaning to: `NSClientV3Service.onDataDelete` guards with `?: return@Listener`, which can never
     * fire today.
     */
    fun JsonObject.optStringCompat(key: String): String {
        val element = this[key] ?: return ""
        return when (element) {
            is JsonNull      -> "null"
            is JsonPrimitive -> element.content
            else             -> element.toString()
        }
    }

    /**
     * Same as `org.json.JSONObject.optJSONObject(name)`: null when the key is missing, when the
     * value is an explicit JSON null, or when it holds anything that is not an object.
     */
    fun JsonObject.optJsonObjectCompat(key: String): JsonObject? = this[key] as? JsonObject

    /**
     * Same as `org.json.JSONObject.optLong(name, fallback)`.
     *
     * Coerces like `org.json` does: a numeric string is parsed, and a floating point value is
     * truncated toward zero. Anything that cannot be read as a number gives [fallback].
     */
    fun JsonObject.optLongCompat(key: String, fallback: Long): Long {
        val primitive = this[key] as? JsonPrimitive ?: return fallback
        if (primitive is JsonNull) return fallback
        primitive.longOrNull?.let { return it }
        primitive.doubleOrNull?.let { return it.toLong() }
        return fallback
    }

    /**
     * Same as `org.json.JSONObject.optBoolean(name)`: false unless the value is a real `true`, or
     * the text `"true"` in any case. `org.json` accepts the string form too.
     */
    fun JsonObject.optBooleanCompat(key: String): Boolean {
        val primitive = this[key] as? JsonPrimitive ?: return false
        if (primitive is JsonNull) return false
        primitive.booleanOrNull?.let { return it }
        return primitive.content.equals("true", ignoreCase = true)
    }

    /** Same as `org.json.JSONObject.optJSONArray(name)`. */
    fun JsonObject.optJsonArrayCompat(key: String): JsonArray? = this[key] as? JsonArray
}
