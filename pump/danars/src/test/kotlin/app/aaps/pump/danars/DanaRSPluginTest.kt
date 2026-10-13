package app.aaps.pump.danars

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@Suppress("SpellCheckingInspection")
class DanaRSPluginTest : DanaRSTestBase() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    private lateinit var danaRSPlugin: DanaRSPlugin

    @Test
    fun basalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        danaRSPlugin.applyBasalConstraints(c, validProfile)
        Assertions.assertEquals(java.lang.Double.valueOf(0.8), c.value(), 0.0001)
        Assertions.assertEquals("DanaRS: limitingbasalratio", c.getReasons())
        Assertions.assertEquals("DanaRS: limitingbasalratio", c.getMostLimitedReasons())
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        danaRSPlugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(200, c.value())
        Assertions.assertEquals("DanaRS: limitingpercentrate", c.getReasons())
        Assertions.assertEquals("DanaRS: limitingpercentrate", c.getMostLimitedReasons())
    }

    @BeforeEach
    fun prepareMocks() {
        whenever(preferences.get(DanaStringKey.RsName)).thenReturn("")
        whenever(preferences.get(DanaStringKey.MacAddress)).thenReturn("")
        whenever(rh.gs(eq(app.aaps.core.ui.R.string.limitingbasalratio), anyOrNull(), anyOrNull())).thenReturn("limitingbasalratio")
        whenever(rh.gs(eq(app.aaps.core.ui.R.string.limitingpercentrate), anyOrNull(), anyOrNull())).thenReturn("limitingpercentrate")

        danaRSPlugin =
            DanaRSPlugin(
                aapsLogger, rh, preferences, commandQueue, aapsSchedulers, rxBus, context, constraintChecker, profileFunction, danaPump, pumpSync, detailedBolusInfoStorage, temporaryBasalStorage,
                fabricPrivacy, dateUtil, uiInteraction, danaHistoryDatabase, decimalFormatter, pumpEnactResultProvider
            )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        danaRSPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}