package info.nightscout.pump.danars

import dagger.android.AndroidInjector
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito

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
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        danaRSPlugin.applyBasalConstraints(c, validProfile)
        Assertions.assertEquals(java.lang.Double.valueOf(0.8), c.value(), 0.0001)
        Assertions.assertEquals("DanaRS: limitingbasalratio", c.getReasons())
        Assertions.assertEquals("DanaRS: limitingbasalratio", c.getMostLimitedReasons())
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        danaRSPlugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(200, c.value())
        Assertions.assertEquals("DanaRS: limitingpercentrate", c.getReasons())
        Assertions.assertEquals("DanaRS: limitingpercentrate", c.getMostLimitedReasons())
    }

    @BeforeEach
    fun prepareMocks() {
        Mockito.`when`(sp.getString(info.nightscout.pump.dana.R.string.key_danars_name, "")).thenReturn("")
        Mockito.`when`(sp.getString(info.nightscout.pump.dana.R.string.key_danars_address, "")).thenReturn("")
        Mockito.`when`(rh.gs(eq(info.nightscout.core.ui.R.string.limitingbasalratio), anyObject(), anyObject())).thenReturn("limitingbasalratio")
        Mockito.`when`(rh.gs(eq(info.nightscout.core.ui.R.string.limitingpercentrate), anyObject(), anyObject())).thenReturn("limitingpercentrate")

        danaRSPlugin =
            DanaRSPlugin(
                { AndroidInjector { } },
                aapsLogger,
                aapsSchedulers,
                rxBus,
                context,
                rh,
                constraintChecker,
                profileFunction,
                sp,
                commandQueue,
                danaPump,
                pumpSync,
                detailedBolusInfoStorage,
                temporaryBasalStorage,
                fabricPrivacy,
                dateUtil,
                uiInteraction,
                danaHistoryDatabase,
                decimalFormatter
            )
    }
}