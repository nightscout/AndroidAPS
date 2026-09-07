package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.nssdk.remotemodel.NSResponse
import app.aaps.core.nssdk.remotemodel.RemoteEntry
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LongJsonDeserializerTest {

    private val gson: Gson = GsonBuilder().run {
        val longJsonDeserializer = LongJsonDeserializer()
        registerTypeAdapter(Long::class.java, longJsonDeserializer)
        registerTypeAdapter(Long::class.javaObjectType, longJsonDeserializer)
        create()
    }

    @Test
    fun readsDecimalRemoteEntryDate() {
        val responseType = object : TypeToken<NSResponse<List<RemoteEntry>>>() {}.type
        val response = gson.fromJson<NSResponse<List<RemoteEntry>>>(
            """{"result":[{"date":1783235730809.793}]}""",
            responseType
        )

        assertThat(response.result?.single()?.date).isEqualTo(1_783_235_730_809L)
    }

    @Test
    fun readsDecimalPrimitiveLong() {
        val lastModified = gson.fromJson(
            """{"collections":{"entries":1757000496856.75}}""",
            LastModified::class.java
        )

        assertThat(lastModified.collections.entries).isEqualTo(1_757_000_496_856L)
    }

    @Test
    fun truncatesNegativeDecimalAndScientificNotation() {
        val values = gson.fromJson(
            """{"first":-123.9,"second":1.757000496856E12}""",
            LongValues::class.java
        )

        assertThat(values.first).isEqualTo(-123L)
        assertThat(values.second).isEqualTo(1_757_000_496_856L)
    }

    @Test
    fun readsNullOptionalLong() {
        val value = gson.fromJson("""{"value":null}""", OptionalLongValue::class.java)

        assertThat(value.value).isNull()
    }

    @Test
    fun rejectsValueOutsideLongRange() {
        assertThrows<JsonParseException> {
            gson.fromJson("""{"value":1E100000000}""", OptionalLongValue::class.java)
        }
    }

    private data class LongValues(
        val first: Long,
        val second: Long
    )

    private data class OptionalLongValue(
        val value: Long?
    )
}
