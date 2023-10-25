package app.aaps.wear

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import app.aaps.wear.interaction.utils.Constants
import app.aaps.wear.interaction.utils.Persistence
import app.aaps.wear.interaction.utils.WearUtil
import app.aaps.wear.testing.mocks.SharedPreferencesMock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito

open class WearTestBase : TestBase() {

    private var clockNow: Long = REF_NOW
    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var clock: Clock
    lateinit var wearUtil: WearUtil

    lateinit var persistence: Persistence

    private val mockedSharedPrefs: HashMap<String, SharedPreferences> = HashMap()

    @BeforeEach
    fun setup() {
        wearUtil = WearUtil(context, aapsLogger, clock)
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
        setClockNow()

        persistence = Mockito.spy(Persistence(aapsLogger, dateUtil, sp))
    }

    fun progressClock(byMilliseconds: Long) {
        clockNow += byMilliseconds
        setClockNow()
    }

    private fun setClockNow() {
        Mockito.`when`(clock.now()).thenReturn(Instant.fromEpochMilliseconds(clockNow))
    }

    companion object {

        const val REF_NOW = 1572610530000L

        fun backInTime(d: Int, h: Int, m: Int, s: Int): Long =
            REF_NOW - (Constants.DAY_IN_MS * d + Constants.HOUR_IN_MS * h + Constants.MINUTE_IN_MS * m + Constants.SECOND_IN_MS * s)
    }
}
