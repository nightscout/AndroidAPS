package app.aaps.wear

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.interaction.utils.Constants
import app.aaps.wear.testing.mocks.SharedPreferencesMock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
open class WearTestBase {

    private var clockNow: Long = REF_NOW
    private val aapsLogger = AAPSLoggerTest()
    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @OptIn(ExperimentalTime::class)
    @Mock lateinit var clock: Clock

    private val mockedSharedPrefs: HashMap<String, SharedPreferences> = HashMap()

    @OptIn(ExperimentalTime::class)
    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        doAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            if (mockedSharedPrefs.containsKey(key)) {
                return@doAnswer mockedSharedPrefs[key]
            } else {
                val newPrefs = SharedPreferencesMock()
                mockedSharedPrefs[key] = newPrefs
                return@doAnswer newPrefs
            }
        }.whenever(context).getSharedPreferences(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())
        setClockNow()
    }

    fun progressClock(byMilliseconds: Long) {
        clockNow += byMilliseconds
        setClockNow()
    }

    @OptIn(ExperimentalTime::class)
    private fun setClockNow() {
        whenever(clock.now()).thenReturn(Instant.fromEpochMilliseconds(clockNow))
    }

    companion object {

        const val REF_NOW = 1572610530000L

        fun backInTime(d: Int, h: Int, m: Int, s: Int): Long =
            REF_NOW - (Constants.DAY_IN_MS * d + Constants.HOUR_IN_MS * h + Constants.MINUTE_IN_MS * m + Constants.SECOND_IN_MS * s)
    }
}
