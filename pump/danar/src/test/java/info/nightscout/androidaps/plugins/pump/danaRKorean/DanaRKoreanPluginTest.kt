package info.nightscout.androidaps.plugins.pump.danaRKorean

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danar.R
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileInstantiator
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRKoreanPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var profileInstantiator: ProfileInstantiator
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    lateinit var danaPump: DanaPump

    private lateinit var danaRPlugin: DanaRKoreanPlugin

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
        danaRPlugin = DanaRKoreanPlugin(injector, aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, activePluginProvider, sp, commandQueue, danaPump, dateUtil, fabricPrivacy,
                                        pumpSync, uiInteraction, danaHistoryDatabase)
    }

    @Test @Throws(Exception::class)
    fun basalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHBASALRATE)
        danaRPlugin.applyBasalConstraints(c, validProfile)
        Assert.assertEquals(0.8, c.value(), 0.01)
        Assert.assertEquals("DanaRKorean: Limiting max basal rate to 0.80 U/h because of pump limit", c.getReasons(aapsLogger))
        Assert.assertEquals("DanaRKorean: Limiting max basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons(aapsLogger))
    }

    @Test @Throws(Exception::class)
    fun percentBasalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHPERCENTBASALRATE)
        danaRPlugin.applyBasalPercentConstraints(c, validProfile)
        Assert.assertEquals(200, c.value())
        Assert.assertEquals("DanaRKorean: Limiting max percent rate to 200% because of pump limit", c.getReasons(aapsLogger))
        Assert.assertEquals("DanaRKorean: Limiting max percent rate to 200% because of pump limit", c.getMostLimitedReasons(aapsLogger))
    }
}