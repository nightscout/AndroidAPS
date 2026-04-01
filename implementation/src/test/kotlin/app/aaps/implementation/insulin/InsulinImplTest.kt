package app.aaps.implementation.insulin

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class InsulinImplTest : TestBase() {

    private lateinit var sut: InsulinImpl
    private lateinit var insulinConfiguration: String
    private val testScope = TestScope()

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uel: UserEntryLogger

    @BeforeEach
    fun setup() {
        // dia 5.0 h, Peak 30 min
        insulinConfiguration = "{\"insulin\":[{\"insulinLabel\":\"test\",\"insulinEndTime\":18000000,\"insulinPeakTime\":1800000,\"concentration\":1.0}]}"
        whenever(preferences.get(StringNonKey.InsulinConfiguration)).thenReturn(insulinConfiguration)
        whenever(persistenceLayer.observeChanges(any<Class<*>>())).thenReturn(emptyFlow())
        // Mock rh.gs() for nickname resolution (OREF_FREE_PEAK template) and buildSuffix (U100 concentration)
        whenever(rh.gs(eq(R.string.free_peak_oref))).thenReturn("Free-Peak Oref")
        whenever(rh.gs(eq(R.string.u100))).thenReturn("U100")
        sut = InsulinImpl(preferences, rh, profileFunction, persistenceLayer, aapsLogger, config, hardLimits, uel, testScope)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(InsulinType.OREF_FREE_PEAK)
    }

    @Test
    fun getFriendlyNameTest() {
        assertThat(sut.friendlyName).isEqualTo("Free-Peak Oref")
    }

    @Test
    fun testIobCalcForTreatment() {
        val treatment = BS(timestamp = 0, amount = 10.0, type = BS.Type.NORMAL, iCfg = sut.iCfg)
        val time = System.currentTimeMillis()
        // check directly after bolus
        treatment.timestamp = time
        treatment.amount = 10.0
        assertThat(treatment.iobCalc(time).iobContrib).isWithin(0.01).of(10.0)
        // check after 1 hour
        treatment.timestamp = time - 1 * 60 * 60 * 1000 // 1 hour
        treatment.amount = 10.0
        assertThat(treatment.iobCalc(time).iobContrib).isWithin(0.01).of(3.92)
        // check after 2 hour
        treatment.timestamp = time - 2 * 60 * 60 * 1000 // 2 hours
        treatment.amount = 10.0
        assertThat(treatment.iobCalc(time).iobContrib).isWithin(0.01).of(0.77)
        // check after 3 hour
        treatment.timestamp = time - 3 * 60 * 60 * 1000 // 3 hours
        treatment.amount = 10.0
        assertThat(treatment.iobCalc(time).iobContrib).isWithin(0.01).of(0.10)
        // check after dia
        treatment.timestamp = time - 4 * 60 * 60 * 1000 // 4 hours
        treatment.amount = 10.0
        assertThat(treatment.iobCalc(time).iobContrib).isWithin(0.01).of(0.0)
    }
}
