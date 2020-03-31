package info.nightscout.androidaps

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.ProfileStore
import info.nightscout.androidaps.logging.AAPSLoggerTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.*

open class TestBase {

    val aapsLogger = AAPSLoggerTest()

    // Add a JUnit rule that will setup the @Mock annotated vars and log.
    // Another possibility would be to add `MockitoAnnotations.initMocks(this) to the setup method.
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Before
    fun setupLocale() {
        Locale.setDefault(Locale.ENGLISH)
        System.setProperty("disableFirebase", "true")
    }

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