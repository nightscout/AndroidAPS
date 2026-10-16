package app.aaps.plugins.aps.openAPSAMA

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock

class OpenAPSAMAPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var determineBasalAMA: DetermineBasalAMA
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin

    @BeforeEach fun prepare() {
        openAPSAMAPlugin = OpenAPSAMAPlugin(
            aapsLogger, rxBus, constraintChecker, rh, config, profileFunction, activePlugin,
            iobCobCalculator, processedTbrEbData, hardLimits, dateUtil, persistenceLayer, glucoseStatusProvider, preferences, determineBasalAMA,
            GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), { apsResultProvider() }, ch, fabricPrivacy, mock()
        )
    }

    /**
     * What is left of this class after the two condition tests went away with the conditions themselves.
     *
     * Thin on purpose, and not pointless: constructing the plugin exercises its whole dependency list, which
     * is the failure this module actually sees - a constructor or graph change that compiles against the
     * production wiring but not against the test wiring.
     */
    @Test
    fun `the plugin constructs and is an APS`() {
        assertThat(openAPSAMAPlugin.getType()).isEqualTo(PluginType.APS)
    }
}
