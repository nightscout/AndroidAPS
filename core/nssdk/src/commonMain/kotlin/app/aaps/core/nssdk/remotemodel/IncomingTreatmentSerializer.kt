package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

internal object IncomingTreatmentSerializer : JsonTransformingSerializer<RemoteTreatment>(RemoteTreatment.serializer()) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val row = element as? JsonObject ?: return element
        // JavaScript serializes the Long.MAX_VALUE sentinel for active, auto-forced LGS as this number.
        val duration = row["originalDuration"] as? JsonPrimitive ?: return row
        if (duration.isString || duration.content != "9223372036854776000" ||
            row["eventType"] != JsonPrimitive("OpenAPS Offline") || row["mode"] != JsonPrimitive("CLOSED_LOOP_LGS") ||
            row["autoForced"] != JsonPrimitive(true)
        ) return row

        return JsonObject(row + ("originalDuration" to JsonPrimitive(Long.MAX_VALUE)))
    }
}
