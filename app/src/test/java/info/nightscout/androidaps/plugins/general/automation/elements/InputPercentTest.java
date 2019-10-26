package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class InputPercentTest {

    @Test
    public void textWatcherTest() {
        InputPercent t = new InputPercent().setValue(530d);
        t.textWatcher.beforeTextChanged(null, 0, 0, 0);
        t.textWatcher.onTextChanged(null, 0, 0, 0);
        t.textWatcher.afterTextChanged(null);
        Assert.assertEquals(130d, t.getValue(), 0.01d);
    }

    @Test
    public void getSetValueTest() {
        InputPercent i = new InputPercent().setValue(10d);
        Assert.assertEquals(10d, i.getValue(), 0.01d);
    }
}