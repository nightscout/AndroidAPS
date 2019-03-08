package info.nightscout.androidaps.plugins.source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class SourceMM640gPluginTest {

    @Test
    public void getPlugin() {
        Assert.assertNotEquals(null, SourceMM640gPlugin.getPlugin());
    }

    @Test
    public void advancedFilteringSupported() {
        Assert.assertEquals(false, SourceMM640gPlugin.getPlugin().advancedFilteringSupported());
    }
}