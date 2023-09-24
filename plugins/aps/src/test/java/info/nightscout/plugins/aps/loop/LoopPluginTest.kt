package info.nightscout.plugins.aps.loop

import android.app.NotificationManager
import android.content.Context
import app.aaps.interfaces.aps.ApsMode
import app.aaps.interfaces.configuration.Config
import app.aaps.interfaces.constraints.ConstraintsChecker
import app.aaps.interfaces.iob.IobCobCalculator
import app.aaps.interfaces.logging.UserEntryLogger
import app.aaps.interfaces.plugin.ActivePlugin
import app.aaps.interfaces.plugin.PluginType
import app.aaps.interfaces.profile.ProfileFunction
import app.aaps.interfaces.pump.defs.PumpDescription
import app.aaps.interfaces.queue.CommandQueue
import app.aaps.interfaces.receivers.ReceiverStatusStore
import app.aaps.interfaces.resources.ResourceHelper
import app.aaps.interfaces.sharedPreferences.SP
import app.aaps.interfaces.ui.UiInteraction
import app.aaps.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.impl.AppRepository
import info.nightscout.pump.virtual.VirtualPumpPlugin
import info.nightscout.sdk.interfaces.RunningConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class LoopPluginTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var constraintChecker: ConstraintsChecker
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
        assertThat(loopPlugin.pluginDescription.fragmentClass).isEqualTo(LoopFragment::class.java.name)
        assertThat(loopPlugin.getType()).isEqualTo(PluginType.LOOP)
        assertThat(loopPlugin.name).isEqualTo("Loop")
        assertThat(loopPlugin.nameShort).isEqualTo("LOOP")
        assertThat(loopPlugin.hasFragment()).isTrue()
        assertThat(loopPlugin.showInList(PluginType.LOOP)).isTrue()
        assertThat(loopPlugin.preferencesId.toLong()).isEqualTo(info.nightscout.plugins.aps.R.xml.pref_loop.toLong())

        // Plugin is disabled by default
        assertThat(loopPlugin.isEnabled()).isFalse()
        loopPlugin.setPluginEnabled(PluginType.LOOP, true)
        assertThat(loopPlugin.isEnabled()).isTrue()

        // No temp basal capable pump should disable plugin
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = false
        assertThat(loopPlugin.isEnabled()).isFalse()
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = true

        // Fragment is hidden by default
        assertThat(loopPlugin.isFragmentVisible()).isFalse()
        loopPlugin.setFragmentVisible(PluginType.LOOP, true)
        assertThat(loopPlugin.isFragmentVisible()).isTrue()
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
        assertThat(mockedLoopPlugin.invokeCalled).isTrue();
    }
*/
}
