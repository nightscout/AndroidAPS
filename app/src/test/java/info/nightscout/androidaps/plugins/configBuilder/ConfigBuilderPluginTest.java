package info.nightscout.androidaps.plugins.configBuilder;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.utils.SP;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, SP.class})
public class ConfigBuilderPluginTest {

    @Test
    public void getPluginTest() {
        ConfigBuilderPlugin configBuilderPlugin = ConfigBuilderPlugin.getPlugin();
        Assert.assertNotNull(configBuilderPlugin);
    }

    @Test
    public void onStartTest() {
        ConfigBuilderPlugin configBuilderPlugin = ConfigBuilderPlugin.getPlugin();
        configBuilderPlugin.setPluginEnabled(PluginType.GENERAL, true);
    }

    @Test
    public void onStopTest() {
        ConfigBuilderPlugin configBuilderPlugin = ConfigBuilderPlugin.getPlugin();
        configBuilderPlugin.setPluginEnabled(PluginType.GENERAL, true);
        configBuilderPlugin.setPluginEnabled(PluginType.GENERAL, false);
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
    }


}