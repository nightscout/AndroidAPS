package info.nightscout.androidaps.plugins.ConfigBuilder;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
public class ConfigBuilderPluginTest {

    @Test
    public void getPluginTest() {
        ConfigBuilderPlugin configBuilderPlugin = ConfigBuilderPlugin.getPlugin();
        Assert.assertNotNull(configBuilderPlugin);
    }
}