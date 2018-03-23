package info.nightscout.androidaps.plugins.PumpDanaRS;

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
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

import static org.mockito.Mockito.when;

/**
 * Created by mike on 23.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, ToastUtils.class, Context.class, SP.class})
public class DanaRSPluginTest {

    DanaRSPlugin danaRSPlugin;

    @Test
    public void basalRateShouldBeLimited() throws Exception {
        danaRSPlugin.setPluginEnabled(PluginBase.PUMP, true);
        danaRSPlugin.setPluginEnabled(PluginBase.PUMP, true);
        DanaRPump.getInstance().maxBasal = 0.8d;

        Constraint<Double> c = new Constraint<>(Constants.REALLYHIGHBASALRATE);
        danaRSPlugin.applyBasalConstraints(c, AAPSMocker.getValidProfile());
        Assert.assertEquals(0.8d, c.value());
        Assert.assertEquals("DanaRS: Limiting basal rate to 0.80 U/h because of pump limit", c.getReasons());
        Assert.assertEquals("DanaRS: Limiting basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons());
    }

    @Test
    public void percentBasalRateShouldBeLimited() throws Exception {
        danaRSPlugin.setPluginEnabled(PluginBase.PUMP, true);
        danaRSPlugin.setPluginEnabled(PluginBase.PUMP, true);
        DanaRPump.getInstance().maxBasal = 0.8d;

        Constraint<Integer> c = new Constraint<>(Constants.REALLYHIGHPERCENTBASALRATE);
        danaRSPlugin.applyBasalPercentConstraints(c, AAPSMocker.getValidProfile());
        Assert.assertEquals((Integer) 200, c.value());
        Assert.assertEquals("DanaRS: Limiting percent rate to 200% because of pump limit", c.getReasons());
        Assert.assertEquals("DanaRS: Limiting percent rate to 200% because of pump limit", c.getMostLimitedReasons());
    }

    @Before
    public void prepareMocks() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockBus();
        AAPSMocker.mockStrings();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();

        when(SP.getString(R.string.key_danars_address, "")).thenReturn("");

        danaRSPlugin = DanaRSPlugin.getPlugin();
    }
}
