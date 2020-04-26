package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class InputDurationTest {

    @Test
    public void getSetValueTest() {
        InputDuration i = new InputDuration(5, InputDuration.TimeUnit.MINUTES);
        Assert.assertEquals(5, i.getValue(), 0.01d);
        Assert.assertEquals(InputDuration.TimeUnit.MINUTES, i.getUnit());
        i = i = new InputDuration(5, InputDuration.TimeUnit.HOURS);
        Assert.assertEquals(5d, i.getValue(), 0.01d);
        Assert.assertEquals(InputDuration.TimeUnit.HOURS, i.getUnit());
        Assert.assertEquals(5 * 60, i.getMinutes());
        i.setMinutes(60);
        Assert.assertEquals(1, i.getValue(), 0.01d);
    }
}