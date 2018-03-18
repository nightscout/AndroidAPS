package info.nightscout.androidaps.plugins.PumpCombo;

import android.content.Context;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpCombo.ruffyscripter.history.Bolus;
import info.nightscout.utils.ToastUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, ConfigBuilderPlugin.class, ToastUtils.class, Context.class})
public class ComboPluginTest {

    @Before
    public void prepareMocks() throws Exception {
        ConfigBuilderPlugin configBuilderPlugin = mock(ConfigBuilderPlugin.class);
        PowerMockito.mockStatic(ConfigBuilderPlugin.class);

        PowerMockito.mockStatic(MainApp.class);
        MainApp mainApp = mock(MainApp.class);
        when(MainApp.getConfigBuilder()).thenReturn(configBuilderPlugin);
        when(MainApp.instance()).thenReturn(mainApp);
        Bus bus = new Bus(ThreadEnforcer.ANY);
        when(MainApp.bus()).thenReturn(bus);
        when(MainApp.gs(0)).thenReturn("");
    }

    @Test
    public void calculateFakePumpTimestamp() throws Exception {
        ComboPlugin plugin = ComboPlugin.getPlugin();
        long now = System.currentTimeMillis();
        long pumpTimestamp = now - now % 1000;
        // same timestamp, different bolus leads to different fake timestamp
        Assert.assertNotEquals(
                plugin.calculateFakeBolusDate(new Bolus(pumpTimestamp, 0.1, true)),
                plugin.calculateFakeBolusDate(new Bolus(pumpTimestamp, 0.3, true))
        );
        // different timestamp, same bolus leads to different fake timestamp
        Assert.assertNotEquals(
                plugin.calculateFakeBolusDate(new Bolus(pumpTimestamp, 0.3, true)),
                plugin.calculateFakeBolusDate(new Bolus(pumpTimestamp + 60 * 1000, 0.3, true))
        );
        // generated timestamp has second-precision
        Bolus bolus = new Bolus(pumpTimestamp, 0.2, true);
        long calculatedTimestamp = plugin.calculateFakeBolusDate(bolus);
        assertEquals(calculatedTimestamp, calculatedTimestamp - calculatedTimestamp % 1000);
    }
}