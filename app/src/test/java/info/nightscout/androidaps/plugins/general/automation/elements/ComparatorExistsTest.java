package info.nightscout.androidaps.plugins.general.automation.elements;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class ComparatorExistsTest {

    @Test
    public void labelsTest() {
        Assert.assertEquals(2, ComparatorExists.Compare.labels().size());
    }

    @Test
    public void getSetValueTest() {
        ComparatorExists c = new ComparatorExists().setValue(ComparatorExists.Compare.NOT_EXISTS);
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, c.getValue());
    }

    @Before
    public void prepare() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
    }
}