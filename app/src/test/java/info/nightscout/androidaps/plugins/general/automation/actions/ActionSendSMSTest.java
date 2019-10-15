package info.nightscout.androidaps.plugins.general.automation.actions;

import android.telephony.SmsManager;

import com.google.common.base.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.automation.elements.InputString;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.SP;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class, SmsManager.class})
public class ActionSendSMSTest {
    private ActionSendSMS actionSendSMS;

    @Test
    public void friendlyNameTest() {
        Assert.assertEquals(R.string.sendsmsactiondescription, actionSendSMS.friendlyName());
    }

    @Test
    public void shortDescriptionTest() {
        actionSendSMS = new ActionSendSMS();
        Assert.assertEquals(null, actionSendSMS.shortDescription()); // not mocked
    }

    @Test
    public void iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_notifications), actionSendSMS.icon());
    }

    @Test
    public void doActionTest() {
        actionSendSMS.text = new InputString().setValue("Asd");
        actionSendSMS.doAction(new Callback() {
            @Override
            public void run() {
                Assert.assertTrue(result.success);
            }
        });
    }

    @Test
    public void hasDialogTest() {
        Assert.assertTrue(actionSendSMS.hasDialog());
    }

    @Test
    public void toJSONTest() {
        actionSendSMS = new ActionSendSMS();
        actionSendSMS.text = new InputString().setValue("Asd");
        Assert.assertEquals("{\"data\":{\"text\":\"Asd\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionSendSMS\"}", actionSendSMS.toJSON());
    }

    @Test
    public void fromJSONTest() {
        actionSendSMS = new ActionSendSMS();
        actionSendSMS.fromJSON("{\"text\":\"Asd\"}");
        Assert.assertEquals("Asd", actionSendSMS.text.getValue());
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockSP();
        mockStatic(SmsManager.class);
        SmsManager smsManager = mock(SmsManager.class);
        PowerMockito.when(SmsManager.getDefault()).thenReturn(smsManager);
        PowerMockito.when(SP.getString(R.string.key_smscommunicator_allowednumbers, "")).thenReturn("1234;5678");
        actionSendSMS = new ActionSendSMS();
    }
}
