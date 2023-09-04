package info.nightscout.androidaps

import android.content.Context
import android.content.SharedPreferences
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.androidaps.testing.mockers.WearUtilMocker
import info.nightscout.androidaps.testing.mocks.SharedPreferencesMock
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

open class WearTestBase : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var wearUtil: WearUtil

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
}