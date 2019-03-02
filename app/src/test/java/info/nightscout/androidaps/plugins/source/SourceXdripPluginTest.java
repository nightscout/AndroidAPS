package info.nightscout.androidaps.plugins.source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class SourceXdripPluginTest {

    @Test
    public void getPlugin() {
        Assert.assertNotEquals(null, SourceXdripPlugin.getPlugin());
    }

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(false, SourceXdripPlugin.getPlugin().advancedFilteringSupported());
    }
}