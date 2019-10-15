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
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.InputPercent;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, ProfileFunctions.class})
public class ActionProfileSwitchPercentTest {
    private ActionProfileSwitchPercent actionProfileSwitchPercent;

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.profilepercentage, actionProfileSwitchPercent.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        actionProfileSwitchPercent = new ActionProfileSwitchPercent();
        actionProfileSwitchPercent.pct = new InputPercent().setValue(100);
        actionProfileSwitchPercent.duration = new InputDuration(30, InputDuration.TimeUnit.MINUTES);
        Assert.assertNull(actionProfileSwitchPercent.shortDescription()); // not mocked
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.icon_actions_profileswitch), actionProfileSwitchPercent.icon());
    }

    @Test
    public void doActionTest() {
        actionProfileSwitchPercent.doAction(new Callback() {
            @Override
            public void run() {
                Assert.assertTrue(result.success);
            }
        });
    }

    @Test
    public void hasDialogTest() {
        Assert.assertTrue(actionProfileSwitchPercent.hasDialog());
    }

    @Test
    public void toJSONTest() {
        actionProfileSwitchPercent = new ActionProfileSwitchPercent();
        actionProfileSwitchPercent.pct = new InputPercent().setValue(100);
        actionProfileSwitchPercent.duration = new InputDuration(30, InputDuration.TimeUnit.MINUTES);
        Assert.assertEquals("{\"data\":{\"percentage\":100,\"durationInMinutes\":30},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionProfileSwitchPercent\"}", actionProfileSwitchPercent.toJSON());
    }

    @Test
    public void fromJSONTest() {
        actionProfileSwitchPercent = new ActionProfileSwitchPercent();
        actionProfileSwitchPercent.fromJSON("{\"percentage\":100,\"durationInMinutes\":30}");
        Assert.assertEquals(100, actionProfileSwitchPercent.pct.getValue(), 0.001d);
        Assert.assertEquals(30, actionProfileSwitchPercent.duration.getMinutes(), 0.001);
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();
        AAPSMocker.mockProfileFunctions();

        actionProfileSwitchPercent = new ActionProfileSwitchPercent();
    }
}
