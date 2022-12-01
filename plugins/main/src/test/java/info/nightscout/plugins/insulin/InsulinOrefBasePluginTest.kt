package info.nightscout.plugins.insulin

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.database.entities.Bolus
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsulinOrefBasePluginTest {

    var testPeak = 0
    var testUserDefinedDia = 0.0
    var shortDiaNotificationSend = false

    inner class InsulinBaseTest(
        injector: HasAndroidInjector,
        rh: ResourceHelper,
        profileFunction: ProfileFunction,
        rxBus: RxBus,
        aapsLogger: AAPSLogger,
        config: Config,
        hardLimits: HardLimits
    ) : InsulinOrefBasePlugin(injector, rh, profileFunction, rxBus, aapsLogger, config, hardLimits) {

        override fun sendShortDiaNotification(dia: Double) {
            shortDiaNotificationSend = true
        }

        override val userDefinedDia: Double
            get() = testUserDefinedDia

        override val peak: Int
            get() = testPeak

        override fun commentStandardText(): String = ""
        override val id get(): Insulin.InsulinType = Insulin.InsulinType.UNKNOWN
        override val friendlyName get(): String = ""
        override fun configuration(): JSONObject = JSONObject()
        override fun applyConfiguration(configuration: JSONObject) {}
    }

    private lateinit var sut: InsulinBaseTest

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits

    private var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
        }
    }

    @BeforeEach
    fun setUp() {
        sut = InsulinBaseTest(injector, rh, profileFunction, rxBus, aapsLogger, config, hardLimits)
        `when`(hardLimits.minDia()).thenReturn(5.0)
    }

    @Test
    fun testGetDia() {
        Assert.assertEquals(5.0, sut.dia, 0.0)
        testUserDefinedDia = 5.0 + 1
        Assert.assertEquals(5.0 + 1, sut.dia, 0.0)
        testUserDefinedDia = 5.0 - 1
        Assert.assertEquals(5.0, sut.dia, 0.0)
        Assert.assertTrue(shortDiaNotificationSend)
    }

    @Test
    fun testIobCalcForTreatment() {
        val treatment = Bolus(timestamp = 0, amount = 10.0, type = Bolus.Type.NORMAL)
        testPeak = 30
        testUserDefinedDia = 4.0
        val time = System.currentTimeMillis()
        // check directly after bolus
        treatment.timestamp = time
        treatment.amount = 10.0
        Assert.assertEquals(10.0, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after 1 hour
        treatment.timestamp = time - 1 * 60 * 60 * 1000 // 1 hour
        treatment.amount = 10.0
        Assert.assertEquals(3.92, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after 2 hour
        treatment.timestamp = time - 2 * 60 * 60 * 1000 // 2 hours
        treatment.amount = 10.0
        Assert.assertEquals(0.77, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after 3 hour
        treatment.timestamp = time - 3 * 60 * 60 * 1000 // 3 hours
        treatment.amount = 10.0
        Assert.assertEquals(0.10, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
        // check after dia
        treatment.timestamp = time - 4 * 60 * 60 * 1000 // 4 hours
        treatment.amount = 10.0
        Assert.assertEquals(0.0, sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib, 0.1)
    }
}