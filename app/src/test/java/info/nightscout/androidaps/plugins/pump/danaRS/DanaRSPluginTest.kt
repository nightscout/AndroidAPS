package info.nightscout.androidaps.plugins.pump.danaRS

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.danars.comm.DanaRSTestBase
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, RxBusWrapper::class, DetailedBolusInfoStorage::class)
class DanaRSPluginTest : DanaRSTestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage

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

    @Before
    fun prepareMocks() {
        Mockito.`when`(sp.getString(R.string.key_danars_address, "")).thenReturn("")
        Mockito.`when`(resourceHelper.gs(eq(R.string.limitingbasalratio), anyObject(), anyObject())).thenReturn("limitingbasalratio")
        Mockito.`when`(resourceHelper.gs(eq(R.string.limitingpercentrate), anyObject(), anyObject())).thenReturn("limitingpercentrate")

        danaRSPlugin = DanaRSPlugin(HasAndroidInjector { AndroidInjector { Unit } }, aapsLogger, aapsSchedulers, rxBus, context, resourceHelper, constraintChecker, profileFunction, activePluginProvider, sp, commandQueue, danaPump, detailedBolusInfoStorage, fabricPrivacy, dateUtil)
    }
}