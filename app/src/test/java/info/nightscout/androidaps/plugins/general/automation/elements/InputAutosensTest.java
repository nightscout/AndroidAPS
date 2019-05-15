package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ProfileFunctions.class, SP.class})
public class InputAutosensTest {

    @Test
    public void textWatcherTest() {
        InputAutosens t = new InputAutosens().setValue(100);
        t.textWatcher.beforeTextChanged(null, 0, 0, 0);
        t.textWatcher.onTextChanged(null, 0, 0, 0);
        t.textWatcher.afterTextChanged(null);
        Assert.assertEquals(100, t.getValue(), 0.01d);

        t = new InputAutosens().setValue(200);
        t.textWatcher.afterTextChanged(null);
        Assert.assertEquals(200, t.getValue(), 0.01d);
    }

    @Test
    public void getSetValueTest() {
        when(SP.getDouble(anyInt(), anyDouble())).thenReturn(0.7d);
        InputAutosens i = new InputAutosens().setValue(500);
        Assert.assertEquals(500, i.getValue(), 0.01d);
        Assert.assertEquals(0, i.minValue, 0.01d);
        i = new InputAutosens().setValue(110);
        Assert.assertEquals(110, i.getValue(), 0.01d);
        Assert.assertEquals(0, i.minValue, 0.01d);
    }


    @Before
    public void prepare() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockBus();
        AAPSMocker.mockStrings();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockSP();
    }
}