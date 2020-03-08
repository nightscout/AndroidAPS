package info

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.data.Profile
import org.json.JSONObject
import org.junit.Rule
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

open class TestBase {
    val validProfileJSON = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    val validProfile: Profile = Profile(JSONObject(validProfileJSON), Constants.MGDL)

    // Add a JUnit rule that will setup the @Mock annotated vars and log.
    // Another possibility would be to add `MockitoAnnotations.initMocks(this) to the setup method.
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    // Workaround for Kotlin nullability.
    // https://medium.com/@elye.project/befriending-kotlin-and-mockito-1c2e7b0ef791
    // https://stackoverflow.com/questions/30305217/is-it-possible-to-use-mockito-in-kotlin
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("Unchecked_Cast")
    fun <T> uninitialized(): T = null as T
}