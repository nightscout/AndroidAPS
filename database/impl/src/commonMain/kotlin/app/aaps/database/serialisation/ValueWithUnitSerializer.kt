package app.aaps.database.serialisation

import app.aaps.database.entities.RunningMode
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.ValueWithUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Reads and writes the `userEntry.values` column exactly as the Gson version did.
 *
 * The shape is a single key object, the key being the Kotlin simple name of the subtype:
 * `{"Insulin":{"value":1.25}}`, with `UNKNOWN` as an empty object. Nobody designed that - it fell out
 * of Gson reflecting over the sealed class - but years of rows are stored in it, so it is reproduced
 * here rather than improved. `UserEntryValuesFormatTest` pins every subtype against literals captured
 * from the Gson implementation.
 *
 * Anything unrecognised becomes [ValueWithUnit.UNKNOWN] instead of throwing, which is what the Gson
 * adapter did and what keeps a downgrade working: a build that predates a new subtype still opens the
 * database, it just cannot show that one value.
 */
internal object ValueWithUnitSerializer : KSerializer<ValueWithUnit> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("app.aaps.database.entities.ValueWithUnit")

    override fun serialize(encoder: Encoder, value: ValueWithUnit) {
        val body = when (value) {
            is ValueWithUnit.UNKNOWN               -> buildJsonObject { }
            is ValueWithUnit.SimpleString          -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.SimpleInt             -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Mgdl                  -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Mmoll                 -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Timestamp             -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Insulin               -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.InsulinConcentration  -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.UnitPerHour           -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Gram                  -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Minute                -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Hour                  -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.Percent               -> buildJsonObject { put("value", value.value) }
            is ValueWithUnit.TherapyEventType      -> buildJsonObject { put("value", value.value.name) }
            is ValueWithUnit.TherapyEventMeterType -> buildJsonObject { put("value", value.value.name) }
            is ValueWithUnit.TherapyEventArrow     -> buildJsonObject { put("value", value.value.name) }
            is ValueWithUnit.TherapyEventLocation  -> buildJsonObject { put("value", value.value.name) }
            is ValueWithUnit.TherapyEventTTReason  -> buildJsonObject { put("value", value.value.name) }
            is ValueWithUnit.RunningModeMode       -> buildJsonObject { put("value", value.value.name) }
        }
        (encoder as JsonEncoder).encodeJsonElement(buildJsonObject { put(nameOf(value), body) })
    }

    override fun deserialize(decoder: Decoder): ValueWithUnit {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val entry = (element as? JsonObject)?.entries?.firstOrNull() ?: return ValueWithUnit.UNKNOWN
        val value = (entry.value as? JsonObject)?.get("value")
        return runCatching { read(entry.key, value) }.getOrDefault(ValueWithUnit.UNKNOWN)
    }

    private fun read(name: String, value: JsonElement?): ValueWithUnit = when (name) {
        "SimpleString"          -> ValueWithUnit.SimpleString(value!!.jsonPrimitive.content)
        "SimpleInt"             -> ValueWithUnit.SimpleInt(value!!.jsonPrimitive.int)
        "Mgdl"                  -> ValueWithUnit.Mgdl(value!!.jsonPrimitive.double)
        "Mmoll"                 -> ValueWithUnit.Mmoll(value!!.jsonPrimitive.double)
        "Timestamp"             -> ValueWithUnit.Timestamp(value!!.jsonPrimitive.long)
        "Insulin"               -> ValueWithUnit.Insulin(value!!.jsonPrimitive.double)
        "InsulinConcentration"  -> ValueWithUnit.InsulinConcentration(value!!.jsonPrimitive.int)
        "UnitPerHour"           -> ValueWithUnit.UnitPerHour(value!!.jsonPrimitive.double)
        "Gram"                  -> ValueWithUnit.Gram(value!!.jsonPrimitive.int)
        "Minute"                -> ValueWithUnit.Minute(value!!.jsonPrimitive.int)
        "Hour"                  -> ValueWithUnit.Hour(value!!.jsonPrimitive.int)
        "Percent"               -> ValueWithUnit.Percent(value!!.jsonPrimitive.int)
        "TherapyEventType"      -> ValueWithUnit.TherapyEventType(TherapyEvent.Type.valueOf(value!!.jsonPrimitive.content))
        "TherapyEventMeterType" -> ValueWithUnit.TherapyEventMeterType(TherapyEvent.MeterType.valueOf(value!!.jsonPrimitive.content))
        "TherapyEventArrow"     -> ValueWithUnit.TherapyEventArrow(TherapyEvent.Arrow.valueOf(value!!.jsonPrimitive.content))
        "TherapyEventLocation"  -> ValueWithUnit.TherapyEventLocation(TherapyEvent.Location.valueOf(value!!.jsonPrimitive.content))
        "TherapyEventTTReason"  -> ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.valueOf(value!!.jsonPrimitive.content))
        "RunningModeMode"       -> ValueWithUnit.RunningModeMode(RunningMode.Mode.valueOf(value!!.jsonPrimitive.content))
        else                    -> ValueWithUnit.UNKNOWN
    }

    /**
     * The stored key. Written out rather than read from reflection, so that it survives obfuscation and
     * so renaming a subtype is a compile error here instead of a silent change to the stored format.
     */
    private fun nameOf(value: ValueWithUnit): String = when (value) {
        is ValueWithUnit.UNKNOWN               -> "UNKNOWN"
        is ValueWithUnit.SimpleString          -> "SimpleString"
        is ValueWithUnit.SimpleInt             -> "SimpleInt"
        is ValueWithUnit.Mgdl                  -> "Mgdl"
        is ValueWithUnit.Mmoll                 -> "Mmoll"
        is ValueWithUnit.Timestamp             -> "Timestamp"
        is ValueWithUnit.Insulin               -> "Insulin"
        is ValueWithUnit.InsulinConcentration  -> "InsulinConcentration"
        is ValueWithUnit.UnitPerHour           -> "UnitPerHour"
        is ValueWithUnit.Gram                  -> "Gram"
        is ValueWithUnit.Minute                -> "Minute"
        is ValueWithUnit.Hour                  -> "Hour"
        is ValueWithUnit.Percent               -> "Percent"
        is ValueWithUnit.TherapyEventType      -> "TherapyEventType"
        is ValueWithUnit.TherapyEventMeterType -> "TherapyEventMeterType"
        is ValueWithUnit.TherapyEventArrow     -> "TherapyEventArrow"
        is ValueWithUnit.TherapyEventLocation  -> "TherapyEventLocation"
        is ValueWithUnit.TherapyEventTTReason  -> "TherapyEventTTReason"
        is ValueWithUnit.RunningModeMode       -> "RunningModeMode"
    }
}
