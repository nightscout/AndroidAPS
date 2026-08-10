package app.aaps.core.data.json

import app.aaps.core.data.json.OrgJsonCompat.hasCompat
import app.aaps.core.data.json.OrgJsonCompat.optBooleanCompat
import app.aaps.core.data.json.OrgJsonCompat.optDoubleCompat
import app.aaps.core.data.json.OrgJsonCompat.optIntCompat
import app.aaps.core.data.json.OrgJsonCompat.optJsonArrayCompat
import app.aaps.core.data.json.OrgJsonCompat.optJsonObjectCompat
import app.aaps.core.data.json.OrgJsonCompat.optLongCompat
import app.aaps.core.data.json.OrgJsonCompat.optStringCompat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * Golden master for [OrgJsonCompat], plus a written record of where the two libraries genuinely
 * disagree.
 *
 * Every read case below is parsed **twice**, once with `org.json` and once with kotlinx, and the two
 * results are required to match. `org.json` is the reference: whatever it does today is what the
 * replacement has to keep doing, quirks included. The accessor differences are invisible to the
 * compiler - if `optString` starts returning null for a missing key nothing fails to build, a
 * downstream `isEmpty()` just quietly takes the other branch - and this is what makes that visible.
 *
 * **The oracle is `org.json:json` from Maven, which is NOT byte for byte the same implementation as
 * Android's.** Android ships a Harmony derived `org.json`; Maven ships Crockford's. They agree on
 * every accessor exercised here, which is why this test is worth running, but do not extend it to
 * anything where the two implementations could reasonably differ (iteration order is the known one)
 * without checking on a device first.
 *
 * The second half is deliberately different in kind. It does not assert that the libraries agree -
 * it asserts that they do NOT, and pins exactly how. Those are the cases a migration has to shim or
 * accept, and a test that quietly skipped them would be worse than no test.
 */
class OrgJsonCompatParityTest {

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
        Case("integral double", """{"k":1.0}""", "k"),
        Case("boolean true", """{"k":true}""", "k"),
        Case("boolean false", """{"k":false}""", "k"),
        Case("nested object", """{"k":{"a":1}}""", "k"),
        Case("empty object", """{"k":{}}""", "k"),
        Case("array", """{"k":[1,2,3]}""", "k"),
        Case("empty array", """{"k":[]}""", "k")
    )

    private fun orgJson(case: Case) = JSONObject(case.json)
    private fun kotlinxJson(case: Case): JsonObject = Json.parseToJsonElement(case.json).jsonObject

    // ---------------------------------------------------------------- reads must match

    @Test
    fun `optString matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optStringCompat(case.key))
                .isEqualTo(orgJson(case).optString(case.key))
        }
    }

    @Test
    fun `optLong matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optLongCompat(case.key, 0L))
                .isEqualTo(orgJson(case).optLong(case.key, 0L))
        }
    }

    @Test
    fun `optLong honours a non zero fallback for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optLongCompat(case.key, -1L))
                .isEqualTo(orgJson(case).optLong(case.key, -1L))
        }
    }

    @Test
    fun `optDouble matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optDoubleCompat(case.key, 0.0))
                .isEqualTo(orgJson(case).optDouble(case.key, 0.0))
        }
    }

    @Test
    fun `optInt matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optIntCompat(case.key, 0))
                .isEqualTo(orgJson(case).optInt(case.key, 0))
        }
    }

    @Test
    fun `optBoolean matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optBooleanCompat(case.key))
                .isEqualTo(orgJson(case).optBoolean(case.key))
        }
    }

    @Test
    fun `has matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).hasCompat(case.key))
                .isEqualTo(orgJson(case).has(case.key))
        }
    }

    @Test
    fun `optJSONObject presence matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optJsonObjectCompat(case.key) != null)
                .isEqualTo(orgJson(case).optJSONObject(case.key) != null)
        }
    }

    @Test
    fun `optJSONArray presence matches org json for every case`() {
        cases.forEach { case ->
            assertWithMessage(case.name)
                .that(kotlinxJson(case).optJsonArrayCompat(case.key) != null)
                .isEqualTo(orgJson(case).optJSONArray(case.key) != null)
        }
    }

    // ---------------------------------------------------------------- writes differ
    //
    // Everything above says "these agree, rely on it". Everything below says "these do NOT agree,
    // and here is exactly how" - because the profile spine SERIALIZES (Profile.toPureNsJson feeds
    // Nightscout), and a value that reads back fine can still go onto the wire in a different shape.

    /**
     * The one that would actually change bytes sent to Nightscout.
     *
     * `org.json` renders a whole-numbered double WITHOUT a fractional part, so a basal rate of
     * `1.0` is uploaded as `1`. kotlinx keeps the double it was given and writes `1.0`. Both parse
     * back to the same number, so nothing round-trips wrong inside AAPS - but the uploaded document
     * is not byte identical, and anything comparing documents as text (a sync hash, a diff, a
     * strict consumer) would see every whole basal rate change.
     */
    @Test
    fun `whole numbered doubles serialize differently`() {
        val org = JSONObject().put("basal", 1.0).toString()
        val kotlinx = buildJsonObject { put("basal", JsonPrimitive(1.0)) }.toString()

        assertThat(org).isEqualTo("""{"basal":1}""")
        assertThat(kotlinx).isEqualTo("""{"basal":1.0}""")
        assertWithMessage("if this ever passes, the divergence is gone and the shim can drop it")
            .that(org).isNotEqualTo(kotlinx)
    }

    /** A non-whole double is written the same way by both, so only the integral case needs care. */
    @Test
    fun `fractional doubles serialize identically`() {
        val org = JSONObject().put("basal", 0.825).toString()
        val kotlinx = buildJsonObject { put("basal", JsonPrimitive(0.825)) }.toString()
        assertThat(org).isEqualTo(kotlinx)
    }

    /**
     * `org.json` refuses a non finite double outright; kotlinx accepts it and emits text that is not
     * valid JSON. A corrupt profile value therefore fails loudly today and would fail silently after
     * a naive migration - the receiver is what would break, and much later.
     */
    @Test
    fun `non finite doubles are rejected by org json but not by kotlinx`() {
        var orgThrew = false
        try {
            JSONObject().put("k", Double.NaN)
        } catch (_: Exception) {
            orgThrew = true
        }
        assertThat(orgThrew).isTrue()

        val kotlinx = buildJsonObject { put("k", JsonPrimitive(Double.NaN)) }.toString()
        assertThat(kotlinx).isEqualTo("""{"k":NaN}""")
    }

    /**
     * `org.json` accepts input that is not JSON - single quotes, unquoted keys - because it was
     * written to be forgiving. kotlinx rejects it unless explicitly made lenient.
     *
     * This matters for anything read from outside AAPS: a hand-edited profile, an old export, a
     * third party uploader. Strictness is better, but it is a behaviour change, not a free win.
     */
    @Test
    fun `org json accepts malformed input that kotlinx rejects`() {
        val malformed = """{k:"v"}"""   // unquoted key - not JSON

        assertThat(JSONObject(malformed).optString("k")).isEqualTo("v")

        var kotlinxThrew = false
        try {
            Json.parseToJsonElement(malformed)
        } catch (_: Exception) {
            kotlinxThrew = true
        }
        assertWithMessage("kotlinx must reject what org.json tolerated").that(kotlinxThrew).isTrue()

        // ...and it can be made to accept it, if a call site turns out to need that.
        val lenient = Json { isLenient = true }
        assertThat(lenient.parseToJsonElement(malformed).jsonObject.optStringCompat("k")).isEqualTo("v")
    }

    /**
     * kotlinx preserves insertion order; this `org.json` does not guarantee it.
     *
     * Pinned only as "kotlinx is ordered", NOT as a comparison, because iteration order is exactly
     * where the Maven and Android implementations are allowed to differ - asserting the oracle's
     * order here would pin the wrong library's behaviour.
     */
    @Test
    fun `kotlinx preserves insertion order`() {
        val doc = buildJsonObject {
            put("z", JsonPrimitive(1))
            put("a", JsonPrimitive(2))
            put("m", JsonPrimitive(3))
        }
        assertThat(doc.keys.toList()).containsExactly("z", "a", "m").inOrder()
        assertThat(doc.toString()).isEqualTo("""{"z":1,"a":2,"m":3}""")
    }
}
