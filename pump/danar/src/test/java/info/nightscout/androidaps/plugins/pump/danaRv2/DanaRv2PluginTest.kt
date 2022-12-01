package info.nightscout.androidaps.plugins.pump.danaRv2

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.R
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileInstantiator
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRv2PluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var profileInstantiator: ProfileInstantiator
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    lateinit var danaPump: DanaPump

    private lateinit var danaRv2Plugin: DanaRv2Plugin

    val injector = HasAndroidInjector {
        AndroidInjector { }
    }

    @BeforeEach
    fun prepareMocks() {
        `when`(sp.getString(R.string.key_danars_address, "")).thenReturn("")
        `when`(rh.gs(R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        danaPump = DanaPump(aapsLogger, sp, dateUtil, profileInstantiator)
        danaRv2Plugin = DanaRv2Plugin(injector, aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, activePluginProvider, sp, commandQueue, danaPump, detailedBolusInfoStorage,
                                      temporaryBasalStorage, dateUtil, fabricPrivacy, pumpSync, uiInteraction, danaHistoryDatabase)
    }

    @Test
    fun basalRateShouldBeLimited() {
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true)
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHBASALRATE)
        danaRv2Plugin.applyBasalConstraints(c, validProfile)
        org.junit.Assert.assertEquals(0.8, c.value(), 0.01)
        org.junit.Assert.assertEquals("DanaRv2: Limiting max basal rate to 0.80 U/h because of pump limit", c.getReasons(aapsLogger))
        org.junit.Assert.assertEquals("DanaRv2: Limiting max basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons(aapsLogger))
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true)
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHPERCENTBASALRATE)
        danaRv2Plugin.applyBasalPercentConstraints(c, validProfile)
        org.junit.Assert.assertEquals(200, c.value())
        org.junit.Assert.assertEquals("DanaRv2: Limiting max percent rate to 200% because of pump limit", c.getReasons(aapsLogger))
        org.junit.Assert.assertEquals("DanaRv2: Limiting max percent rate to 200% because of pump limit", c.getMostLimitedReasons(aapsLogger))
    }
}