package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class StaticLabelTest {

    @Test
    public void constructor() {
        StaticLabel sl = new StaticLabel("any");
        Assert.assertEquals("any", sl.label);

        sl = new StaticLabel(R.string.pumplimit);
        Assert.assertEquals(MainApp.gs(R.string.pumplimit), sl.label);
    }

    @Before
    public void prepareTest() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
    }
}