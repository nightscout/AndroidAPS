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
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, NSUpload.class})
public class ActionLoopSuspendTest {
    ActionLoopSuspend actionLoopSuspend = new ActionLoopSuspend();

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.suspendloop, actionLoopSuspend.friendlyName());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_pause_circle_outline_24dp), actionLoopSuspend.icon());
    }

    @Test
    public void doActionTest() {
        actionLoopSuspend.minutes = 30;

        LoopPlugin.getPlugin().suspendTo(0);
        actionLoopSuspend.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertEquals(true, LoopPlugin.getPlugin().isSuspended());
        // another call should keep it suspended
        actionLoopSuspend.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertEquals(true, LoopPlugin.getPlugin().isSuspended());
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockBus();
        AAPSMocker.mockSP();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockNSUpload();
        AAPSMocker.mockCommandQueue();

    }
}
