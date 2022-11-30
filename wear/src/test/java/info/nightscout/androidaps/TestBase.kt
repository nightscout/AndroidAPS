package info.nightscout.androidaps

import android.content.Context
import android.content.SharedPreferences
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.androidaps.testing.mockers.WearUtilMocker
import info.nightscout.androidaps.testing.mocks.SharedPreferencesMock
import info.nightscout.rx.logging.AAPSLoggerTest
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.junit.Before
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
open class TestBase {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var wearUtil: WearUtil

    val aapsLogger = AAPSLoggerTest()

    //val wearUtil: WearUtil = Mockito.mock(WearUtil::class.java)
    lateinit var wearUtilMocker: WearUtilMocker
    lateinit var persistence: Persistence

    private val mockedSharedPrefs: HashMap<String, SharedPreferences> = HashMap()

    @BeforeEach
    fun setup() {
        wearUtilMocker = WearUtilMocker(wearUtil)
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
        `when`(wearUtil.aapsLogger).thenReturn(aapsLogger)
        `when`(wearUtil.context).thenReturn(context)
        val rateLimits: MutableMap<String, Long> = HashMap()
        `when`(wearUtil.rateLimits).thenReturn(rateLimits)
        persistence = Mockito.spy(Persistence(aapsLogger, dateUtil, sp))

    }

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