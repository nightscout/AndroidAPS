package info.nightscout.androidaps.plugins.Source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.plugins.Source.SourceXdripPlugin;

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