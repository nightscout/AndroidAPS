package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class InputInsulinTest {

    @Test
    public void textWatcherTest() {
        InputInsulin t = new InputInsulin().setValue(30d);
        t.textWatcher.beforeTextChanged(null, 0, 0, 0);
        t.textWatcher.onTextChanged(null, 0, 0, 0);
        t.textWatcher.afterTextChanged(null);
        Assert.assertEquals(20d, t.getValue(), 0.01d);
    }

    @Test
    public void getSetValueTest() {
        InputInsulin i = new InputInsulin().setValue(5d);
        Assert.assertEquals(5d, i.getValue(), 0.01d);
    }
}