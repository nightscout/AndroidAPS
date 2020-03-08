package info.nightscout.androidaps.plugins.pump.danaRS

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.AAPSMocker
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.pump.danaRS.comm.DanaRSTestBase
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.resources.ResourceHelper
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
@PrepareForTest(ConstraintChecker::class, RxBusWrapper::class, L::class, SP::class, MainApp::class)
class DanaRSPluginTest : DanaRSTestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var commandQueue: CommandQueueProvider
    private lateinit var danaRSPlugin: DanaRSPlugin
    lateinit var rxBus: RxBusWrapper

    @Test
    fun basalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHBASALRATE)
        danaRSPlugin.applyBasalConstraints(c, AAPSMocker.getValidProfile())
        Assert.assertEquals(java.lang.Double.valueOf(0.8), c.value(), 0.0001)
        Assert.assertEquals("DanaRS: limitingbasalratio", c.getReasons(aapsLogger))
        Assert.assertEquals("DanaRS: limitingbasalratio", c.getMostLimitedReasons(aapsLogger))
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPump.maxBasal = 0.8
        val c = Constraint(Constants.REALLYHIGHPERCENTBASALRATE)
        danaRSPlugin.applyBasalPercentConstraints(c, AAPSMocker.getValidProfile())
        Assert.assertEquals(200, c.value())
        Assert.assertEquals("DanaRS: limitingpercentrate", c.getReasons(aapsLogger))
        Assert.assertEquals("DanaRS: limitingpercentrate", c.getMostLimitedReasons(aapsLogger))
    }

    @Before
    fun prepareMocks() {
        AAPSMocker.mockMainApp() // TODO remove
        AAPSMocker.mockSP()
        AAPSMocker.mockL()
        AAPSMocker.mockStrings()
        Mockito.`when`(sp.getString(R.string.key_danars_address, "")).thenReturn("")
        Mockito.`when`(resourceHelper.gs(eq(R.string.limitingbasalratio), anyObject(), anyObject())).thenReturn("limitingbasalratio")
        Mockito.`when`(resourceHelper.gs(eq(R.string.limitingpercentrate), anyObject(), anyObject())).thenReturn("limitingpercentrate")

        rxBus = RxBusWrapper()
        danaRSPlugin = DanaRSPlugin(HasAndroidInjector { AndroidInjector { Unit } }, aapsLogger, rxBus, context, resourceHelper, constraintChecker, profileFunction, treatmentsPlugin, sp, commandQueue, danaRPump)
    }
}