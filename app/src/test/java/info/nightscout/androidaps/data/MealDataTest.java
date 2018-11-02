package info.nightscout.androidaps.data;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Created by mike on 26.03.2018.
 */
@RunWith(PowerMockRunner.class)
public class MealDataTest {
    @Test
    public void canCreateObject() {
        MealData md = new MealData();
        Assert.assertEquals(0d, md.boluses, 0.01d);
    }
}
