package app.aaps.plugins.insulin

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class InsulinOrefBasePluginTest : TestBase() {

    var testPeak = 0
    var testUserDefinedDia = 0.0
    var shortDiaNotificationSend = false

    inner class InsulinBaseTest(
        rh: ResourceHelper,
        profileFunction: ProfileFunction,
        rxBus: RxBus,
        aapsLogger: AAPSLogger,
        config: Config,
        hardLimits: HardLimits
    ) : InsulinOrefBasePlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction) {

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
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setUp() {
        sut = InsulinBaseTest(rh, profileFunction, rxBus, aapsLogger, config, hardLimits)
        whenever(hardLimits.minDia()).thenReturn(5.0)
    }

    @Test
    fun testGetDia() {
        assertThat(sut.dia).isEqualTo(5.0)
        testUserDefinedDia = 5.0 + 1
        assertThat(sut.dia).isEqualTo(5.0 + 1)
        testUserDefinedDia = 5.0 - 1
        assertThat(sut.dia).isEqualTo(5.0)
        assertThat(shortDiaNotificationSend).isTrue()
    }

    @Test
    fun testIobCalcForTreatment() {
        val treatment = BS(timestamp = 0, amount = 10.0, type = BS.Type.NORMAL)
        testPeak = 30
        testUserDefinedDia = 4.0
        val time = System.currentTimeMillis()
        // check directly after bolus
        treatment.timestamp = time
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib).isWithin(0.01).of(10.0)
        // check after 1 hour
        treatment.timestamp = time - 1 * 60 * 60 * 1000 // 1 hour
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib).isWithin(0.01).of(3.92)
        // check after 2 hour
        treatment.timestamp = time - 2 * 60 * 60 * 1000 // 2 hours
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib).isWithin(0.01).of(0.77)
        // check after 3 hour
        treatment.timestamp = time - 3 * 60 * 60 * 1000 // 3 hours
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib).isWithin(0.01).of(0.10)
        // check after dia
        treatment.timestamp = time - 4 * 60 * 60 * 1000 // 4 hours
        treatment.amount = 10.0
        assertThat(sut.iobCalcForTreatment(treatment, time, Constants.defaultDIA).iobContrib).isWithin(0.01).of(0.0)
    }
}
