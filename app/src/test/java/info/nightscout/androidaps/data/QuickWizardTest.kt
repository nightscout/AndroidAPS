package info.nightscout.androidaps.data

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.wizard.QuickWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import org.json.JSONArray
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(Profile::class)
class QuickWizardTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var loopPlugin: LoopPlugin
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    private val data1 = "{\"buttonText\":\"Meal\",\"carbs\":36,\"validFrom\":0,\"validTo\":18000," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":0,\"useBasalIOB\":0,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    private val data2 = "{\"buttonText\":\"Lunch\",\"carbs\":18,\"validFrom\":36000,\"validTo\":39600," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":1,\"useBasalIOB\":2,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    var array: JSONArray = JSONArray("[$data1,$data2]")

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is QuickWizardEntry) {
                it.aapsLogger = aapsLogger
                it.sp = sp
                it.profileFunction = profileFunction
                it.treatmentsPlugin = treatmentsPlugin
                it.loopPlugin = loopPlugin
                it.iobCobCalculatorPlugin = iobCobCalculatorPlugin
            }
        }
    }

    private lateinit var quickWizard : QuickWizard

    @Before
    fun mock() {
        PowerMockito.mockStatic(Profile::class.java)
        PowerMockito.`when`<Any>(Profile::class.java, "secondsFromMidnight").thenReturn(0)
        `when`(sp.getString(R.string.key_quickwizard, "[]")).thenReturn("[]")
        quickWizard = QuickWizard(sp, injector)
    }

    @Test fun setDataTest() {
        quickWizard.setData(array)
        Assert.assertEquals(2, quickWizard.size())
    }

    @Test fun test() {
        quickWizard.setData(array)
        Assert.assertEquals("Lunch", quickWizard.get(1).buttonText())
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