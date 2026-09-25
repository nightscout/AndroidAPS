package app.aaps.core.nssdk.mapper

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * A spike, not a contract test.
 *
 * `GsonTypeCoercionTest` pins what Gson does today with a wrong-typed field: it coerces a number
 * into a `String`, and a numeric string into `Long` / `Double` / `Int` / `Boolean`. Before rewriting
 * 190 annotations, this measures whether kotlinx.serialization can be **configured** to do the same,
 * or whether every affected field needs a hand written serializer. Those are very different amounts
 * of work, so the answer decides how the converter switch gets done.
 *
 * The mirror class below only carries the field shapes that matter, not the whole wire model.
 */
class KotlinxCoercionSpikeTest {

    @Serializable
    private data class Mirror(
        @SerialName("eventType") val eventType: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("date") val date: Long? = null,
        @SerialName("insulin") val insulin: Double? = null,
        @SerialName("percentage") val percentage: Int? = null,
        @SerialName("isValid") val isValid: Boolean? = null
    )

    /** Every case from GsonTypeCoercionTest that relies on coercion. */
    private val cases = listOf(
        "number into String" to """{"created_at":1785992179555}""",
        "numeric string into Long" to """{"date":"1785992179555"}""",
        "numeric string into Double" to """{"insulin":"0.25"}""",
        "numeric string into Int" to """{"percentage":"90"}""",
        "quoted true into Boolean" to """{"isValid":"true"}""",
        "integer into Double" to """{"insulin":4}"""
    )

    private fun outcomes(json: Json): Map<String, Boolean> =
        cases.associate { (name, text) -> name to runCatching { json.decodeFromString(Mirror.serializer(), text) }.isSuccess }

    /**
     * Strict is the default, and it is far closer to Gson than expected: a **quoted number** is
     * already accepted by `Long`, `Double` and `Int` fields, and `"true"` by a `Boolean` field.
     *
     * Exactly one case differs - a bare number arriving in a `String` field - and that is the one
     * `created_at` actually hits in the wild. So the gap between the two libraries here is a single
     * behaviour, not the broad class of coercions it first looked like.
     */
    @Test
    fun `strict kotlinx differs from Gson in exactly one case`() {
        val strict = outcomes(Json { ignoreUnknownKeys = true; explicitNulls = false })

        assertThat(strict).isEqualTo(
            mapOf(
                "number into String" to false,      // <- the only gap
                "numeric string into Long" to true,
                "numeric string into Double" to true,
                "numeric string into Int" to true,
                "quoted true into Boolean" to true,
                "integer into Double" to true
            )
        )
    }

    /**
     * The question this spike exists to answer. If lenient covers everything, the converter switch
     * is a configuration choice. If it does not, the uncovered cases each need a serializer.
     */
    @Test
    fun `lenient kotlinx - what it does and does not recover`() {
        val lenient = outcomes(Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true })

        // Asserted as a whole map so a failure prints the full picture in one go.
        assertThat(lenient).isEqualTo(
            mapOf(
                "number into String" to true,
                "numeric string into Long" to true,
                "numeric string into Double" to true,
                "numeric string into Int" to true,
                "quoted true into Boolean" to true,
                "integer into Double" to true
            )
        )
    }

    /** Values, not just "did it parse" - a coercion that silently produces the wrong number is worse. */
    @Test
    fun `lenient kotlinx produces the same values as Gson`() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

        assertThat(json.decodeFromString(Mirror.serializer(), """{"created_at":1785992179555}""").createdAt)
            .isEqualTo("1785992179555")
        assertThat(json.decodeFromString(Mirror.serializer(), """{"date":"1785992179555"}""").date)
            .isEqualTo(1785992179555L)
        assertThat(json.decodeFromString(Mirror.serializer(), """{"insulin":"0.25"}""").insulin)
            .isEqualTo(0.25)
        assertThat(json.decodeFromString(Mirror.serializer(), """{"percentage":"90"}""").percentage)
            .isEqualTo(90)
        assertThat(json.decodeFromString(Mirror.serializer(), """{"isValid":"true"}""").isValid)
            .isTrue()
    }

    /** Unknown keys and absent fields, the other two things the wire layer depends on. */
    @Test
    fun `unknown keys are ignored and absent fields fall back to the default`() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
        val parsed = json.decodeFromString(
            Mirror.serializer(),
            """{"eventType":"Correction Bolus","somethingFromTheFuture":{"a":[1,2]}}"""
        )

        assertThat(parsed.eventType).isEqualTo("Correction Bolus")
        assertThat(parsed.date).isNull()
        assertThat(parsed.insulin).isNull()
    }

    /** An explicit JSON null must land as Kotlin null, the way Gson leaves it. */
    @Test
    fun `explicit nulls decode to null`() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
        val parsed = json.decodeFromString(Mirror.serializer(), """{"insulin":null,"carbs":null}""")

        assertThat(parsed.insulin).isNull()
    }
}
