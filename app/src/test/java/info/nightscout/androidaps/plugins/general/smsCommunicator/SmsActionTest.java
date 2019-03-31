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

public class SmsActionTest {
    String result = "";

    @Test
    public void doTests() {

        SmsAction smsAction = new SmsAction() {
            @Override
            public void run() {
                result = "A";
            }
        };
        smsAction.run();
        Assert.assertEquals(result, "A");

        smsAction = new SmsAction(1d) {
            @Override
            public void run() {
                result = "B";
            }
        };
        smsAction.run();
        Assert.assertEquals(result, "B");
        Assert.assertEquals(smsAction.aDouble, 1d, 0.000001d);

        smsAction = new SmsAction(1d, 2) {
            @Override
            public void run() {
                result = "C";
            }
        };
        smsAction.run();
        Assert.assertEquals(result, "C");
        Assert.assertEquals(smsAction.aDouble, 1d, 0.000001d);
        Assert.assertEquals(smsAction.secondInteger.intValue(), 2);

        smsAction = new SmsAction("aString", 3) {
            @Override
            public void run() {
                result = "D";
            }
        };
        smsAction.run();
        Assert.assertEquals(result, "D");
        Assert.assertEquals(smsAction.aString, "aString");
        Assert.assertEquals(smsAction.secondInteger.intValue(), 3);

        smsAction = new SmsAction(4) {
            @Override
            public void run() {
                result = "E";
            }
        };
        smsAction.run();
        Assert.assertEquals(result, "E");
        Assert.assertEquals(smsAction.anInteger.intValue(), 4);

        smsAction = new SmsAction(5, 6) {
            @Override
            public void run() {
                result = "F";
            }
        };
        smsAction.run();
        Assert.assertEquals(result, "F");
        Assert.assertEquals(smsAction.anInteger.intValue(), 5);
        Assert.assertEquals(smsAction.secondInteger.intValue(), 6);
    }

}
