package info.nightscout.androidaps.plugins.Source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.plugins.Source.SourceGlimpPlugin;

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