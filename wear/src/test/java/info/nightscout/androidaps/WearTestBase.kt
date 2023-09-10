package info.nightscout.androidaps

import android.content.Context
import android.content.SharedPreferences
import info.nightscout.androidaps.interaction.utils.Constants
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.androidaps.testing.mocks.SharedPreferencesMock
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito

class FakeWearUtil(context: Context, aapsLogger: AAPSLogger) : WearUtil(context, aapsLogger) {
    private var clockMsDiff = 0L

    override fun timestamp(): Long = REF_NOW + clockMsDiff

    fun progressClock(byMilliseconds: Long) {
        clockMsDiff += byMilliseconds
    }

    companion object {
        const val REF_NOW = 1572610530000L
    }
}

open class WearTestBase : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    lateinit var fakeWearUtil: FakeWearUtil

    lateinit var persistence: Persistence

    private val mockedSharedPrefs: HashMap<String, SharedPreferences> = HashMap()


    @BeforeEach
    fun setup() {
        fakeWearUtil = FakeWearUtil(context, aapsLogger)
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

        persistence = Mockito.spy(Persistence(aapsLogger, dateUtil, sp))
    }

    companion object {
        fun backInTime(d: Int, h: Int, m: Int, s: Int): Long =
            FakeWearUtil.REF_NOW - (Constants.DAY_IN_MS * d + Constants.HOUR_IN_MS * h + Constants.MINUTE_IN_MS * m + Constants.SECOND_IN_MS * s)
    }
}
