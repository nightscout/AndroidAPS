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

/**
 * The largest whole number a JavaScript number holds exactly, `Number.MAX_SAFE_INTEGER` (2^53 - 1).
 * Nightscout runs on JavaScript and keeps every number as a double, so a larger whole number comes back
 * rounded.
 */
internal const val JS_MAX_SAFE_INTEGER = 9_007_199_254_740_991L

/**
 * A duration on the Nightscout wire, written so that it survives JavaScript.
 *
 * AAPS keeps [Long.MAX_VALUE] for "no end": the temporary target and the therapy event of a scene without
 * a duration, a permanent temporary target, a running mode forced by a constraint. Nightscout stored that
 * as a double and gave it back as 9223372036854776000, more than a [Long] holds. Reading it failed, and
 * with it the whole page of treatments, so a new install or a client stopped loading the history there
 * (#5626).
 *
 * - Written: a duration above [JS_MAX_SAFE_INTEGER] goes out as [JS_MAX_SAFE_INTEGER], which JavaScript
 *   keeps exactly. That is about 285000 years, so Nightscout still shows the entry without an end.
 * - Read: anything from [JS_MAX_SAFE_INTEGER] up, also a number beyond the [Long] range that an older AAPS
 *   left in Nightscout, is [Long.MAX_VALUE] again. Exactly that, not a large finite value: a duration that
 *   comes from Nightscout may only shorten an entry (`SyncNsTemporaryTargetTransaction` and the other
 *   sync transactions), so the master would otherwise cut its own endless entries when they come back.
 *
 * A number sent as a string is read as before (`isLenient` in `nsSdkJson`).
 */
internal object WireDurationSerializer : KSerializer<Long> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.aaps.core.nssdk.WireDuration", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value.coerceAtMost(JS_MAX_SAFE_INTEGER))

    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return fromWire(decoder.decodeLong())
        val element = decoder.decodeJsonElement()
        val text = (element as? JsonPrimitive)?.content ?: throw SerializationException("A duration must be a number, not $element")
        text.toLongOrNull()?.let { return fromWire(it) }
        // Not a Long: a number too large for one (9223372036854776000), or the same in another notation.
        // Only the rounded "no end" can be that large; anything else stays an error, as before.
        val number = text.toDoubleOrNull()
        if (number != null && number >= JS_MAX_SAFE_INTEGER) return Long.MAX_VALUE
        throw SerializationException("A duration must be a whole number, not '$text'")
    }

    private fun fromWire(value: Long): Long = if (value >= JS_MAX_SAFE_INTEGER) Long.MAX_VALUE else value
}
