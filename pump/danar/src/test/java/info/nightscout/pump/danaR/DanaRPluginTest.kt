package info.nightscout.pump.danaR

import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBaseWithProfile
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var instantiator: Instantiator
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    lateinit var danaPump: DanaPump

    private lateinit var danaRPlugin: DanaRPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }

    @BeforeEach
    fun prepareMocks() {
        `when`(sp.getString(info.nightscout.pump.dana.R.string.key_danars_address, "")).thenReturn("")
        `when`(sp.getString(info.nightscout.pump.dana.R.string.key_danar_bt_name, "")).thenReturn("")
        `when`(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        danaPump = DanaPump(aapsLogger, sp, dateUtil, instantiator, decimalFormatter)
        danaRPlugin = DanaRPlugin(
            injector, aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, activePlugin, sp, commandQueue, danaPump, dateUtil, fabricPrivacy, pumpSync,
            uiInteraction, danaHistoryDatabase, decimalFormatter
        )
    }

    @Test @Throws(Exception::class)
    fun basalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        danaRPlugin.applyBasalConstraints(c, validProfile)
        Assertions.assertEquals(0.8, c.value(), 0.01)
        Assertions.assertEquals("DanaR: Limiting max basal rate to 0.80 U/h because of pump limit", c.getReasons())
        Assertions.assertEquals("DanaR: Limiting max basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons())
    }

    @Test @Throws(Exception::class)
    fun percentBasalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        danaRPlugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(200, c.value())
        Assertions.assertEquals("DanaR: Limiting max percent rate to 200% because of pump limit", c.getReasons())
        Assertions.assertEquals("DanaR: Limiting max percent rate to 200% because of pump limit", c.getMostLimitedReasons())
    }
}