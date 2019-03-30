package info.nightscout.androidaps.plugins.general.smsCommunicator;

import android.telephony.SmsMessage;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SmsMessage.class, MainApp.class})

public class SmsTest {

    @Test
    public void doTests() {
        SmsMessage smsMessage = mock(SmsMessage.class);
        when(smsMessage.getOriginatingAddress()).thenReturn("aNumber");
        when(smsMessage.getMessageBody()).thenReturn("aBody");

        Sms sms = new Sms(smsMessage);
        Assert.assertEquals(sms.phoneNumber, "aNumber");
        Assert.assertEquals(sms.text, "aBody");
        Assert.assertTrue(sms.received);

        sms = new Sms("aNumber", "aBody");
        Assert.assertEquals(sms.phoneNumber, "aNumber");
        Assert.assertEquals(sms.text, "aBody");
        Assert.assertTrue(sms.sent);

        sms = new Sms("aNumber", R.string.insulin_unit_shortname);
        Assert.assertEquals(sms.phoneNumber, "aNumber");
        Assert.assertEquals(sms.text, MainApp.gs(R.string.insulin_unit_shortname));
        Assert.assertTrue(sms.sent);

        Assert.assertEquals(sms.toString(), "SMS from aNumber: U");
    }

    @Before
    public void prepareTests() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
    }
}
