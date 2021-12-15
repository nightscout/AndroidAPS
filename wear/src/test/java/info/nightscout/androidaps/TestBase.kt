package info.nightscout.androidaps

import android.content.Context
import android.content.SharedPreferences
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.androidaps.testing.mockers.WearUtilMocker
import info.nightscout.androidaps.testing.mocks.SharedPreferencesMock
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.rx.TestAapsSchedulers
import info.nightscout.shared.logging.AAPSLoggerTest
import org.junit.Before
import org.junit.Rule
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.*

open class TestBase {

    @Mock lateinit var context: Context

    val aapsLogger = AAPSLoggerTest()
    val aapsSchedulers: AapsSchedulers = TestAapsSchedulers()

    val wearUtil: WearUtil = Mockito.spy(WearUtil())
    val wearUtilMocker = WearUtilMocker(wearUtil)
    lateinit var persistence: Persistence

    private val mockedSharedPrefs: HashMap<String, SharedPreferences> = HashMap()

    @Before
    fun setup() {
        Mockito.doAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            if (mockedSharedPrefs.containsKey(key)) {
                return@doAnswer mockedSharedPrefs[key]
            } else {
                val newPrefs = SharedPreferencesMock()
                mockedSharedPrefs[key] = newPrefs
                return@doAnswer newPrefs
            }
        }.`when`(context).getSharedPreferences(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())

        wearUtilMocker.prepareMockNoReal()
        wearUtil.aapsLogger = aapsLogger
        wearUtil.context = context
        persistence = Mockito.spy(Persistence(context, aapsLogger, wearUtil))

        Mockito.doAnswer { invocation ->
            val payload = invocation.getArgument<String>(0)
            try {
                return@doAnswer Base64.getDecoder().decode(payload)
            } catch (ex: IllegalArgumentException) {
                return@doAnswer null
            }
        }.`when`(persistence).base64decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())

        Mockito.doAnswer { invocation ->
            val payload = invocation.getArgument<ByteArray>(0)
            Base64.getEncoder().encodeToString(payload)
        }.`when`(persistence).base64encodeToString(ArgumentMatchers.any(), ArgumentMatchers.anyInt())
    }

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