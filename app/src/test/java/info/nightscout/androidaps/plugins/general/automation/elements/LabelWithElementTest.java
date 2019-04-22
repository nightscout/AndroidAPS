package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class LabelWithElementTest {

    @Test
    public void constructorTest() {
        LabelWithElement l = new LabelWithElement("A", "B", new InputInsulin());
        Assert.assertEquals("A", l.textPre);
        Assert.assertEquals("B", l.textPost);
        Assert.assertEquals(InputInsulin.class, l.element.getClass());
    }

}