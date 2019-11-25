package info.nightscout.androidaps.plugins.general.smsCommunicator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SmsCommunicatorPlugin.class, L.class, SP.class, MainApp.class, DateUtil.class})

public class AuthRequestTest {
    private SmsCommunicatorPlugin smsCommunicatorPlugin;
    private Sms sentSms;
    private boolean actionCalled = false;

    @Test
    public void doTests() {
        Sms requester = new Sms("aNumber", "aText");
        SmsAction action = new SmsAction() {
            @Override
            public void run() {
                actionCalled = true;
            }
        };

        // Check if SMS requesting code is sent
        AuthRequest authRequest = new AuthRequest(smsCommunicatorPlugin, requester, "Request text", "ABC", action);

        Assert.assertEquals(sentSms.getPhoneNumber(), "aNumber");
        Assert.assertEquals(sentSms.getText(), "Request text");

        // wrong reply
        actionCalled = false;
        authRequest.action("EFG");
        Assert.assertEquals(sentSms.getPhoneNumber(), "aNumber");
        Assert.assertEquals(sentSms.getText(), "Wrong code. Command cancelled.");
        Assert.assertFalse(actionCalled);

        // correct reply
        authRequest = new AuthRequest(smsCommunicatorPlugin, requester, "Request text", "ABC", action);
        actionCalled = false;
        authRequest.action("ABC");
        Assert.assertTrue(actionCalled);
        // second time action should not be called
        actionCalled = false;
        authRequest.action("ABC");
        Assert.assertFalse(actionCalled);

        // test timed out message
        long now = 10000;
        when(DateUtil.now()).thenReturn(now);
        authRequest = new AuthRequest(smsCommunicatorPlugin, requester, "Request text", "ABC", action);
        actionCalled = false;
        when(DateUtil.now()).thenReturn(now + T.mins(Constants.SMS_CONFIRM_TIMEOUT).msecs() + 1);
        authRequest.action("ABC");
        Assert.assertFalse(actionCalled);
    }

    @Before
    public void prepareTests() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockStrings();

        mockStatic(DateUtil.class);

        smsCommunicatorPlugin = mock(SmsCommunicatorPlugin.class);
        doAnswer((Answer) invocation -> {
            sentSms = invocation.getArgument(0);
            return null;
        }).when(smsCommunicatorPlugin).sendSMS(any(Sms.class));

    }
}
