package app.aaps.plugins.sync.nsclientV3.json

import app.aaps.core.data.json.OrgJsonCompat.optBooleanCompat
import app.aaps.core.data.json.OrgJsonCompat.optJsonObjectCompat
import app.aaps.core.data.json.OrgJsonCompat.optLongCompat
import app.aaps.core.data.json.OrgJsonCompat.optStringCompat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * NSClientV3 specific half of the `OrgJsonCompat` checks.
 *
 * The accessor-by-accessor matrix moved to `OrgJsonCompatParityTest` in `:core:data`, next to the
 * shim itself, when the shim was shared so `:core:interfaces` could use it. What stays here is what
 * is genuinely about **this** module: the document shapes NSClientV3 actually reads, and the two
 * call-site behaviours that would break quietly.
 */
class OrgJsonCompatTest {

    /**
     * The shapes the NSClientV3 code actually reads, taken from the live call sites, checked against
     * `org.json` end to end rather than one accessor at a time.
     */
    @Test
    fun `real NSClient document shapes match org json`() {
        val documents = listOf(
            """{"colName":"treatments","identifier":"abc123"}""",
            """{"colName":"entries"}""",
            """{"identifier":"abc123"}""",
            """{}""",
            """{"success":true,"collections":"treatments entries"}""",
            """{"success":false,"message":"not authorised"}""",
            """{"message":null,"level":"urgent","title":"Alarm"}""",
            """{"srvModified":1785992181588,"identifier":"cold"}""",
            """{"envelope":{"cmd":"bolus","amount":1.5},"identifier":"ctrl_1"}""",
            """{"ack":{"ok":true},"progress":{"percent":40}}"""
        )
        val keys = listOf(
            "colName", "identifier", "success", "collections", "message",
            "level", "title", "srvModified", "envelope", "ack", "progress"
        )

        for (document in documents) {
            val reference = JSONObject(document)
            val candidate = Json.parseToJsonElement(document).jsonObject
            for (key in keys) {
                // Skip optString on object valued keys - see `optString on an object differs only
                // in key order` below. No call site reads an object through optString.
                if (reference.optJSONObject(key) == null)
                    assertWithMessage("optString(%s) of %s", key, document)
                        .that(candidate.optStringCompat(key)).isEqualTo(reference.optString(key))
                assertWithMessage("optLong(%s) of %s", key, document)
                    .that(candidate.optLongCompat(key, 0L)).isEqualTo(reference.optLong(key, 0L))
                assertWithMessage("optBoolean(%s) of %s", key, document)
                    .that(candidate.optBooleanCompat(key)).isEqualTo(reference.optBoolean(key))
                assertWithMessage("optJSONObject(%s) of %s", key, document)
                    .that(candidate.optJsonObjectCompat(key) != null)
                    .isEqualTo(reference.optJSONObject(key) != null)
            }
        }
    }

    /**
     * The one place the two libraries genuinely disagree, recorded on purpose.
     *
     * Reading an **object** through `optString` serialises the subtree, and `org.json` iterates a
     * hash map, so its key order is unspecified and does not follow the source text. kotlinx keeps
     * source order. Asserting equality of those two strings would be pinning nondeterminism, so the
     * matrix skips this combination.
     *
     * It is safe to skip because no call site does it: all six keys read through `optString`
     * (`identifier`, `colName`, `collections`, `message`, `level`, `title`) hold strings. Object
     * valued keys are read through `optJSONObject`, which agrees exactly.
     *
     * What must still hold is that neither side loses anything - both produce text that parses back
     * to the same object.
     */
    @Test
    fun `optString on an object differs only in key order`() {
        val document = """{"envelope":{"cmd":"bolus","amount":1.5,"id":"x"}}"""
        val reference = JSONObject(document).optString("envelope")
        val candidate = Json.parseToJsonElement(document).jsonObject.optStringCompat("envelope")

        // Same content, reparsed - order is not part of the contract.
        assertThat(Json.parseToJsonElement(candidate).jsonObject)
            .isEqualTo(Json.parseToJsonElement(reference).jsonObject)
    }

    /**
     * `startsWith` on the result is how `NSClientV3Service` routes client control documents. With
     * `org.json` the receiver is never null, so this cannot throw - the replacement has to keep that.
     */
    @Test
    fun `startsWith on a missing identifier does not throw`() {
        val candidate = Json.parseToJsonElement("""{"other":1}""").jsonObject

        val identifier = candidate.optStringCompat("identifier")
        assertThat(identifier).isEqualTo("")
        assertThat(identifier.startsWith("ctrl_")).isFalse()
    }
}
