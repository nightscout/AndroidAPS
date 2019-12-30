package info.nightscout.androidaps.plugins.source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class MM640GPluginTest {

    @Test
    public void getPlugin() {
        Assert.assertNotEquals(null, MM640gPlugin.getPlugin());
    }

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(false, MM640gPlugin.getPlugin().advancedFilteringSupported());
    }
}