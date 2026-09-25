package app.aaps.implementation.insulin

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.iobCalc
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class InsulinImplTest : TestBase() {

    private lateinit var sut: InsulinImpl
    private lateinit var insulinConfiguration: String
    private val testScope = TestScope()

    @Mock lateinit var preferences: Preferences
    private val rh = generatedTextResolver()
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
        // Observed on the master too now; a flow that never emits leaves these tests as they were.
        whenever(preferences.observe(StringNonKey.InsulinConfiguration)).thenReturn(MutableStateFlow(insulinConfiguration))
        whenever(persistenceLayer.observeChanges(any<KClass<*>>())).thenReturn(emptyFlow())
        // Template and concentration labels are TextRefs, used for the nickname and the label suffix.
        // gs(TextRef) is a DEFAULT interface method, so a mock returns null rather than running it - and
        // then the stored entry fails to parse.
        sut = InsulinImpl(preferences, rh, profileFunction, aapsLogger, config, hardLimits, uel, testScope)
    }

    // The EPS-driven refresh this class used to own now lives in ProfileFunctionImpl, together with the
    // collectResilient regression that guarded it — see ProfileFunctionImplTest.

    @Test
    fun testIobCalcForTreatment() {
        // The curve is carried by the bolus record itself, so state it here rather than asking the plugin
        // "what insulin is in use" — that question belongs to the profile now. Matches insulinConfiguration.
        val iCfg = ICfg(insulinLabel = "test", insulinEndTime = 18_000_000, insulinPeakTime = 1_800_000, concentration = 1.0)
        val treatment = BS(timestamp = 0, amount = 10.0, type = BS.Type.NORMAL, iCfg = iCfg)
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
