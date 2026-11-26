package app.aaps.core.nssdk

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal

/**
 * Does to a JSON document what Nightscout does to it: Nightscout runs on JavaScript, which reads every
 * number as a double (`JSON.parse`) and writes it back (`JSON.stringify`). A whole number above 2^53 comes
 * back rounded: [Long.MAX_VALUE] as 9223372036854776000, more than a Long holds.
 *
 * JavaScript writes a number below 10^21 with the shortest digits that read back as the same double, then
 * zeros instead of an exponent. Kotlin's [Double.toString] gives the same shortest digits, in another
 * notation, which [BigDecimal] turns into the plain one. `JavaScriptJsonTest` checks this against the output
 * of real Node.
 */
internal fun throughJavaScript(json: String): String = asJavaScript(Json.parseToJsonElement(json)).toString()

@OptIn(ExperimentalSerializationApi::class)
private fun asJavaScript(element: JsonElement): JsonElement = when (element) {
    is JsonObject    -> JsonObject(element.mapValues { asJavaScript(it.value) })
    is JsonArray     -> JsonArray(element.map(::asJavaScript))
    is JsonNull      -> element
    is JsonPrimitive ->
        if (element.isString || element.booleanOrNull != null) element
        else JsonUnquotedLiteral(BigDecimal(element.content.toDouble().toString()).stripTrailingZeros().toPlainString())
}
