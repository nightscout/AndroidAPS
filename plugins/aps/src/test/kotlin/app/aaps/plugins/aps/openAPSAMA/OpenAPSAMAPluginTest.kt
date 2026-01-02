package app.aaps.plugins.aps.openAPSAMA

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

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
            GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator), apsResultProvider
        )
    }

    @Test
    fun specialEnableConditionTest() {
        assertThat(openAPSAMAPlugin.specialEnableCondition()).isTrue()
    }

    @Test
    fun specialShowInListConditionTest() {
        assertThat(openAPSAMAPlugin.specialShowInListCondition()).isTrue()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        openAPSAMAPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
