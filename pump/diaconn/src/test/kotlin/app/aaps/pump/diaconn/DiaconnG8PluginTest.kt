package app.aaps.pump.diaconn

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.diaconn.database.DiaconnHistoryDatabase
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class DiaconnG8PluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var diaconnHistoryDatabase: DiaconnHistoryDatabase
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage

    lateinit var diaconnG8Pump: DiaconnG8Pump

    private lateinit var diaconnG8Plugin: DiaconnG8Plugin

    @BeforeEach
    fun prepareMocks() {
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")

        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
        diaconnG8Plugin = DiaconnG8Plugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, constraintChecker, diaconnG8Pump,
            pumpSync, detailedBolusInfoStorage, temporaryBasalStorage, fabricPrivacy, dateUtil, aapsSchedulers,
            uiInteraction, diaconnHistoryDatabase, pumpEnactResultProvider
        )
    }

    @Test
    fun basalRateShouldBeLimited() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        diaconnG8Pump.maxBasal = 0.8
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        diaconnG8Plugin.applyBasalConstraints(c, validProfile)
        Assertions.assertEquals(0.8, c.value(), 0.01)
        Assertions.assertEquals("DiaconnG8: Limiting max basal rate to 0.80 U/h because of pump limit", c.getReasons())
        Assertions.assertEquals("DiaconnG8: Limiting max basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons())
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        val c = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        diaconnG8Plugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(200, c.value())
        Assertions.assertEquals("DiaconnG8: Limiting max percent rate to 200% because of pump limit", c.getReasons())
        Assertions.assertEquals("DiaconnG8: Limiting max percent rate to 200% because of pump limit", c.getMostLimitedReasons())
    }

    @Test
    fun percentBasalRateShouldBePositive() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        val c = ConstraintObject(-10, aapsLogger)
        diaconnG8Plugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(0, c.value())
        Assertions.assertEquals("DiaconnG8: Limiting max percent rate to 0% because of it must be positive value", c.getReasons())
    }

    @Test
    fun bolusAmountShouldBeLimited() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        diaconnG8Pump.maxBolus = 5.0
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        diaconnG8Plugin.applyBolusConstraints(c)
        Assertions.assertEquals(5.0, c.value(), 0.01)
        Assertions.assertEquals("DiaconnG8: Limiting bolus to 5.0 U because of pump limit", c.getReasons())
        Assertions.assertEquals("DiaconnG8: Limiting bolus to 5.0 U because of pump limit", c.getMostLimitedReasons())
    }

    @Test
    fun extendedBolusAmountShouldBeLimited() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        diaconnG8Pump.maxBolus = 5.0
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        diaconnG8Plugin.applyExtendedBolusConstraints(c)
        Assertions.assertEquals(5.0, c.value(), 0.01)
        Assertions.assertEquals("DiaconnG8: Limiting bolus to 5.0 U because of pump limit", c.getReasons())
    }

    @Test
    fun isInitializedShouldReturnTrueWhenPumpIsConnected() {
        diaconnG8Pump.lastConnection = System.currentTimeMillis()
        diaconnG8Pump.maxBasal = 1.0
        assertThat(diaconnG8Plugin.isInitialized()).isTrue()
    }

    @Test
    fun isInitializedShouldReturnFalseWhenNotConnected() {
        diaconnG8Pump.lastConnection = 0
        diaconnG8Pump.maxBasal = 0.0
        assertThat(diaconnG8Plugin.isInitialized()).isFalse()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        diaconnG8Plugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
