package info.nightscout.androidaps.plugins.PumpInsight;

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
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpInsight.connector.StatusTaskRunner;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 23.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, ToastUtils.class, Context.class})
public class InsightPluginTest {

    InsightPlugin insightPlugin;

    @Test
    public void basalRateShouldBeLimited() throws Exception {
        insightPlugin.setPluginEnabled(PluginType.PUMP, true);
        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
        result.maximumBasalAmount = 1.1d;
        insightPlugin.setStatusResult(result);

        Constraint<Double> c = new Constraint<>(Constants.REALLYHIGHBASALRATE);
        insightPlugin.applyBasalConstraints(c, AAPSMocker.getValidProfile());
        Assert.assertEquals(1.1d, c.value());
        Assert.assertEquals("Insight: Limiting basal rate to 1.10 U/h because of pump limit", c.getReasons());
        Assert.assertEquals("Insight: Limiting basal rate to 1.10 U/h because of pump limit", c.getMostLimitedReasons());
    }

    @Before
    public void prepareMocks() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockBus();
        AAPSMocker.mockStrings();
        AAPSMocker.mockCommandQueue();

        insightPlugin = InsightPlugin.getPlugin();
    }

}
