package info.nightscout.androidaps.plugins.pump.combo;

import android.content.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.androidaps.utils.ToastUtils;

import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, ToastUtils.class, Context.class, CommandQueue.class})
public class ComboPluginTest {

    ComboPlugin comboPlugin;

    @Test
    public void invalidBasalRateOnComboPumpShouldLimitLoopInvokation() throws Exception {
        comboPlugin.setPluginEnabled(PluginType.PUMP, true);
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false);

        Constraint<Boolean> c = new Constraint<>(true);
        c = comboPlugin.isLoopInvocationAllowed(c);
        Assert.assertEquals("Combo: No valid basal rate read from pump", c.getReasons());
        Assert.assertEquals(Boolean.FALSE, c.value());
        comboPlugin.setPluginEnabled(PluginType.PUMP, false);
    }

    @Test
    public void calculateFakePumpTimestamp() throws Exception {
        long now = System.currentTimeMillis();
        long pumpTimestamp = now - now % 1000;
        // same timestamp, different bolus leads to different fake timestamp
        Assert.assertNotEquals(
                comboPlugin.calculateFakeBolusDate(new Bolus(pumpTimestamp, 0.1, true)),
                comboPlugin.calculateFakeBolusDate(new Bolus(pumpTimestamp, 0.3, true))
        );
        // different timestamp, same bolus leads to different fake timestamp
        Assert.assertNotEquals(
                comboPlugin.calculateFakeBolusDate(new Bolus(pumpTimestamp, 0.3, true)),
                comboPlugin.calculateFakeBolusDate(new Bolus(pumpTimestamp + 60 * 1000, 0.3, true))
        );
        // generated timestamp has second-precision
        Bolus bolus = new Bolus(pumpTimestamp, 0.2, true);
        long calculatedTimestamp = comboPlugin.calculateFakeBolusDate(bolus);
        assertEquals(calculatedTimestamp, calculatedTimestamp - calculatedTimestamp % 1000);
    }

    @Before
    public void prepareMocks() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockBus();
        AAPSMocker.mockStrings();
        AAPSMocker.mockCommandQueue();

        comboPlugin = ComboPlugin.getPlugin();
    }
}