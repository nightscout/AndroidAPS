package app.aaps.pump.carelevo.data.common

import app.aaps.pump.carelevo.domain.type.AlarmCause
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * The JSON of every record this driver stores. It writes and reads exactly what the Gson code before it did, so the
 * records on a phone keep loading and an older build can still read new ones (`CarelevoStoredJsonGoldenTest`):
 * - a null field is left out, every other field is written, also one that has its default value;
 * - a field this build does not know is skipped;
 * - NaN and infinity are kept, as Gson's `serializeSpecialFloatingPointValues` did.
 */
internal val carelevoJson: Json = Json {
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
}

/**
 * An [AlarmCause] by its name, as Gson wrote it. A name this build does not know, from a newer build before a
 * downgrade, reads as [AlarmCause.ALARM_UNKNOWN], so it does not make the whole stored alarm list unreadable.
 */
internal object AlarmCauseByNameSerializer : KSerializer<AlarmCause> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.aaps.pump.carelevo.AlarmCause", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AlarmCause) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): AlarmCause {
        val name = decoder.decodeString()
        return AlarmCause.entries.firstOrNull { it.name == name } ?: AlarmCause.ALARM_UNKNOWN
    }
}
