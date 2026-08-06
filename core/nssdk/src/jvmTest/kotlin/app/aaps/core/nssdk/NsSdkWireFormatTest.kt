package app.aaps.core.nssdk

import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.nssdk.remotemodel.RemoteTreatment
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Pins what AAPS puts **on the wire**, not what it reads off it.
 *
 * Backward compatibility depends on this side. An AAPS instance uploads documents that are read by
 * Nightscout's own validation, by other uploaders, and by AAPS clients running an older version -
 * none of which update at the same time as this app. A field that silently stops being written is
 * not a local problem, it is a problem on every reader that has not been updated.
 *
 * The specific hazard is that kotlinx's default is the **opposite** of Gson's. Gson writes every
 * non-null field. kotlinx omits any field whose value equals its default unless `encodeDefaults` is
 * on, so `0`, `false` and every field that was given a `= null` default would quietly disappear.
 * See [app.aaps.core.nssdk.nsSdkJson].
 */
class NsSdkWireFormatTest {

    private fun encoded(treatment: RemoteTreatment) =
        Json.parseToJsonElement(nsSdkJson.encodeToString(RemoteTreatment.serializer(), treatment)).jsonObject

    // ---------------------------------------------------------------- field names

    /** The wire names are the `@SerialName` ones, not the Kotlin property names. */
    @Test
    fun `wire names are used, not property names`() {
        val json = encoded(
            RemoteTreatment(
                eventType = EventType.CORRECTION_BOLUS,
                date = 1785992179555,
                created_at = "2026-08-06T04:56:19.555Z",
                utcOffset = 120,
                insulin = 0.25,
                isValid = true,
                isReadOnly = false
            )
        )

        assertThat(json.keys).containsAtLeast("eventType", "date", "created_at", "utcOffset", "insulin", "isValid", "isReadOnly")
        // the Kotlin name must not leak onto the wire
        assertThat(json.keys).doesNotContain("createdAt")
    }

    // ---------------------------------------------------------------- values that equal a default

    /**
     * The regression this file exists for. `false` and `0` are real values a reader needs, and both
     * are also the natural default for their type - exactly what kotlinx would drop.
     */
    @Test
    fun `false and zero are written, not omitted`() {
        val json = encoded(
            RemoteTreatment(
                eventType = EventType.TEMPORARY_TARGET,
                date = 1785994614220,
                isValid = true,
                isReadOnly = false,      // false, not absent
                utcOffset = 0,           // zero, not absent
                duration = 0             // zero, not absent
            )
        )

        assertThat(json.keys).containsAtLeast("isReadOnly", "utcOffset", "duration")
        assertThat(json["isReadOnly"]?.jsonPrimitive?.content).isEqualTo("false")
        assertThat(json["utcOffset"]?.jsonPrimitive?.content).isEqualTo("0")
        assertThat(json["duration"]?.jsonPrimitive?.content).isEqualTo("0")
    }

    /** Same hazard on a model whose defaults are non-null: every counter must be written. */
    @Test
    fun `zero collection timestamps are written`() {
        val json = Json.parseToJsonElement(
            nsSdkJson.encodeToString(LastModified.serializer(), LastModified(LastModified.Collections()))
        ).jsonObject["collections"]!!.jsonObject

        assertThat(json.keys).containsExactly("devicestatus", "entries", "profile", "treatments", "foods", "settings")
        assertThat(json["devicestatus"]?.jsonPrimitive?.content).isEqualTo("0")
        assertThat(json["treatments"]?.jsonPrimitive?.content).isEqualTo("0")
    }

    // ---------------------------------------------------------------- nulls stay off the wire

    /**
     * The other half of matching Gson: a null field is omitted rather than written as
     * `"field": null`. Nightscout validation rejects some explicit nulls, so this is not cosmetic.
     */
    @Test
    fun `null fields are omitted rather than written as null`() {
        val json = encoded(RemoteTreatment(eventType = EventType.CARBS_CORRECTION, date = 1785987360000, carbs = 4.0))

        assertThat(json.keys).containsAtLeast("eventType", "date", "carbs")
        assertThat(json.keys).doesNotContain("insulin")
        assertThat(json.keys).doesNotContain("notes")
        assertThat(json.keys).doesNotContain("pumpSerial")
    }

    // ---------------------------------------------------------------- enums

    /** Enums go out as their Nightscout text, not as the Kotlin constant name. */
    @Test
    fun `event types are written as their Nightscout text`() {
        assertThat(encoded(RemoteTreatment(eventType = EventType.CORRECTION_BOLUS))["eventType"]?.jsonPrimitive?.content)
            .isEqualTo("Correction Bolus")
        assertThat(encoded(RemoteTreatment(eventType = EventType.TEMPORARY_TARGET))["eventType"]?.jsonPrimitive?.content)
            .isEqualTo("Temporary Target")
        assertThat(encoded(RemoteTreatment(eventType = EventType.TEMPORARY_BASAL))["eventType"]?.jsonPrimitive?.content)
            .isEqualTo("Temp Basal")
    }

    // ---------------------------------------------------------------- numbers

    /** A whole number in a Double field keeps its decimal point, as Gson wrote it. */
    @Test
    fun `numbers keep their form`() {
        val json = encoded(RemoteTreatment(eventType = EventType.CARBS_CORRECTION, carbs = 4.0, insulin = 0.25))

        assertThat(json["carbs"]?.jsonPrimitive?.content).isEqualTo("4.0")
        assertThat(json["insulin"]?.jsonPrimitive?.content).isEqualTo("0.25")
    }

    // ---------------------------------------------------------------- round trip against a real record

    /**
     * A real uploaded record, parsed and written back out. Every field the wire model knows about has
     * to come back with the same value - this is what an older reader will be looking at.
     */
    @Test
    fun `a real record survives being read and written again`() {
        val original = """
            {"app":"AAPS","date":1785992179555,"eventType":"Correction Bolus","insulin":0.25,
             "isBasalInsulin":false,"isReadOnly":false,"isValid":true,"pumpId":1785992181495,
             "pumpSerial":"TESTSERIAL","pumpType":"DANA_RS","type":"SMB","utcOffset":120,
             "created_at":"2026-08-06T04:56:19.555Z","identifier":"1bebd84b-8d61-58b2-a967-1b34a1e8c4d4"}
        """.trimIndent()

        val parsed = nsSdkJson.decodeFromString(RemoteTreatment.serializer(), original)
        val written = encoded(parsed)
        val before = Json.parseToJsonElement(original).jsonObject

        for (key in before.keys)
            assertThat(written[key]).isEqualTo(before[key])
    }
}
