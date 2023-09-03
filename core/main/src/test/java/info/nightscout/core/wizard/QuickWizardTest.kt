package info.nightscout.core.wizard

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.json.JSONArray
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class QuickWizardTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var loop: Loop

    private val data1 = "{\"buttonText\":\"Meal\",\"carbs\":36,\"validFrom\":0,\"validTo\":18000," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":0,\"useBasalIOB\":0,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    private val data2 = "{\"buttonText\":\"Lunch\",\"carbs\":18,\"validFrom\":36000,\"validTo\":39600," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":1,\"useBasalIOB\":2,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    private var array: JSONArray = JSONArray("[$data1,$data2]")

    class MockedTime : QuickWizardEntry.Time() {

        override fun secondsFromMidnight() = 0
    }

    private val mockedTime = MockedTime()

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is QuickWizardEntry) {
                it.aapsLogger = aapsLogger
                it.sp = sp
                it.profileFunction = profileFunction
                it.loop = loop
                it.time = mockedTime
            }
        }
    }

    private lateinit var quickWizard: QuickWizard

    @BeforeEach
    fun setup() {
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_quickwizard, "[]")).thenReturn("[]")
        quickWizard = QuickWizard(sp, injector)
    }

    @Test
    fun setDataTest() {
        quickWizard.setData(array)
        Assertions.assertEquals(2, quickWizard.size())
    }

    @Test
    fun test() {
        quickWizard.setData(array)
        Assertions.assertEquals("Lunch", quickWizard[1].buttonText())
    }

    @Test
    fun active() {
        quickWizard.setData(array)
        val e: QuickWizardEntry = quickWizard.getActive()!!
        Assertions.assertEquals(36.0, e.carbs().toDouble(), 0.01)
        quickWizard.remove(0)
        quickWizard.remove(0)
        Assertions.assertNull(quickWizard.getActive())
    }

    @Test
    fun newEmptyItemTest() {
        Assertions.assertNotNull(quickWizard.newEmptyItem())
    }

    @Test
    fun addOrUpdate() {
        quickWizard.setData(array)
        Assertions.assertEquals(2, quickWizard.size())
        quickWizard.addOrUpdate(quickWizard.newEmptyItem())
        Assertions.assertEquals(3, quickWizard.size())
        val q: QuickWizardEntry = quickWizard.newEmptyItem()
        q.position = 0
        quickWizard.addOrUpdate(q)
        Assertions.assertEquals(3, quickWizard.size())
    }

    @Test
    fun remove() {
        quickWizard.setData(array)
        quickWizard.remove(0)
        Assertions.assertEquals(1, quickWizard.size())
    }
}