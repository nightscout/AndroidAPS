package app.aaps.core.data.json

import app.aaps.core.data.json.OrgJsonCompat.optStringCompat
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
 * `org.json` is a JVM and Android API, so it cannot go to iOS. Code moving to `commonMain` has to
 * stop using it, but the two libraries disagree about a missing key and nothing about that
 * disagreement shows up at compile time:
 *
 * ```
 * doc.optString("k")                  // org.json : "" when absent, never null
 * doc["k"]?.jsonPrimitive?.content    // kotlinx  : null when absent
 * ```
 *
 * A straight translation would silently flip every downstream `isEmpty()` check and `?:` fallback.
 * These functions keep the old behaviour so the type swap changes nothing that can be observed, and
 * `OrgJsonCompatParityTest` checks each one against a real `org.json` over a shared matrix of inputs.
 *
 * **This is a migration shim, not a design.** It deliberately copies quirks that are probably bugs -
 * see [optStringCompat] returning the four letter text `null`. They are kept so the move off
 * `org.json` is provably behaviour preserving. Fixing them is a separate, visible change.
 *
 * It used to live in `:plugins:sync`, which is Android only, on the argument that the quirks should
 * stay out of shared modules. That argument stopped holding when `:core:interfaces` itself had to
 * move: the profile spine (`Profile.toPureNsJson`, `ProfileStore`, `SingleProfile`) carries
 * `JSONObject` and `JSONArray` in its **contracts**, so the shim has to be visible wherever those
 * contracts are.
 *
 * Read this together with the divergences the parity test documents but does NOT hide - see the
 * `writes differ` section there. Reading is safe to shim; writing changes the bytes on the wire.
 */
object OrgJsonCompat {

    /**
     * Same as `org.json.JSONObject.optString(name)`.
     *
     * - missing key -> `""`
     * - explicit JSON null -> the four letter text `"null"`, because `org.json` renders its
     *   `JSONObject.NULL` sentinel through `String.valueOf`
     * - string -> the string itself, unquoted
     * - number or boolean -> its text form
     * - object or array -> its JSON text
     *
     * Never returns Kotlin null. Callers written against `org.json` rely on that, sometimes without
     * meaning to.
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

    /**
     * Same as `org.json.JSONObject.optDouble(name, fallback)`.
     *
     * Needed by the profile spine, where every schedule value is a double. Coerces a numeric string
     * the way `org.json` does.
     */
    fun JsonObject.optDoubleCompat(key: String, fallback: Double): Double {
        val primitive = this[key] as? JsonPrimitive ?: return fallback
        if (primitive is JsonNull) return fallback
        return primitive.doubleOrNull ?: fallback
    }

    /**
     * Same as `org.json.JSONObject.optInt(name, fallback)`, including its inconsistency about
     * out-of-range values - which is why this reads more carefully than the others.
     *
     * `org.json` takes two different routes depending on how the value was written, and they
     * disagree once the value does not fit in an `Int`:
     *
     * - a real JSON **number** is a boxed `Number`, and `org.json` calls `intValue()` on it, which
     *   TRUNCATES the low 32 bits - so `1785992181588` reads back as `-714213548`
     * - a **quoted** number is text, and `org.json` falls back to `Double.intValue()`, which
     *   SATURATES - so `"1785992181588"` reads back as `2147483647`
     *
     * Reading both through `Long.toInt()` looks obviously right and is wrong for the quoted case:
     * it silently turns a large positive number negative. The parity test catches exactly that.
     */
    fun JsonObject.optIntCompat(key: String, fallback: Int): Int {
        val primitive = this[key] as? JsonPrimitive ?: return fallback
        if (primitive is JsonNull) return fallback
        return if (primitive.isString) {
            primitive.content.toIntOrNull()
                ?: primitive.content.toDoubleOrNull()?.toInt()   // saturates, like Double.intValue()
                ?: fallback
        } else {
            primitive.longOrNull?.toInt()                        // truncates, like Long.intValue()
                ?: primitive.doubleOrNull?.toInt()
                ?: fallback
        }
    }

    /**
     * Same as `org.json.JSONObject.has(name)`.
     *
     * Note this is `has`, not "has a usable value": `org.json` answers true for an explicit JSON
     * null, and so does this.
     */
    fun JsonObject.hasCompat(key: String): Boolean = containsKey(key)
}
