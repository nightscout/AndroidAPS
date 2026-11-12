package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * An Int that also accepts a decimal number, rounded to the nearest whole number.
 *
 * Nightscout collections are shared: other clients can write `"carbs": 10.5` or `"carbs": 12.0` where
 * AAPS keeps a whole number. The default Int decoder throws on those, and as a collection is decoded
 * as one list, one such document loses the whole page. Written back, the value is a plain Int, so
 * the upload format does not change.
 */
internal object RoundedIntSerializer : KSerializer<Int> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.aaps.core.nssdk.RoundedInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val element = decoder.decodeJsonElement()
        val text = (element as? JsonPrimitive)?.content
        val value = text?.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: throw SerializationException("Expected a number, found $element")
        return value.roundToInt()
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
