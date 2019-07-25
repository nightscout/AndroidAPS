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
public class InputBgTest {

    @Test
    public void textWatcherTest() {
        InputBg t = new InputBg().setUnits(Constants.MMOL).setValue(1d);
        t.textWatcher.beforeTextChanged(null, 0, 0, 0);
        t.textWatcher.onTextChanged(null, 0, 0, 0);
        t.textWatcher.afterTextChanged(null);
        Assert.assertEquals(4d, t.getValue(), 0.01d);

        t = new InputBg().setValue(300d).setUnits(Constants.MGDL);
        t.textWatcher.afterTextChanged(null);
        Assert.assertEquals(270d, t.getValue(), 0.01d);
    }

    @Test
    public void getSetValueTest() {
        InputBg i = new InputBg().setUnits(Constants.MMOL).setValue(5d);
        Assert.assertEquals(5d, i.getValue(), 0.01d);
        Assert.assertEquals(2, i.minValue, 0.01d);
        i = new InputBg().setValue(100d).setUnits(Constants.MGDL);
        Assert.assertEquals(100d, i.getValue(), 0.01d);
        Assert.assertEquals(40, i.minValue, 0.01d);
        Assert.assertEquals(Constants.MGDL, i.getUnits());
    }


    @Before
    public void prepare() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockBus();
        AAPSMocker.mockStrings();
        AAPSMocker.mockProfileFunctions();
    }
}