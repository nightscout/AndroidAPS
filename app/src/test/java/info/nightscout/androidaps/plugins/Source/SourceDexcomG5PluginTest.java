package info.nightscout.androidaps.plugins.Source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.Config;

@RunWith(PowerMockRunner.class)
public class SourceDexcomG5PluginTest {

    @Test
    public void getPlugin() {
        Assert.assertNotEquals(null, SourceDexcomG5Plugin.getPlugin());
    }

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(true, SourceDexcomG5Plugin.getPlugin().advancedFilteringSupported());
    }
}