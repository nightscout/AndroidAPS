package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

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
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class})
public class ActionLoopEnableTest {
    ActionLoopEnable actionLoopEnable = new ActionLoopEnable();

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.enableloop, actionLoopEnable.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        Assert.assertEquals(MainApp.gs(R.string.enableloop), actionLoopEnable.shortDescription());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_play_circle_outline_24dp), actionLoopEnable.icon());
    }

    @Test
    public void doActionTest() {
        LoopPlugin.getPlugin().setPluginEnabled(PluginType.LOOP, false);
        actionLoopEnable.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertEquals(true, LoopPlugin.getPlugin().isEnabled(PluginType.LOOP));
        // another call should keep it enabled
        actionLoopEnable.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertEquals(true, LoopPlugin.getPlugin().isEnabled(PluginType.LOOP));
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockCommandQueue();

        VirtualPumpPlugin pump = mock(VirtualPumpPlugin.class);
        when(pump.specialEnableCondition()).thenReturn(true);
        PumpDescription pumpDescription = new PumpDescription();
        pumpDescription.isTempBasalCapable = true;
        when(pump.getPumpDescription()).thenReturn(pumpDescription);
        when(AAPSMocker.configBuilderPlugin.getActivePump()).thenReturn(pump);

    }
}
