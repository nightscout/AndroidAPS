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
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, NSUpload.class})
public class ActionLoopResumeTest {
    ActionLoopResume actionLoopResume = new ActionLoopResume();

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.resumeloop, actionLoopResume.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        Assert.assertEquals("Resume loop", actionLoopResume.shortDescription());
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_replay_24dp), actionLoopResume.icon());
    }

    @Test
    public void doActionTest() {
        LoopPlugin.getPlugin().suspendTo(DateUtil.now() + T.hours(1).msecs());
        actionLoopResume.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertEquals(false, LoopPlugin.getPlugin().isSuspended());
        // another call should keep it resumed
        actionLoopResume.doAction(new Callback() {
            @Override
            public void run() {
            }
        });
        Assert.assertEquals(false, LoopPlugin.getPlugin().isSuspended());
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockDatabaseHelper();
        AAPSMocker.mockStrings();
        AAPSMocker.mockSP();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockNSUpload();
        AAPSMocker.mockCommandQueue();

    }
}
