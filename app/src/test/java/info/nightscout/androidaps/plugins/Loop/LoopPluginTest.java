package info.nightscout.androidaps.plugins.Loop;

import android.content.Context;

import com.squareup.otto.Bus;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.utils.SP;

import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by mike on 23.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, Context.class})
public class LoopPluginTest {

    VirtualPumpPlugin virtualPumpPlugin;
    LoopPlugin loopPlugin;
    MockedBus bus;

    @Test
    public void testPluginInterface() {
        Assert.assertEquals(LoopFragment.class.getName(), loopPlugin.pluginDescription.getFragmentClass());
        Assert.assertEquals(PluginType.LOOP, loopPlugin.getType());
        Assert.assertEquals("Loop", loopPlugin.getName());
        Assert.assertEquals("LOOP", loopPlugin.getNameShort());
        Assert.assertEquals(true, loopPlugin.hasFragment());
        Assert.assertEquals(true, loopPlugin.showInList(PluginType.LOOP));
        Assert.assertEquals(R.xml.pref_closedmode, loopPlugin.getPreferencesId());

        // Plugin is disabled by default
        Assert.assertEquals(false, loopPlugin.isEnabled(PluginType.LOOP));
        loopPlugin.setPluginEnabled(PluginType.LOOP, true);
        Assert.assertEquals(true, loopPlugin.isEnabled(PluginType.LOOP));

        // No temp basal capable pump should disable plugin
        virtualPumpPlugin.getPumpDescription().isTempBasalCapable = false;
        Assert.assertEquals(false, loopPlugin.isEnabled(PluginType.LOOP));
        virtualPumpPlugin.getPumpDescription().isTempBasalCapable = true;


        // Fragment is hidden by default
        Assert.assertEquals(false, loopPlugin.isFragmentVisible());
        loopPlugin.setFragmentVisible(PluginType.LOOP, true);
        Assert.assertEquals(true, loopPlugin.isFragmentVisible());

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
    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();

        bus = new MockedBus();
        when(MainApp.bus()).thenReturn(bus);

        loopPlugin = LoopPlugin.getPlugin();
        virtualPumpPlugin = VirtualPumpPlugin.getPlugin();

        when(ConfigBuilderPlugin.getActivePump()).thenReturn(virtualPumpPlugin);
    }

    class MockedBus extends Bus {
    }


}
