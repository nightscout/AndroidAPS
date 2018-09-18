package info.nightscout.androidaps.plugins.general.automation.actions;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({})
public class TriggerOrTest {

    @Test
    public void doTests() {
        TriggerOr t = new TriggerOr();
        TriggerOr t2 = new TriggerOr();
        TriggerOr t3 = new TriggerOr();

        Assert.assertTrue(t.size() == 0);

        t.add(t2);
        Assert.assertTrue(t.size() == 1);
        Assert.assertEquals(t2, t.get(0));

        t.add(t3);
        Assert.assertTrue(t.size() == 2);
        Assert.assertEquals(t2, t.get(0));
        Assert.assertEquals(t3, t.get(1));

        t.remove(t2);
        Assert.assertTrue(t.size() == 1);
        Assert.assertEquals(t3, t.get(0));

        Assert.assertFalse(t.shouldRun());
    }
}
