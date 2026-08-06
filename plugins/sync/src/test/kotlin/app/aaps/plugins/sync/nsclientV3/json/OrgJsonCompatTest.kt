package app.aaps.plugins.sync.nsclientV3.json

import app.aaps.plugins.sync.nsclientV3.json.OrgJsonCompat.optBooleanCompat
import app.aaps.plugins.sync.nsclientV3.json.OrgJsonCompat.optJsonArrayCompat
import app.aaps.plugins.sync.nsclientV3.json.OrgJsonCompat.optJsonObjectCompat
import app.aaps.plugins.sync.nsclientV3.json.OrgJsonCompat.optLongCompat
import app.aaps.plugins.sync.nsclientV3.json.OrgJsonCompat.optStringCompat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * Golden master test for [OrgJsonCompat].
 *
 * Every case below is parsed **twice**, once with `org.json` and once with kotlinx, and the two
 * results are required to match. `org.json` is the reference: whatever it does today is what the
 * replacement has to keep doing, quirks included.
 *
 * The point is that the accessor differences are invisible to the compiler. If `optString` starts
 * returning null for a missing key nothing fails to build - a downstream `isEmpty()` just quietly
 * takes the other branch. This test is what makes that visible.
 */
class OrgJsonCompatTest {

    /** One row of the matrix: a document, and the key to read out of it. */
    private data class Case(val name: String, val json: String, val key: String)

    private val cases = listOf(
        Case("missing key", """{"other":1}""", "k"),
        Case("explicit null", """{"k":null}""", "k"),
        Case("plain string", """{"k":"hello"}""", "k"),
        Case("empty string", """{"k":""}""", "k"),
        Case("string with spaces", """{"k":"a b c"}""", "k"),
        Case("string that looks numeric", """{"k":"1785992181588"}""", "k"),
        Case("string true", """{"k":"true"}""", "k"),
        Case("string TRUE upper", """{"k":"TRUE"}""", "k"),
        Case("string false", """{"k":"false"}""", "k"),
        Case("int", """{"k":42}""", "k"),
        Case("zero", """{"k":0}""", "k"),
        Case("negative int", """{"k":-6}""", "k"),
        Case("big long", """{"k":1785992181588}""", "k"),
        Case("double", """{"k":0.692}""", "k"),
        Case("negative double", """{"k":-0.411}""", "k"),
        Case("boolean true", """{"k":true}""", "k"),
        Case("boolean false", """{"k":false}""", "k"),
        Case("nested object", """{"k":{"a":1}}""", "k"),
        Case("empty object", """{"k":{}}""", "k"),
        Case("array", """{"k":[1,2,3]}""", "k"),
        Case("empty array", """{"k":[]}""", "k")
    )

    private fun orgJson(case: Case) = JSONObject(case.json)
    private fun kotlinxJson(case: Case): JsonObject = Json.parseToJsonElement(case.json).jsonObject

    // ---------------------------------------------------------------- the matrix

    @Test
    fun `optString matches org json for every case`() {
        for (case in cases)
            assertWithMessage("optString - %s", case.name)
                .that(kotlinxJson(case).optStringCompat(case.key))
                .isEqualTo(orgJson(case).optString(case.key))
    }

    @Test
    fun `optLong matches org json for every case`() {
        for (case in cases)
            assertWithMessage("optLong 0 - %s", case.name)
                .that(kotlinxJson(case).optLongCompat(case.key, 0L))
                .isEqualTo(orgJson(case).optLong(case.key, 0L))
    }

    @Test
    fun `optLong honours a non zero fallback for every case`() {
        for (case in cases)
            assertWithMessage("optLong -1 - %s", case.name)
                .that(kotlinxJson(case).optLongCompat(case.key, -1L))
                .isEqualTo(orgJson(case).optLong(case.key, -1L))
    }

    @Test
    fun `optBoolean matches org json for every case`() {
        for (case in cases)
            assertWithMessage("optBoolean - %s", case.name)
                .that(kotlinxJson(case).optBooleanCompat(case.key))
                .isEqualTo(orgJson(case).optBoolean(case.key))
    }

    @Test
    fun `optJSONObject presence matches org json for every case`() {
        for (case in cases) {
            val expected = orgJson(case).optJSONObject(case.key)
            val actual = kotlinxJson(case).optJsonObjectCompat(case.key)

            assertWithMessage("optJSONObject present - %s", case.name)
                .that(actual != null).isEqualTo(expected != null)
            if (expected != null && actual != null)
                assertWithMessage("optJSONObject keys - %s", case.name)
                    .that(actual.keys).isEqualTo(expected.keys().asSequence().toSet())
        }
    }

    @Test
    fun `optJSONArray presence matches org json for every case`() {
        for (case in cases) {
            val expected = orgJson(case).optJSONArray(case.key)
            val actual = kotlinxJson(case).optJsonArrayCompat(case.key)

            assertWithMessage("optJSONArray present - %s", case.name)
                .that(actual != null).isEqualTo(expected != null)
            if (expected != null && actual != null)
                assertWithMessage("optJSONArray size - %s", case.name)
                    .that(actual.size).isEqualTo(expected.length())
        }
    }

    // ---------------------------------------------------------------- real call site shapes

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
