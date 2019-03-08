package info.nightscout.androidaps.plugins.source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class SourceGlimpPluginTest {

    @Test
    public void getPlugin() {
        Assert.assertNotEquals(null, SourceGlimpPlugin.getPlugin());
    }

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(false, SourceGlimpPlugin.getPlugin().advancedFilteringSupported());
    }
}