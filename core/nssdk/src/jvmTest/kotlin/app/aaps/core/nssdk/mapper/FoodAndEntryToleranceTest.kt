package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.nsSdkJson
import app.aaps.core.nssdk.remotemodel.NSResponse
import app.aaps.core.nssdk.remotemodel.RemoteEntry
import app.aaps.core.nssdk.remotemodel.RemoteFood
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test

/**
 * Regression tests for the one thing the Gson to kotlinx move changed that nothing was watching.
 *
 * Under Gson, a field declared non-null but **without a default** was quietly filled with
 * `null` / `0` / `0.0` when the server did not send it, because Gson builds objects through
 * `Unsafe.allocateInstance` and never calls the constructor. kotlinx treats exactly the same
 * declaration as **mandatory** and throws.
 *
 * That difference is not per record. `v3/food` and `v3/entries` are decoded as one list, so a single
 * unparseable document takes the whole page with it - and the workers only advance their cursor
 * after a successful decode, so the same page is requested again forever.
 *
 * Neither `coerceInputValues` nor `explicitNulls` helps: both only apply to properties that already
 * have a default. The fix was to give the affected fields the same values Gson used to leave behind.
 *
 * These collections are shared and multi-writer - Nightscout's own food editor, xDrip, Loop and
 * other uploaders all write to them - so "AAPS always sends this field" is not a safe assumption.
 */
class FoodAndEntryToleranceTest {

    // ---------------------------------------------------------------- food

    /**
     * A "quickpick" document. Nightscout stores these in the same `food` collection as real food,
     * and they have no `portion` and no `carbs`. `FoodMapper` already has an `else -> return null`
     * branch for exactly this - it must be reached, not pre-empted by a parse failure.
     */
    @Test
    fun `a quickpick food document parses and is then dropped by the mapper`() {
        val json = """{"type":"quickpick","name":"Breakfast","foods":[],"hidden":false,"position":0}"""

        val food = nsSdkJson.decodeFromString(RemoteFood.serializer(), json)
        assertThat(food.type).isEqualTo("quickpick")
        assertThat(food.portion).isEqualTo(0.0)
        assertThat(food.carbs).isEqualTo(0)

        // and the mapper drops it, exactly as it did under Gson
        assertThat(food.toNSFood()).isNull()
    }

    /**
     * The case that made this severe: one bad document must not take the good ones with it. This is
     * what `v3/food` actually returns - a list, decoded in a single pass.
     */
    @Test
    fun `a quickpick in the middle of a list does not lose the real food`() {
        val json = """
            [{"type":"quickpick","name":"Breakfast","foods":[],"hidden":false},
             {"type":"food","name":"Apple","portion":100.0,"carbs":12,"unit":"g"},
             {"type":"food","name":"Bread","portion":50.0,"carbs":25,"unit":"g"}]
        """.trimIndent()

        val foods = nsSdkJson.decodeFromString(ListSerializer(RemoteFood.serializer()), json)
        assertThat(foods).hasSize(3)

        val mapped = foods.mapNotNull { it.toNSFood() }
        assertThat(mapped).hasSize(2)
        assertThat(mapped.map { it.name }).containsExactly("Apple", "Bread")
    }

    /** A third-party uploader writing an explicit null instead of omitting the field. */
    @Test
    fun `a food document with explicit nulls still parses`() {
        val json = """{"type":"food","name":"Apple","portion":100.0,"carbs":null,"unit":null}"""

        val food = nsSdkJson.decodeFromString(RemoteFood.serializer(), json)
        assertThat(food.carbs).isEqualTo(0)
        assertThat(food.name).isEqualTo("Apple")
    }

    /** A history/deleted document, where the server strips the payload. */
    @Test
    fun `a stripped food document parses instead of throwing`() {
        val json = """{"identifier":"abc123","srvModified":1785992181588,"isValid":false}"""

        val food = nsSdkJson.decodeFromString(RemoteFood.serializer(), json)
        assertThat(food.type).isEmpty()
        assertThat(food.toNSFood()).isNull()   // not type "food" -> dropped, as before
    }

    /** The whole response shape the client actually decodes. */
    @Test
    fun `a food response wrapper survives a mixed list`() {
        val json = """{"result":[{"type":"quickpick","name":"QP"},{"type":"food","name":"Apple","portion":100.0,"carbs":12}]}"""

        val response = nsSdkJson.decodeFromString(NSResponse.serializer(ListSerializer(RemoteFood.serializer())), json)
        assertThat(response.result).hasSize(2)
        assertThat(response.result?.mapNotNull { it.toNSFood() }).hasSize(1)
    }

    // ---------------------------------------------------------------- entries

    /**
     * `entries` is the glucose feed. The endpoints the follower uses are **not** filtered by type,
     * so a record without `type` reaches the decoder. Losing the page here would stop all glucose.
     */
    @Test
    fun `an entry without type parses and is skipped by both mappers`() {
        val json = """{"date":1785992179555,"sgv":120.0,"device":"someUploader"}"""

        val entry = nsSdkJson.decodeFromString(RemoteEntry.serializer(), json)
        assertThat(entry.type).isEmpty()
        assertThat(entry.toCalibrationMbg()).isNull()
    }

    @Test
    fun `an entry with an explicit null type parses`() {
        val json = """{"date":1785992179555,"sgv":120.0,"type":null}"""

        val entry = nsSdkJson.decodeFromString(RemoteEntry.serializer(), json)
        assertThat(entry.type).isEmpty()
    }

    /** One typeless record must not take the real glucose values with it. */
    @Test
    fun `a typeless entry in a list does not lose the real readings`() {
        val json = """
            [{"date":1785992179555,"sgv":120.0},
             {"date":1785992479555,"sgv":125.0,"type":"sgv","dateString":"2026-08-06T05:01:19.555Z"},
             {"date":1785992779555,"sgv":130.0,"type":"sgv","dateString":"2026-08-06T05:06:19.555Z"}]
        """.trimIndent()

        val entries = nsSdkJson.decodeFromString(ListSerializer(RemoteEntry.serializer()), json)
        assertThat(entries).hasSize(3)
        assertThat(entries.count { it.type == "sgv" }).isEqualTo(2)
    }
}
