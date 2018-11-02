package info.nightscout.androidaps.plugins.Source;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.plugins.Source.SourceMM640gPlugin;

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