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
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class})
public class InputTempTargetTest {

    @Test
    public void getSetValueTest() {
        InputTempTarget i = new InputTempTarget().setUnits(Constants.MMOL).setValue(5d);
        Assert.assertEquals(5d, i.getValue(), 0.01d);
        Assert.assertEquals(Constants.MIN_TT_MMOL, i.minValue, 0.01d);
        i = new InputTempTarget().setValue(100d).setUnits(Constants.MGDL);
        Assert.assertEquals(100d, i.getValue(), 0.01d);
        Assert.assertEquals(Constants.MIN_TT_MGDL, i.minValue, 0.01d);
        Assert.assertEquals(Constants.MGDL, i.getUnits());
    }


    @Before
    public void prepare() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
        AAPSMocker.mockProfileFunctions();
    }
}