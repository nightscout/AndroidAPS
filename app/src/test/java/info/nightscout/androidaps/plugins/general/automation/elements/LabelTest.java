package info.nightscout.androidaps.plugins.general.automation.elements;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
public class LabelTest {

    @Test
    public void constructorTest() {
        Label l = new Label("A", "B", new InputInsulin());
        Assert.assertEquals("A", l.textPre);
        Assert.assertEquals("B", l.textPost);
        Assert.assertEquals(InputInsulin.class, l.element.getClass());
    }

}