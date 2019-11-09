package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.SPMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, TreatmentsPlugin.class, ProfileFunctions.class, ConfigBuilderPlugin.class, NSProfilePlugin.class})
public class ActionProfileSwitchTest {
    TreatmentsPlugin treatmentsPlugin;
    ProfileSwitch profileAdded;
    private ActionProfileSwitch actionProfileSwitch;
    private  String stringJson = "{\"data\":{\"profileToSwitchTo\":\"Test\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionProfileSwitch\"}";

    @Before
    public void setUp() {
        AAPSMocker.mockMainApp();
        SPMocker.prepareMock();
        SP.putString("profile", AAPSMocker.getValidProfileStore().getData().toString());
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockStrings();
        AAPSMocker.mockTreatmentService();
        AAPSMocker.mockDatabaseHelper();
        AAPSMocker.mockProfileFunctions();

        treatmentsPlugin = AAPSMocker.mockTreatmentPlugin();
        NSProfilePlugin profilePlugin = NSProfilePlugin.getPlugin();
        when(ConfigBuilderPlugin.getPlugin().getActiveProfileInterface())
                .thenReturn(profilePlugin);

        Mockito.doAnswer(invocation -> {
            profileAdded = invocation.getArgument(0);
            return null;
        }).when(treatmentsPlugin).addToHistoryProfileSwitch(any(ProfileSwitch.class));
    }

    @Test
    public void friendlyName() {
        actionProfileSwitch = new ActionProfileSwitch();
        Assert.assertEquals(R.string.profilename, actionProfileSwitch.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        actionProfileSwitch = new ActionProfileSwitch();
        actionProfileSwitch.inputProfileName.setValue("Test");
        when(MainApp.gs(anyInt(), anyString())).thenReturn("Change profile to Test");
        Assert.assertEquals("Change profile to Test", actionProfileSwitch.shortDescription());
    }

    @Test
    public void doAction() {
        PowerMockito.when(ProfileFunctions.getInstance().getProfileName()).thenReturn("Test");

        actionProfileSwitch = new ActionProfileSwitch();
        actionProfileSwitch.inputProfileName.setValue("someOtherProfile");
        actionProfileSwitch.profileName = "someProfile";

        actionProfileSwitch.doAction(new Callback() {
            @Override
            public void run() {
                Assert.assertTrue(result.success);
            }
        });

    }

    @Test
    public void hasDialogTest() {
        actionProfileSwitch = new ActionProfileSwitch();
        Assert.assertTrue(actionProfileSwitch.hasDialog());
    }

    @Test
    public void toJSONTest() {
        actionProfileSwitch = new ActionProfileSwitch();
        actionProfileSwitch.inputProfileName.setValue("Test");
        Assert.assertEquals(stringJson, actionProfileSwitch.toJSON());
    }

    @Test
    public void fromJSONTest() {
        String data = "{\"profileToSwitchTo\":\"Test\"}";
        actionProfileSwitch = new ActionProfileSwitch();
        actionProfileSwitch.fromJSON(data);
        Assert.assertEquals("Test", actionProfileSwitch.profileName);
    }

    @Test
    public void iconTest() {
        actionProfileSwitch = new ActionProfileSwitch();
        Assert.assertEquals(Optional.of(R.drawable.icon_actions_profileswitch), actionProfileSwitch.icon());
    }
}