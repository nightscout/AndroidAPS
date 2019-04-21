package info.nightscout.androidaps.interfaces;

import android.content.Context;

import com.squareup.otto.Bus;

import junit.framework.Assert;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.pump.combo.ComboPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.source.SourceGlimpPlugin;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.Mockito.when;

/**
 * Created by mike on 18.03.2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, FabricPrivacy.class, SP.class, Context.class, OpenAPSMAPlugin.class, OpenAPSAMAPlugin.class, OpenAPSSMBPlugin.class})
public class ConstraintsCheckerTest {

    VirtualPumpPlugin pump = new VirtualPumpPlugin();
    ConstraintChecker constraintChecker;

    SafetyPlugin safetyPlugin;
    ObjectivesPlugin objectivesPlugin;
    ComboPlugin comboPlugin;
    DanaRPlugin danaRPlugin;
    DanaRSPlugin danaRSPlugin;
    LocalInsightPlugin insightPlugin;
    OpenAPSSMBPlugin openAPSSMBPlugin;

    boolean notificationSent = false;

    public ConstraintsCheckerTest() throws JSONException {
    }

    @Test
    public void isLoopInvokationAllowedTest() throws Exception {
        comboPlugin.setPluginEnabled(PluginType.PUMP, true);
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false);

        Constraint<Boolean> c = constraintChecker.isLoopInvokationAllowed();
        Assert.assertEquals(true, c.getReasonList().size() == 2); // Combo & Objectives
        Assert.assertEquals(true, c.getMostLimitedReasonList().size() == 2); // Combo & Objectives
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void isClosedLoopAllowedTest() throws Exception {
        when(SP.getString(R.string.key_aps_mode, "open")).thenReturn("closed");
        objectivesPlugin.objectives.get(3).setStartedOn(null);

        Constraint<Boolean> c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasonList().size() == 2); // Safety & Objectives
        Assert.assertEquals(true, c.getMostLimitedReasonList().size() == 2); // Safety & Objectives
        Assert.assertEquals(Boolean.FALSE, c.value());

        when(SP.getString(R.string.key_aps_mode, "open")).thenReturn("open");
        c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasonList().size() == 3); // 2x Safety & Objectives
        Assert.assertEquals(true, c.getMostLimitedReasonList().size() == 3); // 2x Safety & Objectives
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void isAutosensModeEnabledTest() throws Exception {
        objectivesPlugin.objectives.get(5).setStartedOn(null);
        when(SP.getBoolean(R.string.key_openapsama_useautosens, false)).thenReturn(false);

        Constraint<Boolean> c = constraintChecker.isAutosensModeEnabled();
        Assert.assertEquals(true, c.getReasonList().size() == 2); // Safety & Objectives
        Assert.assertEquals(true, c.getMostLimitedReasonList().size() == 2); // Safety & Objectives
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void isAMAModeEnabledTest() throws Exception {
        objectivesPlugin.objectives.get(6).setStartedOn(null);

        Constraint<Boolean> c = constraintChecker.isAMAModeEnabled();
        Assert.assertEquals(true, c.getReasonList().size() == 1); // Objectives
        Assert.assertEquals(true, c.getMostLimitedReasonList().size() == 1); // Objectives
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void isAdvancedFilteringEnabledTest() throws Exception {
        when(ConfigBuilderPlugin.getPlugin().getActiveBgSource()).thenReturn(SourceGlimpPlugin.getPlugin());

        Constraint<Boolean> c = constraintChecker.isAdvancedFilteringEnabled();
        Assert.assertEquals(true, c.getReasonList().size() == 1); // Safety
        Assert.assertEquals(true, c.getMostLimitedReasonList().size() == 1); // Safety
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void isSuperBolusEnabledTest() throws Exception {
        OpenAPSSMBPlugin.getPlugin().setPluginEnabled(PluginType.APS, true);

        Constraint<Boolean> c = constraintChecker.isSuperBolusEnabled();
        Assert.assertEquals(Boolean.FALSE, c.value()); // SMB should limit
    }

    @Test
    public void isSMBModeEnabledTest() throws Exception {
        objectivesPlugin.objectives.get(7).setStartedOn(null);
        when(SP.getBoolean(R.string.key_use_smb, false)).thenReturn(false);
        when(MainApp.getConstraintChecker().isClosedLoopAllowed()).thenReturn(new Constraint<>(true));

        Constraint<Boolean> c = constraintChecker.isSMBModeEnabled();
        Assert.assertEquals(true, c.getReasonList().size() == 2); // Safety & Objectives
        Assert.assertEquals(true, c.getMostLimitedReasonList().size() == 2); // Safety & Objectives
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    // applyBasalConstraints tests
    @Test
    public void basalRateShouldBeLimited() throws Exception {
        // DanaR, RS
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true);
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true);
        DanaRPump.getInstance().maxBasal = 0.8d;

        // Insight
//        insightPlugin.setPluginEnabled(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBasalAmount = 1.1d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        when(SP.getDouble(R.string.key_openapsma_max_basal, 1d)).thenReturn(1d);
        when(SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d)).thenReturn(4d);
        when(SP.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        // Apply all limits
        Constraint<Double> d = constraintChecker.getMaxBasalAllowed(AAPSMocker.getValidProfile());
        Assert.assertEquals(0.8d, d.value());
        Assert.assertEquals(6, d.getReasonList().size());
        Assert.assertEquals("DanaR: Limiting basal rate to 0.80 U/h because of pump limit", d.getMostLimitedReasons());

    }

    @Test
    public void percentBasalRateShouldBeLimited() throws Exception {
        // DanaR, RS
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true);
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true);
        DanaRPump.getInstance().maxBasal = 0.8d;

        // Insight
//        insightPlugin.setPluginEnabled(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBasalAmount = 1.1d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        when(SP.getDouble(R.string.key_openapsma_max_basal, 1d)).thenReturn(1d);
        when(SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d)).thenReturn(4d);
        when(SP.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        // Apply all limits
        Constraint<Integer> i = constraintChecker.getMaxBasalPercentAllowed(AAPSMocker.getValidProfile());
        Assert.assertEquals((Integer) 100, i.value());
        Assert.assertEquals(8, i.getReasonList().size()); // 6x Safety & RS & R
        Assert.assertEquals("Safety: Limiting percent rate to 100% because of pump limit", i.getMostLimitedReasons());

    }

    // applyBolusConstraints tests
    @Test
    public void bolusAmountShouldBeLimited() throws Exception {
        // DanaR, RS
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true);
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true);
        DanaRPump.getInstance().maxBolus = 6d;

        // Insight
//        insightPlugin.setPluginEnabled(PluginType.PUMP, true);
//        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
//        result.maximumBolusAmount = 7d;
//        insightPlugin.setStatusResult(result);

        // No limit by default
        when(SP.getDouble(R.string.key_treatmentssafety_maxbolus, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("child");

        // Apply all limits
        Constraint<Double> d = constraintChecker.getMaxBolusAllowed();
        Assert.assertEquals(3d, d.value());
        Assert.assertEquals(4, d.getReasonList().size()); // 2x Safety & RS & R
        Assert.assertEquals("Safety: Limiting bolus to 3.0 U because of max value in preferences", d.getMostLimitedReasons());

    }

    // applyCarbsConstraints tests
    @Test
    public void carbsAmountShouldBeLimited() throws Exception {
        // No limit by default
        when(SP.getInt(R.string.key_treatmentssafety_maxcarbs, 48)).thenReturn(48);

        // Apply all limits
        Constraint<Integer> i = constraintChecker.getMaxCarbsAllowed();
        Assert.assertEquals((Integer) 48, i.value());
        Assert.assertEquals(true, i.getReasonList().size() == 1);
        Assert.assertEquals("Safety: Limiting carbs to 48 g because of max value in preferences", i.getMostLimitedReasons());
    }

    // applyMaxIOBConstraints tests
    @Test
    public void iobAMAShouldBeLimited() {
        // No limit by default
        when(SP.getDouble(R.string.key_openapsma_max_iob, 1.5d)).thenReturn(1.5d);
        when(SP.getString(R.string.key_age, "")).thenReturn("teenage");
        OpenAPSAMAPlugin.getPlugin().setPluginEnabled(PluginType.APS, true);
        OpenAPSMAPlugin.getPlugin().setPluginEnabled(PluginType.APS, false);
        OpenAPSSMBPlugin.getPlugin().setPluginEnabled(PluginType.APS, false);

        // Apply all limits
        Constraint<Double> d = constraintChecker.getMaxIOBAllowed();
        Assert.assertEquals(1.5d, d.value());
        Assert.assertEquals(d.getReasonList().toString(),2, d.getReasonList().size());
        Assert.assertEquals("Safety: Limiting IOB to 1.5 U because of max value in preferences", d.getMostLimitedReasons());

    }

    @Test
    public void iobSMBShouldBeLimited() {
        // No limit by default
        when(SP.getDouble(R.string.key_openapssmb_max_iob, 3d)).thenReturn(3d);
        when(SP.getString(R.string.key_age, "")).thenReturn("teenage");
        OpenAPSSMBPlugin.getPlugin().setPluginEnabled(PluginType.APS, true);
        OpenAPSAMAPlugin.getPlugin().setPluginEnabled(PluginType.APS, false);
        OpenAPSMAPlugin.getPlugin().setPluginEnabled(PluginType.APS, false);

        // Apply all limits
        Constraint<Double> d = constraintChecker.getMaxIOBAllowed();
        Assert.assertEquals(3d, d.value());
        Assert.assertEquals(d.getReasonList().toString(), 2, d.getReasonList().size());
        Assert.assertEquals("Safety: Limiting IOB to 3.0 U because of max value in preferences", d.getMostLimitedReasons());

    }

    @Before
    public void prepareMock() throws Exception {

        PowerMockito.mockStatic(FabricPrivacy.class);

        MainApp mainApp = AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockConstraintsChecker();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockBus();
        AAPSMocker.mockStrings();
        AAPSMocker.mockSP();
        AAPSMocker.mockCommandQueue();

        // RS constructor
        when(SP.getString(R.string.key_danars_address, "")).thenReturn("");

        //SafetyPlugin
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(pump);

        constraintChecker = new ConstraintChecker();

        safetyPlugin = SafetyPlugin.getPlugin();
        objectivesPlugin = ObjectivesPlugin.getPlugin();
        comboPlugin = ComboPlugin.getPlugin();
        danaRPlugin = DanaRPlugin.getPlugin();
        danaRSPlugin = DanaRSPlugin.getPlugin();
        insightPlugin = LocalInsightPlugin.getPlugin();
        openAPSSMBPlugin = OpenAPSSMBPlugin.getPlugin();
        ArrayList<PluginBase> constraintsPluginsList = new ArrayList<>();
        constraintsPluginsList.add(safetyPlugin);
        constraintsPluginsList.add(objectivesPlugin);
        constraintsPluginsList.add(comboPlugin);
        constraintsPluginsList.add(danaRPlugin);
        constraintsPluginsList.add(danaRSPlugin);
        constraintsPluginsList.add(insightPlugin);
        constraintsPluginsList.add(openAPSSMBPlugin);
        when(mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class)).thenReturn(constraintsPluginsList);

    }

    class MockedBus extends Bus {
        @Override
        public void post(Object event) {
            notificationSent = true;
        }
    }

}
