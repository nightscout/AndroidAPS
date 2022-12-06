package info.nightscout.androidaps.data

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.core.wizard.QuickWizard
import info.nightscout.core.wizard.QuickWizardEntry
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.junit.Assert
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
    fun mock() {

        `when`(sp.getString(info.nightscout.core.utils.R.string.key_quickwizard, "[]")).thenReturn("[]")
        quickWizard = QuickWizard(sp, injector)
    }

    @Test fun setDataTest() {
        quickWizard.setData(array)
        Assert.assertEquals(2, quickWizard.size())
    }

    @Test fun test() {
        quickWizard.setData(array)
        Assert.assertEquals("Lunch", quickWizard[1].buttonText())
    }

    @Test fun active() {
        quickWizard.setData(array)
        val e: QuickWizardEntry = quickWizard.getActive()!!
        Assert.assertEquals(36.0, e.carbs().toDouble(), 0.01)
        quickWizard.remove(0)
        quickWizard.remove(0)
        Assert.assertNull(quickWizard.getActive())
    }

    @Test fun newEmptyItemTest() {
        Assert.assertNotNull(quickWizard.newEmptyItem())
    }

    @Test fun addOrUpdate() {
        quickWizard.setData(array)
        Assert.assertEquals(2, quickWizard.size())
        quickWizard.addOrUpdate(quickWizard.newEmptyItem())
        Assert.assertEquals(3, quickWizard.size())
        val q: QuickWizardEntry = quickWizard.newEmptyItem()
        q.position = 0
        quickWizard.addOrUpdate(q)
        Assert.assertEquals(3, quickWizard.size())
    }

    @Test fun remove() {
        quickWizard.setData(array)
        quickWizard.remove(0)
        Assert.assertEquals(1, quickWizard.size())
    }
}