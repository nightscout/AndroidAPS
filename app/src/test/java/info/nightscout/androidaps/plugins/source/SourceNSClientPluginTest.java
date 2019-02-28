package info.nightscout.androidaps.plugins.source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class SourceNSClientPluginTest {

    @Test
    public void getPlugin() {
        Assert.assertNotEquals(null, SourceNSClientPlugin.getPlugin());
    }

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(false, SourceNSClientPlugin.getPlugin().advancedFilteringSupported());
    }
}