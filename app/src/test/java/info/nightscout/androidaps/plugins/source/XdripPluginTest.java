package info.nightscout.androidaps.plugins.source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class XdripPluginTest {

    @Test
    public void getPlugin() {
        Assert.assertNotEquals(null, XdripPlugin.getPlugin());
    }

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(false, XdripPlugin.getPlugin().advancedFilteringSupported());
    }
}