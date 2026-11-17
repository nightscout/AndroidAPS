package app.aaps.plugins.aps.autotune.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Readers for the oref0 prep file that fail where the `get...` readers of `org.json` failed: on a key
 * that is missing, null or of the wrong type. The callers read inside a `try` and stop at the first
 * failure, and these keep that.
 *
 * The coercion is `org.json`'s too: a number in quotes is read as a number, and a whole number in an
 * `Int` field is cut to its low 32 bits, while a quoted one is held at the `Int` limits. See
 * `OrgJsonCompat.optIntCompat` in `:core:data` for why the two differ.
 */

/** A value of an oref0 prep file that is missing or of the wrong type. */
internal class OrgJsonReadException(message: String) : Exception(message)

private fun JsonObject.primitive(key: String): JsonPrimitive {
    val element = this[key]
    return if (element is JsonPrimitive && element !is JsonNull) element else throw OrgJsonReadException("No number or text for $key")
}

internal fun JsonObject.double(key: String): Double =
    primitive(key).content.toDoubleOrNull() ?: throw OrgJsonReadException("$key is not a number")

internal fun JsonObject.long(key: String): Long {
    val content = primitive(key).content
    return content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong() ?: throw OrgJsonReadException("$key is not a number")
}

internal fun JsonObject.int(key: String): Int {
    val primitive = primitive(key)
    val content = primitive.content
    return (if (primitive.isString) content.toIntOrNull() else content.toLongOrNull()?.toInt())
        ?: content.toDoubleOrNull()?.toInt()
        ?: throw OrgJsonReadException("$key is not a number")
}

internal fun JsonObject.string(key: String): String =
    when (val element: JsonElement = this[key] ?: throw OrgJsonReadException("No value for $key")) {
        is JsonNull      -> "null"
        is JsonPrimitive -> element.content
        else             -> element.toString()
    }

internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: throw OrgJsonReadException("$key is not an array")

internal fun JsonArray.obj(index: Int): JsonObject = this[index] as? JsonObject ?: throw OrgJsonReadException("Item $index is not an object")
