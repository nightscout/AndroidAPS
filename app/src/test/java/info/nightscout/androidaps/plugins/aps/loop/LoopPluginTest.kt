package info.nightscout.androidaps.plugins.aps.loop

import android.app.NotificationManager
import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.utils.buildHelper.ConfigImpl
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.RunningConfiguration
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, ReceiverStatusStore::class, RunningConfiguration::class, UserEntryLogger::class, DateUtil::class)
class LoopPluginTest : TestBase() {

    @Mock lateinit var sp: SP
    private val rxBus: RxBusWrapper = RxBusWrapper(aapsSchedulers)
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var context: Context
    @Mock lateinit var commandQueue: CommandQueueProvider
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var uel:UserEntryLogger
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var runningConfiguration: RunningConfiguration

    private lateinit var loopPlugin: LoopPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }
    @Before fun prepareMock() {

        loopPlugin = LoopPlugin(injector, aapsLogger, aapsSchedulers, rxBus, sp, ConfigImpl(), constraintChecker, resourceHelper, profileFunction, context, commandQueue, activePlugin, virtualPumpPlugin, iobCobCalculator, receiverStatusStore, fabricPrivacy, dateUtil, uel, repository, runningConfiguration)
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
    }

    @Test
    fun testPluginInterface() {
        `when`(resourceHelper.gs(R.string.loop)).thenReturn("Loop")
        `when`(resourceHelper.gs(R.string.loop_shortname)).thenReturn("LOOP")
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        val pumpDescription = PumpDescription()
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        Assert.assertEquals(LoopFragment::class.java.name, loopPlugin.pluginDescription.fragmentClass)
        Assert.assertEquals(PluginType.LOOP, loopPlugin.getType())
        Assert.assertEquals("Loop", loopPlugin.name)
        Assert.assertEquals("LOOP", loopPlugin.nameShort)
        Assert.assertEquals(true, loopPlugin.hasFragment())
        Assert.assertEquals(true, loopPlugin.showInList(PluginType.LOOP))
        Assert.assertEquals(R.xml.pref_loop.toLong(), loopPlugin.preferencesId.toLong())

        // Plugin is disabled by default
        Assert.assertEquals(false, loopPlugin.isEnabled(PluginType.LOOP))
        loopPlugin.setPluginEnabled(PluginType.LOOP, true)
        Assert.assertEquals(true, loopPlugin.isEnabled(PluginType.LOOP))

        // No temp basal capable pump should disable plugin
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = false
        Assert.assertEquals(false, loopPlugin.isEnabled(PluginType.LOOP))
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = true

        // Fragment is hidden by default
        Assert.assertEquals(false, loopPlugin.isFragmentVisible())
        loopPlugin.setFragmentVisible(PluginType.LOOP, true)
        Assert.assertEquals(true, loopPlugin.isFragmentVisible())
    }

    /* ***********  not working
    @Test
    public void eventTreatmentChangeShouldTriggerInvoke() {

        // Unregister tested plugin to prevent calling real invoke
        MainApp.bus().unregister(loopPlugin);

        class MockedLoopPlugin extends LoopPlugin {
            boolean invokeCalled = false;

            @Override
            public void invoke(String initiator, boolean allowNotification) {
                invokeCalled = true;
            }

        }

        MockedLoopPlugin mockedLoopPlugin = new MockedLoopPlugin();
        Treatment t = new Treatment();
        bus.post(new EventTreatmentChange(t));
        Assert.assertEquals(true, mockedLoopPlugin.invokeCalled);
    }
*/
}