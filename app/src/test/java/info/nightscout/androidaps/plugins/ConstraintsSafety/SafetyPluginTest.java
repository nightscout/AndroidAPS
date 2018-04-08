package info.nightscout.androidaps.plugins.ConstraintsSafety;

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
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.Source.SourceGlimpPlugin;
import info.nightscout.utils.SP;

import static org.mockito.Mockito.when;

/**
 * Created by mike on 23.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class, Context.class})
public class SafetyPluginTest {

    VirtualPumpPlugin pump = new VirtualPumpPlugin();
    SafetyPlugin safetyPlugin;

    @Test
    public void pumpDescriptionShouldLimitLoopInvokation() throws Exception {
        pump.getPumpDescription().isTempBasalCapable = false;

        Constraint<Boolean> c = new Constraint<>(true);
        c = safetyPlugin.isLoopInvokationAllowed(c);
        Assert.assertEquals("Safety: Pump is not temp basal capable", c.getReasons());
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void disabledEngineeringModeShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("closed");
        when(MainApp.isEngineeringModeOrRelease()).thenReturn(false);

        Constraint<Boolean> c = new Constraint<>(true);
        c = safetyPlugin.isClosedLoopAllowed(c);
        Assert.assertEquals(true, c.getReasons().contains("Running dev version. Closed loop is disabled."));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void setOpenLoopInPreferencesShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("open");

        Constraint<Boolean> c = new Constraint<>(true);
        c = safetyPlugin.isClosedLoopAllowed(c);
        Assert.assertEquals(true, c.getReasons().contains("Closed loop mode disabled in preferences"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notEnabledSMBInPreferencesDisablesSMB() throws Exception {
        when(SP.getBoolean(R.string.key_use_smb, false)).thenReturn(false);
        when(MainApp.getConstraintChecker().isClosedLoopAllowed()).thenReturn(new Constraint<>(true));

        Constraint<Boolean> c = new Constraint<>(true);
        c = safetyPlugin.isSMBModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("SMB disabled in preferences"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void openLoopPreventsSMB() throws Exception {
        when(SP.getBoolean(R.string.key_use_smb, false)).thenReturn(true);
        when(MainApp.getConstraintChecker().isClosedLoopAllowed()).thenReturn(new Constraint<>(false));

        Constraint<Boolean> c = new Constraint<>(true);
        c = safetyPlugin.isSMBModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("SMB not allowed in open loop mode"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void bgsourceShouldPreventSMBAlways() throws Exception {
        when(MainApp.getConfigBuilder().getActiveBgSource()).thenReturn(SourceGlimpPlugin.getPlugin());

        Constraint<Boolean> c = new Constraint<>(true);
        c = safetyPlugin.isAdvancedFilteringEnabled(c);
        Assert.assertEquals("Safety: SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering", c.getReasons());
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void basalRateShouldBeLimited() throws Exception {
        when(SP.getDouble(R.string.key_openapsma_max_basal, 1d)).thenReturn(1d);
        when(SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d)).thenReturn(4d);
        when(SP.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        Constraint<Double> c = new Constraint<>(Constants.REALLYHIGHBASALRATE);
        safetyPlugin.applyBasalConstraints(c, AAPSMocker.getValidProfile());
        Assert.assertEquals(1d, c.value());
        Assert.assertEquals("Safety: Limiting basal rate to 1.00 U/h because of max value in preferences\n" +
                "Safety: Limiting basal rate to 4.00 U/h because of max basal multiplier\n" +
                "Safety: Limiting basal rate to 3.00 U/h because of max daily basal multiplier\n" +
                "Safety: Limiting basal rate to 2.00 U/h because of hard limit", c.getReasons());
        Assert.assertEquals("Safety: Limiting basal rate to 1.00 U/h because of max value in preferences", c.getMostLimitedReasons());

    }

    @Test
    public void doNotAllowNegativeBasalRate() throws Exception {
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        Constraint<Double> d = new Constraint<>(-0.5d);
        safetyPlugin.applyBasalConstraints(d, AAPSMocker.getValidProfile());
        Assert.assertEquals(0d, d.value());
        Assert.assertEquals("Safety: Limiting basal rate to 0.00 U/h because of it must be positive value", d.getReasons());
    }

    @Test
    public void percentBasalRateShouldBeLimited() throws Exception {
        // No limit by default
        when(SP.getDouble(R.string.key_openapsma_max_basal, 1d)).thenReturn(1d);
        when(SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d)).thenReturn(4d);
        when(SP.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("child");


        Constraint<Integer> i = new Constraint<>(Constants.REALLYHIGHPERCENTBASALRATE);
        safetyPlugin.applyBasalPercentConstraints(i, AAPSMocker.getValidProfile());
        Assert.assertEquals((Integer) 100, i.value());
        Assert.assertEquals("Safety: Percent rate 1111111% recalculated to 11111.11 U/h with current basal 1.00 U/h\n" +
                "Safety: Limiting basal rate to 1.00 U/h because of max value in preferences\n" +
                "Safety: Limiting basal rate to 4.00 U/h because of max basal multiplier\n" +
                "Safety: Limiting basal rate to 3.00 U/h because of max daily basal multiplier\n" +
                "Safety: Limiting basal rate to 2.00 U/h because of hard limit\n" +
                "Safety: Limiting percent rate to 100% because of pump limit", i.getReasons());
        Assert.assertEquals("Safety: Limiting percent rate to 100% because of pump limit", i.getMostLimitedReasons());
    }

    @Test
    public void doNotAllowNegativePercentBasalRate() throws Exception {
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        Constraint<Integer> i = new Constraint<>(-22);
        safetyPlugin.applyBasalPercentConstraints(i, AAPSMocker.getValidProfile());
        Assert.assertEquals((Integer) 0, i.value());
        Assert.assertEquals("Safety: Percent rate -22% recalculated to -0.22 U/h with current basal 1.00 U/h\n" +
                "Safety: Limiting basal rate to 0.00 U/h because of it must be positive value\n" +
                "Safety: Limiting percent rate to 0% because of pump limit", i.getReasons());
        Assert.assertEquals("Safety: Limiting percent rate to 0% because of pump limit", i.getMostLimitedReasons());
    }

    @Test
    public void bolusAmountShouldBeLimited() throws Exception {
        when(SP.getDouble(R.string.key_treatmentssafety_maxbolus, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        Constraint<Double> d = new Constraint<>(Constants.REALLYHIGHBOLUS);
        d = safetyPlugin.applyBolusConstraints(d);
        Assert.assertEquals(3d, d.value());
        Assert.assertEquals("Safety: Limiting bolus to 3.0 U because of max value in preferences\n" +
                "Safety: Limiting bolus to 5.0 U because of hard limit", d.getReasons());
        Assert.assertEquals("Safety: Limiting bolus to 3.0 U because of max value in preferences", d.getMostLimitedReasons());
    }

    @Test
    public void doNotAllowNegativeBolusAmount() throws Exception {
        when(SP.getDouble(R.string.key_treatmentssafety_maxbolus, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        Constraint<Double> d = new Constraint<>(-22d);
        d = safetyPlugin.applyBolusConstraints(d);
        Assert.assertEquals(0d, d.value());
        Assert.assertEquals("Safety: Limiting bolus to 0.0 U because of it must be positive value", d.getReasons());
        Assert.assertEquals("Safety: Limiting bolus to 0.0 U because of it must be positive value", d.getMostLimitedReasons());
    }

    @Test
    public void carbsAmountShouldBeLimited() throws Exception {
        // No limit by default
        when(SP.getInt(R.string.key_treatmentssafety_maxcarbs, 48)).thenReturn(48);

        // Negative carbs not allowed
        Constraint<Integer> i = new Constraint<>(-22);
        safetyPlugin.applyCarbsConstraints(i);
        Assert.assertEquals((Integer) 0, i.value());
        Assert.assertEquals("Safety: Limiting carbs to 0 g because of it must be positive value", i.getReasons());

        // Apply all limits
        i = safetyPlugin.applyCarbsConstraints(new Constraint<>(Constants.REALLYHIGHCARBS));
        Assert.assertEquals((Integer) 48, i.value());
        Assert.assertEquals("Safety: Limiting carbs to 48 g because of max value in preferences", i.getReasons());
    }

    @Test
    public void iobShouldBeLimited() throws Exception {
        when(SP.getDouble(R.string.key_openapsma_max_iob, 1.5d)).thenReturn(1.5d);
        when(SP.getString(R.string.key_age, "")).thenReturn("teenage");
        OpenAPSMAPlugin.getPlugin().setPluginEnabled(PluginType.APS, true);
        OpenAPSAMAPlugin.getPlugin().setPluginEnabled(PluginType.APS, true);
        //OpenAPSSMBPlugin.getPlugin().setPluginEnabled(PluginType.APS, true);

        // Apply all limits
        Constraint<Double> d = new Constraint<>(Constants.REALLYHIGHIOB);
        d = safetyPlugin.applyMaxIOBConstraints(d);
        Assert.assertEquals(1.5d, d.value());
        Assert.assertEquals("Safety: Limiting IOB to 1.5 U because of max value in preferences\n" +
                "Safety: Limiting IOB to 7.0 U because of hard limit\n" +
                "Safety: Limiting IOB to 7.0 U because of hard limit", d.getReasons());
        Assert.assertEquals("Safety: Limiting IOB to 1.5 U because of max value in preferences", d.getMostLimitedReasons());
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockConstraintsChecker();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();
        AAPSMocker.mockBus();


        when(MainApp.getConfigBuilder().getActivePump()).thenReturn(pump);

        safetyPlugin = SafetyPlugin.getPlugin();
    }
}
