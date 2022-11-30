package info.nightscout.pump.danars

import android.content.Context
import dagger.android.AndroidInjector
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito

@Suppress("SpellCheckingInspection")
class DanaRSPluginTest : DanaRSTestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var constraintChecker: Constraints
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
        val c = Constraint(Constants.REALLYHIGHBASALRATE)
        danaRSPlugin.applyBasalConstraints(c, validProfile)
        Assert.assertEquals(java.lang.Double.valueOf(0.8), c.value(), 0.0001)
        Assert.assertEquals("DanaRS: limitingbasalratio", c.getReasons(aapsLogger))
        Assert.assertEquals("DanaRS: limitingbasalratio", c.getMostLimitedReasons(aapsLogger))
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHPERCENTBASALRATE)
        danaRSPlugin.applyBasalPercentConstraints(c, validProfile)
        Assert.assertEquals(200, c.value())
        Assert.assertEquals("DanaRS: limitingpercentrate", c.getReasons(aapsLogger))
        Assert.assertEquals("DanaRS: limitingpercentrate", c.getMostLimitedReasons(aapsLogger))
    }

    @BeforeEach
    fun prepareMocks() {
        Mockito.`when`(sp.getString(R.string.key_danars_address, "")).thenReturn("")
        Mockito.`when`(rh.gs(eq(R.string.limitingbasalratio), anyObject(), anyObject())).thenReturn("limitingbasalratio")
        Mockito.`when`(rh.gs(eq(R.string.limitingpercentrate), anyObject(), anyObject())).thenReturn("limitingpercentrate")

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
                danaHistoryDatabase
            )
    }
}