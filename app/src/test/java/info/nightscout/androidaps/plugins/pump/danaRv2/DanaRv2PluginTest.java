package info.nightscout.androidaps.plugins.pump.danaRv2;

import android.content.Context;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;

import static org.junit.Assert.assertEquals;

/**
 * Created by Rumen on 01.08.2018
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, ToastUtils.class, Context.class, SP.class})
public class DanaRv2PluginTest {
    DanaRv2Plugin danaRv2Plugin;

    @Test
    public void getPlugin() {
        danaRv2Plugin.getPlugin();
    }

    @Test
    public void basalRateShouldBeLimited() throws Exception {
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true);
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true);
        DanaRPump.getInstance().maxBasal = 0.8d;

        Constraint<Double> c = new Constraint<>(Constants.REALLYHIGHBASALRATE);
        danaRv2Plugin.applyBasalConstraints(c, AAPSMocker.getValidProfile());
        Assert.assertEquals(0.8d, c.value());
        Assert.assertEquals("DanaRv2: Limiting basal rate to 0.80 U/h because of pump limit", c.getReasons());
        Assert.assertEquals("DanaRv2: Limiting basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons());
    }

    @Test
    public void percentBasalRateShouldBeLimited() throws Exception {
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true);
        danaRv2Plugin.setPluginEnabled(PluginType.PUMP, true);
        DanaRPump.getInstance().maxBasal = 0.8d;

        Constraint<Integer> c = new Constraint<>(Constants.REALLYHIGHPERCENTBASALRATE);
        danaRv2Plugin.applyBasalPercentConstraints(c, AAPSMocker.getValidProfile());
        Assert.assertEquals((Integer) 200, c.value());
        Assert.assertEquals("DanaRv2: Limiting percent rate to 200% because of pump limit", c.getReasons());
        Assert.assertEquals("DanaRv2: Limiting percent rate to 200% because of pump limit", c.getMostLimitedReasons());
    }

    @Before
    public void prepareMocks() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockStrings();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockCommandQueue();

//        when(SP.getString(R.string.key_danars_address, "")).thenReturn("");

        danaRv2Plugin = DanaRv2Plugin.getPlugin();
    }
}
