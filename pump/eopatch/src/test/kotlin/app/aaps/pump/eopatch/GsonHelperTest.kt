package app.aaps.pump.eopatch

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.jupiter.api.Test

class GsonHelperTest {

    @Test
    fun `sharedGson should return non-null Gson instance`() {
        val gson = GsonHelper.sharedGson()

        assertThat(gson).isNotNull()
        assertThat(gson).isInstanceOf(Gson::class.java)
    }

    @Test
    fun `sharedGson should return same instance on multiple calls`() {
        val gson1 = GsonHelper.sharedGson()
        val gson2 = GsonHelper.sharedGson()

        assertThat(gson1).isSameInstanceAs(gson2)
    }

    @Test
    fun `should serialize special floating point values`() {
        val gson = GsonHelper.sharedGson()

        data class TestData(val value: Float)

        // Test NaN
        val nanData = TestData(Float.NaN)
        val nanJson = gson.toJson(nanData)
        assertThat(nanJson).contains("NaN")

        // Test Positive Infinity
        val posInfData = TestData(Float.POSITIVE_INFINITY)
        val posInfJson = gson.toJson(posInfData)
        assertThat(posInfJson).contains("Infinity")

        // Test Negative Infinity
        val negInfData = TestData(Float.NEGATIVE_INFINITY)
        val negInfJson = gson.toJson(negInfData)
        assertThat(negInfJson).contains("-Infinity")
    }

    @Test
    fun `should serialize and deserialize normal values`() {
        val gson = GsonHelper.sharedGson()

        data class TestData(val name: String, val value: Double)

        val original = TestData("test", 123.45)
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, TestData::class.java)

        assertThat(deserialized.name).isEqualTo(original.name)
        assertThat(deserialized.value).isEqualTo(original.value)
    }

    @Test
    fun `should handle null values`() {
        val gson = GsonHelper.sharedGson()

        data class TestData(val value: String?)

        val original = TestData(null)
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, TestData::class.java)

        assertThat(deserialized.value).isNull()
    }

    @Test
    fun `should serialize complex objects`() {
        val gson = GsonHelper.sharedGson()

        data class Address(val street: String, val city: String)
        data class Person(val name: String, val address: Address)

        val person = Person("John", Address("Main St", "NYC"))
        val json = gson.toJson(person)
        val deserialized = gson.fromJson(json, Person::class.java)

        assertThat(deserialized.name).isEqualTo(person.name)
        assertThat(deserialized.address.street).isEqualTo(person.address.street)
        assertThat(deserialized.address.city).isEqualTo(person.address.city)
    }
}
