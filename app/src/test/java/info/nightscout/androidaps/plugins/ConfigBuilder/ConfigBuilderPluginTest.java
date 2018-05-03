package info.nightscout.androidaps.plugins.ConfigBuilder;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PluginType;

import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
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
        Assert.assertEquals(true, ((AAPSMocker.MockedBus) MainApp.bus()).registered);
    }

    @Test
    public void onStopTest() {
        ConfigBuilderPlugin configBuilderPlugin = ConfigBuilderPlugin.getPlugin();
        configBuilderPlugin.setPluginEnabled(PluginType.GENERAL, true);
        configBuilderPlugin.setPluginEnabled(PluginType.GENERAL, false);
        Assert.assertEquals(false, ((AAPSMocker.MockedBus) MainApp.bus()).registered);
    }

    @Before
    public void prepareMock() throws Exception {
        MainApp mainApp = mock(MainApp.class);
        PowerMockito.mockStatic(MainApp.class);
        AAPSMocker.prepareMockedBus();
    }


}