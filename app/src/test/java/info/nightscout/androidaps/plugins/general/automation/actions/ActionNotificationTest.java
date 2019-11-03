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
import info.nightscout.androidaps.plugins.general.automation.elements.InputString;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, NSUpload.class})
public class ActionNotificationTest {
    private ActionNotification actionNotification;

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.notification, actionNotification.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        actionNotification = new ActionNotification();
        actionNotification.text = new InputString().setValue("Asd");
        Assert.assertEquals(null, actionNotification.shortDescription()); // not mocked
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_notifications), actionNotification.icon());
    }

    @Test
    public void doActionTest() {
        actionNotification.doAction(new Callback() {
            @Override
            public void run() {
                Assert.assertTrue(result.success);
            }
        });
    }

    @Test
    public void hasDialogTest() {
        Assert.assertTrue(actionNotification.hasDialog());
    }

    @Test
    public void toJSONTest() {
        actionNotification = new ActionNotification();
        actionNotification.text = new InputString().setValue("Asd");
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionNotification\"}", actionNotification.toJSON());
    }

    @Test
    public void fromJSONTest() {
        actionNotification = new ActionNotification();
        actionNotification.fromJSON("{\"text\":\"Asd\"}");
        Assert.assertEquals("Asd", actionNotification.text.getValue());
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();
        AAPSMocker.mockNSUpload();

        actionNotification = new ActionNotification();
    }
}
