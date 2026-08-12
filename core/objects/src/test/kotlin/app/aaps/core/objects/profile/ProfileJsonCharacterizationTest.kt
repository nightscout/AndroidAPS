package app.aaps.core.objects.profile

import android.content.Context
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.blockFromJsonArray
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

/**
 * Characterization of the profile JSON contract, written BEFORE the `org.json` -> kotlinx conversion.
 *
 * The profile document is the highest risk thing left to convert: it feeds Nightscout, it is read
 * back from other uploaders and from older AAPS versions, and every number in it is a dosing
 * parameter. So this file pins what `org.json` does today rather than what the schema looks like -
 * the schema is the easy part.
 *
 * Three findings drive it, all measured rather than assumed:
 *
 * 1. Real profile documents carry numbers as **quoted strings** (`"value":"0.1"`) as well as real
 *    numbers - both shapes occur in the wild. Both `org.json` AND kotlinx coerce the quoted form,
 *    so this is NOT the hazard it looks like - measured, not assumed. See the test below.
 * 2. A malformed schedule makes `blockFromJsonArray` return **null** (an invalid profile), not throw.
 *    Strict parsing would turn that into an exception, i.e. a crash where today there is a rejected
 *    profile.
 * 3. `org.json` writes a whole-numbered double without its fraction, so a basal of `1.0` is uploaded
 *    as `1`. kotlinx writes `1.0`. A full profile has ~120 such values.
 *
 * When the conversion happens these assertions must still hold. Where a divergence is intentional it
 * is asserted as a divergence here, not quietly skipped.
 */
class ProfileJsonCharacterizationTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context

    private lateinit var dateUtil: DateUtil

    @BeforeEach fun setup() {
        dateUtil = DateUtilImpl(context)
    }

    private fun schedule(vararg entries: Pair<String, String>): JSONArray =
        JSONArray().also { arr ->
            entries.forEach { (time, value) ->
                arr.put(JSONObject().put("time", time).put("value", value))
            }
        }

    // ---------------------------------------------------------------- 1. numbers arrive as strings

    /**
     * `"0.1"` is a JSON *string*, and the whole profile spine depends on it being read as a number.
     */
    @Test fun `schedule values are read from quoted strings`() {
        val blocks = blockFromJsonArray(schedule("00:00" to "0.1", "12:00" to "0.25"), dateUtil)

        assertThat(blocks).isNotNull()
        assertThat(blocks!!).hasSize(2)
        assertThat(blocks[0].amount).isEqualTo(0.1)
        assertThat(blocks[1].amount).isEqualTo(0.25)
    }

    /** ...and as real JSON numbers, which is what AAPS itself writes. Both shapes must work. */
    @Test fun `schedule values are read from real numbers too`() {
        val arr = JSONArray()
            .put(JSONObject().put("time", "00:00").put("value", 0.1))
            .put(JSONObject().put("time", "12:00").put("value", 0.25))
        val blocks = blockFromJsonArray(arr, dateUtil)

        assertThat(blocks).isNotNull()
        assertThat(blocks!![0].amount).isEqualTo(0.1)
        assertThat(blocks[1].amount).isEqualTo(0.25)
    }

    /** An integer-looking string is a double too - `"6"` is an ISF, not the integer 6. */
    @Test fun `integer looking strings become doubles`() {
        val blocks = blockFromJsonArray(schedule("00:00" to "6"), dateUtil)
        assertThat(blocks!![0].amount).isEqualTo(6.0)
    }

    // ---------------------------------------------------------------- 2. failure is null, not throw

    @Test fun `a non numeric value yields an invalid profile rather than an exception`() {
        assertThat(blockFromJsonArray(schedule("00:00" to "not a number"), dateUtil)).isNull()
    }

    @Test fun `a missing value key yields an invalid profile rather than an exception`() {
        val arr = JSONArray().put(JSONObject().put("time", "00:00"))
        assertThat(blockFromJsonArray(arr, dateUtil)).isNull()
    }

    /**
     * Hour alignment is a real rule, not an accident: a schedule that does not start on the hour is
     * rejected. Worth pinning because a rewritten parser could easily drop the check.
     */
    @Test fun `a schedule not aligned to whole hours is rejected`() {
        assertThat(blockFromJsonArray(schedule("00:30" to "0.1", "12:00" to "0.2"), dateUtil)).isNull()
    }

    @Test fun `a null array yields null`() {
        assertThat(blockFromJsonArray(null, dateUtil)).isNull()
    }

    // ---------------------------------------------------------------- 3. the write side diverges

    /**
     * The divergence that would change bytes uploaded to Nightscout.
     *
     * A profile has 24 basal + 24 ISF + 24 IC + 48 target entries, so a document can differ in ~120
     * places without a single value being wrong. Both parse back identically - what breaks is any
     * consumer comparing documents as text.
     */
    @Test fun `whole numbered profile values serialize differently`() {
        val org = JSONObject().put("value", 1.0).toString()
        val kotlinx = buildJsonObject { put("value", JsonPrimitive(1.0)) }.toString()

        assertThat(org).isEqualTo("""{"value":1}""")
        assertThat(kotlinx).isEqualTo("""{"value":1.0}""")
        assertWithMessage("if this ever passes, the divergence is gone and the shim can drop it")
            .that(org).isNotEqualTo(kotlinx)
    }

    /** A fractional basal rate is written the same by both, so only the whole-numbered case matters. */
    @Test fun `fractional profile values serialize identically`() {
        assertThat(JSONObject().put("value", 0.825).toString())
            .isEqualTo(buildJsonObject { put("value", JsonPrimitive(0.825)) }.toString())
    }

    // ---------------------------------------------------------------- 5. what a real NS store looks like

    /**
     * Shape facts taken from a live Nightscout `/api/v1/profile.json`, not from the NS docs.
     *
     * The response is an **array** of store documents (the profile history), each holding a `store`
     * keyed by user-chosen profile name. Nightscout adds its own fields (`_id`, `date`, `srvModified`,
     * `app`, `subject`, `identifier`, `utcOffset`) and omits the AAPS-specific ones entirely - a real
     * NS-authored profile has no `dia` and no `iCfg`, so those must stay optional on the read path.
     */
    @Test fun `a real nightscout store document parses`() {
        val nsShaped = """
            [{"_id":"6a4507e64d1ba8c2e4196ec1","defaultProfile":"Vsedni den",
              "date":1782908904415,"created_at":"2026-07-01T12:28:24.415Z",
              "startDate":"2026-07-01T12:28:24.4150000Z","utcOffset":0,
              "store":{"Vsedni den":{
                "carbratio":[{"time":"00:00","timeAsSeconds":0,"value":8.1}],
                "sens":[{"time":"00:00","timeAsSeconds":0,"value":10}],
                "basal":[{"time":"00:00","timeAsSeconds":0,"value":1},
                         {"time":"06:00","timeAsSeconds":21600,"value":1.27}],
                "target_low":[{"time":"00:00","timeAsSeconds":0,"value":5.5}],
                "target_high":[{"time":"00:00","timeAsSeconds":0,"value":5.5}],
                "units":"mmol","timezone":"Europe/Prague"}}}]
        """.trimIndent()

        val doc = JSONArray(nsShaped).getJSONObject(0)
        assertThat(doc.getString("defaultProfile")).isEqualTo("Vsedni den")
        assertThat(doc.has("dia")).isFalse()
        assertThat(doc.has("iCfg")).isFalse()

        val profile = doc.getJSONObject("store").getJSONObject("Vsedni den")
        val basal = blockFromJsonArray(profile.getJSONArray("basal"), dateUtil)
        assertThat(basal).isNotNull()
        assertThat(basal!![0].amount).isEqualTo(1.0)
        assertThat(basal[1].amount).isEqualTo(1.27)
    }

    /**
     * The one that decides the whole-number question.
     *
     * Nightscout itself writes a whole-numbered rate **bare** - the live document contains
     * `"value":1`, `"value":6`, `"value":10`, never `1.0`. So `org.json`'s rendering matches what the
     * server produces, and kotlinx's `1.0` would match neither AAPS today nor Nightscout. That makes
     * the divergence worth shimming rather than accepting.
     */
    @Test fun `nightscout writes whole numbered rates without a fraction`() {
        val fromNs = """{"basal":[{"time":"00:00","timeAsSeconds":0,"value":1}]}"""
        val entry = JSONObject(fromNs).getJSONArray("basal").getJSONObject(0)

        assertThat(entry.getDouble("value")).isEqualTo(1.0)
        // round-tripping it through org.json keeps the bare form
        assertThat(JSONObject().put("value", entry.getDouble("value")).toString()).isEqualTo("""{"value":1}""")
    }

    /**
     * Softens the slash finding: Nightscout stores and returns `Europe/Prague` unescaped, so the
     * `mg\/dl` form org.json emits is normalised away server-side. It is still a byte difference on
     * the way out, but it does not survive a round trip - worth knowing before treating it as a
     * blocker.
     */
    @Test fun `nightscout returns slashes unescaped`() {
        val fromNs = """{"timezone":"Europe/Prague"}"""
        assertThat(fromNs).doesNotContain("\\/")
        assertThat(JSONObject(fromNs).getString("timezone")).isEqualTo("Europe/Prague")
    }

    // ---------------------------------------------------------------- 4. what strict parsing costs (less than expected)

    /**
     * Quoted numbers are NOT the migration hazard they look like.
     *
     * The expectation going in was that a `Double` field would reject `"0.1"` without `isLenient`,
     * because it is a JSON string where a number belongs. Measured, that is false: kotlinx coerces it
     * in default (strict) configuration. Pinned here so the conversion does not switch `isLenient` on
     * for a problem that does not exist - leniency would also start accepting genuinely malformed
     * input that is rejected today.
     *
     * Caveat, stated rather than glossed: this exercises the built-in `Double` serializer directly and
     * through a map. The `@Serializable` data-class code path could not be exercised from this module
     * (no serialization plugin here), so if the conversion uses generated serializers, re-measure.
     */
    @Test fun `kotlinx accepts quoted numbers without leniency`() {
        assertThat(Json.decodeFromString(Double.serializer(), "\"0.1\"")).isEqualTo(0.1)
        assertThat(
            Json.decodeFromString(MapSerializer(String.serializer(), Double.serializer()), """{"value":"0.1"}""")
        ).containsEntry("value", 0.1)

        // ...matching what org.json has always done, which is why the profile spine works today.
        assertThat(JSONObject("""{"value":"0.1"}""").getDouble("value")).isEqualTo(0.1)
    }

    /**
     * The divergence that only real data revealed: `org.json` escapes a forward slash, kotlinx does
     * not. Every profile carries two slashed strings - `units` (`mg/dl`) and `timezone`
     * (`Africa/Cairo`) - so this changes bytes on every single upload, independently of the
     * whole-number issue above.
     *
     * Both decode to the same string, so nothing inside AAPS notices. A document hash or a byte
     * comparison on the Nightscout side would.
     */
    @Test fun `forward slashes are escaped by org json but not by kotlinx`() {
        val org = JSONObject().put("units", "mg/dl").toString()
        val kotlinx = buildJsonObject { put("units", JsonPrimitive("mg/dl")) }.toString()

        assertThat(org).isEqualTo("""{"units":"mg\/dl"}""")
        assertThat(kotlinx).isEqualTo("""{"units":"mg/dl"}""")
        // ...and both read back the same, which is why this hides so well.
        assertThat(JSONObject(org).getString("units"))
            .isEqualTo(Json.parseToJsonElement(kotlinx).let { (it as kotlinx.serialization.json.JsonObject) }["units"]!!.let { (it as JsonPrimitive).content })
    }
}
