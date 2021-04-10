package info.nightscout.androidaps.plugins.pump.danaRv2

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.TemporaryBasalStorage
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, DetailedBolusInfoStorage::class, TemporaryBasalStorage::class)
class DanaRv2PluginTest : TestBaseWithProfile() {

    @Mock lateinit var context: Context
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var sp: SP
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync

    lateinit var danaPump: DanaPump

    private lateinit var danaRv2Plugin: DanaRv2Plugin

    val injector = HasAndroidInjector {
        AndroidInjector { }
    }

    @Before
    fun prepareMocks() {
        `when`(sp.getString(R.string.key_danars_address, "")).thenReturn("")
        `when`(resourceHelper.gs(R.string.pumplimit)).thenReturn("pump limit")
        `when`(resourceHelper.gs(R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(resourceHelper.gs(R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        danaPump = DanaPump(aapsLogger, sp, dateUtil, injector)
        danaRv2Plugin = DanaRv2Plugin(injector, aapsLogger, aapsSchedulers, rxBus, context, resourceHelper, constraintChecker, activePluginProvider, sp, commandQueue, danaPump,detailedBolusInfoStorage, temporaryBasalStorage, dateUtil, fabricPrivacy, pumpSync)
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