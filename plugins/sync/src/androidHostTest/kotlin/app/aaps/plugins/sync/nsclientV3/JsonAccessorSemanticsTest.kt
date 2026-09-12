package app.aaps.plugins.sync.nsclientV3

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * Pins the exact semantics of the `org.json` accessors the NSClientV3 code relies on.
 *
 * The wire layer is moving off `org.json.JSONObject` to `kotlinx.serialization.json.JsonObject`,
 * because `org.json` is a JVM/Android API and cannot go to iOS. The two libraries do **not** behave
 * the same for a missing key, and nothing about that difference shows up at compile time:
 *
 * ```
 * doc.optString("k")                  // org.json  : "" when absent, never null
 * doc["k"]?.jsonPrimitive?.content    // kotlinx   : null when absent
 * ```
 *
 * So a straight translation silently flips every downstream `isEmpty()` check and `?:` fallback.
 * These tests record what the current code actually gets, so the replacement can be checked against
 * it instead of against an assumption.
 *
 * They also confirm **which** `org.json` is on the unit test classpath. The module sets
 * `isReturnDefaultValues = true`, which would make the `android.jar` stub return null/0/false from
 * every method - that would make every JSONObject-based test in this module meaningless. The real
 * implementation arrives transitively through `io.socket:socket.io-client`. If these tests ever
 * start failing with nulls and zeroes, that transitive dependency is gone and a real `org.json:json`
 * test dependency is needed.
 */
class JsonAccessorSemanticsTest {

    // ---------------------------------------------------------------- optString

    /**
     * The important one. `optString` returns a **non-null** `String`, so a missing key gives `""`.
     *
     * This is what makes `NSClientV3Service.onDataDelete` read
     * `response.optString("colName") ?: return@Listener` - Kotlin sees the Java platform type
     * `String!` and allows the elvis without a warning, but it can never fire. See
     * `deadElvisOnOptString` below.
     */
    @Test
    fun `optString returns empty string for a missing key, not null`() {
        val doc = JSONObject("""{"present":"value"}""")

        assertThat(doc.optString("present")).isEqualTo("value")
        assertThat(doc.optString("missing")).isEqualTo("")
        assertThat(doc.optString("missing")).isNotNull()
    }

    /**
     * An explicit JSON null is **not** the same as a missing key, and it is not Kotlin null either -
     * it comes back as the four letter text `null`.
     *
     * This is a landmine for the migration. `NSClientV3Service` logs
     * `data.optString("message")` straight into the NSClient log, so `{"message":null}` from the
     * server currently writes the literal word "null" into the user visible log. kotlinx would give
     * Kotlin null there instead.
     *
     * The two org.json implementations disagree here: this is the behaviour of the one Android
     * ships (`JSON.toString(JSONObject.NULL)` -> `"null"`). Crockford's reference implementation
     * returns the fallback `""` instead. So this assertion also records *which* implementation the
     * unit tests run against.
     */
    @Test
    fun `optString on an explicit null gives the four letter text null`() {
        val doc = JSONObject("""{"nulled":null}""")

        assertThat(doc.optString("nulled")).isEqualTo("null")
        assertThat(doc.optString("nulled")).hasLength(4)
    }

    /**
     * The dead branch in `NSClientV3Service.onDataDelete`, isolated.
     *
     * After the move to kotlinx this elvis would start firing, which **changes behaviour**: today a
     * delete event with no `colName` carries on with an empty collection name and quietly matches
     * none of the `if (collection == ...)` branches. Fix it on purpose or preserve it on purpose,
     * but do not let a rewrite decide it by accident.
     */
    @Test
    fun `elvis after optString is dead code - current behaviour, pinned`() {
        val doc = JSONObject("""{"identifier":"abc"}""")

        var elvisFired = false
        val collection = doc.optString("colName") ?: run { elvisFired = true; "bailed" }

        assertThat(elvisFired).isFalse()
        assertThat(collection).isEqualTo("")
    }

    // ---------------------------------------------------------------- optJSONObject

    /** Unlike `optString`, this one really does return null, so the `?:` guards on it are live. */
    @Test
    fun `optJSONObject returns null for a missing key`() {
        val doc = JSONObject("""{"envelope":{"a":1}}""")

        assertThat(doc.optJSONObject("envelope")).isNotNull()
        assertThat(doc.optJSONObject("missing")).isNull()
    }

    /** A key holding a non-object gives null rather than throwing. */
    @Test
    fun `optJSONObject returns null when the value is not an object`() {
        val doc = JSONObject("""{"notAnObject":"plain string"}""")

        assertThat(doc.optJSONObject("notAnObject")).isNull()
    }

    // ---------------------------------------------------------------- optLong / optBoolean

    /** `LoadSettingsWorker` and `onDataCreateUpdate` both pass an explicit 0 default. */
    @Test
    fun `optLong falls back to the supplied default`() {
        val doc = JSONObject("""{"srvModified":1785992181588}""")

        assertThat(doc.optLong("srvModified", 0L)).isEqualTo(1785992181588L)
        assertThat(doc.optLong("missing", 0L)).isEqualTo(0L)
    }

    /** A string that looks like a number is coerced, it does not fall back to the default. */
    @Test
    fun `optLong coerces a numeric string`() {
        val doc = JSONObject("""{"srvModified":"1785992181588"}""")

        assertThat(doc.optLong("srvModified", 0L)).isEqualTo(1785992181588L)
    }

    /** Used for the socket.io subscribe/auth replies in `NSClientV3Service`. */
    @Test
    fun `optBoolean returns false for a missing key`() {
        val doc = JSONObject("""{"success":true}""")

        assertThat(doc.optBoolean("success")).isTrue()
        assertThat(doc.optBoolean("missing")).isFalse()
    }

    // ---------------------------------------------------------------- classpath guard

    /**
     * Proves the real `org.json` is on the test classpath and not the `android.jar` stub.
     *
     * With the stub plus `isReturnDefaultValues = true` every call here would return null / 0 /
     * false and the assertions above would be vacuous.
     */
    @Test
    fun `the real org json implementation is on the test classpath`() {
        val doc = JSONObject("""{"a":1,"b":"two"}""")

        assertThat(doc.length()).isEqualTo(2)
        assertThat(doc.getInt("a")).isEqualTo(1)
        assertThat(doc.getString("b")).isEqualTo("two")
        assertThat(doc.has("a")).isTrue()
        assertThat(doc.toString()).contains("\"a\"")
    }
}
