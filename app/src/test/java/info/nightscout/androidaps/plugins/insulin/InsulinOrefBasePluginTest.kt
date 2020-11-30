package info.nightscout.androidaps.plugins.insulin

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.insulin.InsulinOrefBasePlugin.Companion.MIN_DIA
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

class InsulinOrefBasePluginTest {

    var testPeak = 0
    var testUserDefinedDia = 0.0
    var shortDiaNotificationSend = false

    inner class InsulinBaseTest(
        injector: HasAndroidInjector,
        resourceHelper: ResourceHelper,
        profileFunction: ProfileFunction,
        rxBus: RxBusWrapper,
        aapsLogger: AAPSLogger
    ) : InsulinOrefBasePlugin(
        injector, resourceHelper, profileFunction, rxBus, aapsLogger
    ) {

        override fun sendShortDiaNotification(dia: Double) {
            shortDiaNotificationSend = true
        }

        override val userDefinedDia: Double
            get() = testUserDefinedDia

        override val peak: Int
            get() = testPeak

        override fun commentStandardText(): String = ""
        override val id get(): InsulinInterface.InsulinType = InsulinInterface.InsulinType.UNKNOWN
        override val friendlyName get(): String = ""
        override fun configuration(): JSONObject = JSONObject()
        override fun applyConfiguration(configuration: JSONObject) {}
    }

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private lateinit var sut: InsulinBaseTest

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var rxBus: RxBusWrapper
    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var activePlugin: ActivePluginProvider

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is Treatment) {
                it.defaultValueHelper = defaultValueHelper
                it.resourceHelper = resourceHelper
                it.profileFunction = profileFunction
                it.activePlugin = activePlugin
            }
        }
    }

    @Before
    fun setUp() {
        sut = InsulinBaseTest(injector, resourceHelper, profileFunction, rxBus, aapsLogger)
    }

    @Test
    fun testGetDia() {
        Assert.assertEquals(MIN_DIA, sut.dia, 0.0)
        testUserDefinedDia = MIN_DIA + 1
        Assert.assertEquals(MIN_DIA + 1, sut.dia, 0.0)
        testUserDefinedDia = MIN_DIA - 1
        Assert.assertEquals(MIN_DIA, sut.dia, 0.0)
        Assert.assertTrue(shortDiaNotificationSend)
    }

    @Test
    fun minDiaTes() {
        Assert.assertEquals(5.0, MIN_DIA, 0.0001)
    }

    @Test
    fun testIobCalcForTreatment() {
        val treatment = Treatment(injector) //TODO: this should be a separate sut. I'd prefer a separate class.
        val expected = Iob()
        Assert.assertEquals(expected, sut.iobCalcForTreatment(treatment, 0, 0.0))
        testPeak = 30
        testUserDefinedDia = 4.0
        val time = System.currentTimeMillis()
        // check directly after bolus
        treatment.date = time
        treatment.insulin = 10.0
        Assert.assertEquals(10.0, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after 1 hour
        treatment.date = time - 1 * 60 * 60 * 1000 // 1 hour
        treatment.insulin = 10.0
        Assert.assertEquals(3.92, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after 2 hour
        treatment.date = time - 2 * 60 * 60 * 1000 // 1 hour
        treatment.insulin = 10.0
        Assert.assertEquals(0.77, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after 3 hour
        treatment.date = time - 3 * 60 * 60 * 1000 // 1 hour
        treatment.insulin = 10.0
        Assert.assertEquals(0.10, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after dia
        treatment.date = time - 4 * 60 * 60 * 1000
        treatment.insulin = 10.0
        Assert.assertEquals(0.0, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
    }
}