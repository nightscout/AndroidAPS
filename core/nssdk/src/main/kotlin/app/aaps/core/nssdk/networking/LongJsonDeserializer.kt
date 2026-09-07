package app.aaps.core.nssdk.networking

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class LongJsonDeserializer : JsonDeserializer<Long?> {

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Long? =
        json.toLongOrNull("Long value")
}

internal fun JsonElement?.toLongOrNull(name: String): Long? {
    if (this == null || isJsonNull) return null
    if (!isJsonPrimitive) throw JsonParseException("$name is not a number")

    return try {
        val value = asString.toBigDecimal()
        if (value <= MIN_LONG_EXCLUSIVE || value >= MAX_LONG_EXCLUSIVE) {
            throw ArithmeticException()
        }
        value.toLong()
    } catch (error: NumberFormatException) {
        throw JsonParseException("$name is not a number", error)
    } catch (error: ArithmeticException) {
        throw JsonParseException("$name is outside the Long range", error)
    }
}

private val MIN_LONG_EXCLUSIVE = "-9223372036854775809".toBigDecimal()
private val MAX_LONG_EXCLUSIVE = "9223372036854775808".toBigDecimal()
