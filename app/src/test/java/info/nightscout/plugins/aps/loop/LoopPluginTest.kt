package info.nightscout.plugins.aps.loop

import android.app.NotificationManager
import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.ApsMode
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.configBuilder.RunningConfiguration
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.virtual.VirtualPumpPlugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class LoopPluginTest : TestBase() {

    @Mock lateinit var sp: SP
    private val rxBus: RxBus = RxBus(aapsSchedulers, aapsLogger)
    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var context: Context
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var config: Config
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var loopPlugin: LoopPlugin

    private val injector = HasAndroidInjector { AndroidInjector { } }
    @BeforeEach fun prepareMock() {

        loopPlugin = LoopPlugin(
            injector, aapsLogger, aapsSchedulers, rxBus, sp, config,
            constraintChecker, rh, profileFunction, context, commandQueue, activePlugin, virtualPumpPlugin, iobCobCalculator, receiverStatusStore, fabricPrivacy, dateUtil, uel,
            repository, runningConfiguration, uiInteraction
        )
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
    }

    @Test
    fun testPluginInterface() {
        `when`(rh.gs(info.nightscout.core.ui.R.string.loop)).thenReturn("Loop")
        `when`(rh.gs(info.nightscout.plugins.aps.R.string.loop_shortname)).thenReturn("LOOP")
        `when`(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)).thenReturn(ApsMode.CLOSED.name)
        val pumpDescription = PumpDescription()
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        Assertions.assertEquals(LoopFragment::class.java.name, loopPlugin.pluginDescription.fragmentClass)
        Assertions.assertEquals(PluginType.LOOP, loopPlugin.getType())
        Assertions.assertEquals("Loop", loopPlugin.name)
        Assertions.assertEquals("LOOP", loopPlugin.nameShort)
        Assertions.assertEquals(true, loopPlugin.hasFragment())
        Assertions.assertEquals(true, loopPlugin.showInList(PluginType.LOOP))
        Assertions.assertEquals(info.nightscout.plugins.aps.R.xml.pref_loop.toLong(), loopPlugin.preferencesId.toLong())

        // Plugin is disabled by default
        Assertions.assertEquals(false, loopPlugin.isEnabled())
        loopPlugin.setPluginEnabled(PluginType.LOOP, true)
        Assertions.assertEquals(true, loopPlugin.isEnabled())

        // No temp basal capable pump should disable plugin
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = false
        Assertions.assertEquals(false, loopPlugin.isEnabled())
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = true

        // Fragment is hidden by default
        Assertions.assertEquals(false, loopPlugin.isFragmentVisible())
        loopPlugin.setFragmentVisible(PluginType.LOOP, true)
        Assertions.assertEquals(true, loopPlugin.isFragmentVisible())
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
        Assertions.assertEquals(true, mockedLoopPlugin.invokeCalled);
    }
*/
}