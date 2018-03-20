package info.nightscout.androidaps.interfaces;

import android.content.Context;

import com.squareup.otto.Bus;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.androidaps.plugins.PumpCombo.ComboPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpInsight.InsightPumpPlugin;
import info.nightscout.androidaps.plugins.PumpInsight.connector.StatusTaskRunner;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.SP;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by mike on 18.03.2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, FabricPrivacy.class, SP.class, Context.class})
public class ConstraintsCheckerTest {

    PumpInterface pump = new VirtualPumpPlugin();
    ConstraintChecker constraintChecker;

    ConfigBuilderPlugin configBuilderPlugin = mock(ConfigBuilderPlugin.class);
    MainApp mainApp = mock(MainApp.class);
    MockedBus bus = new MockedBus();

    String validProfile = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}";
    Profile profile = new Profile(new JSONObject(validProfile), Constants.MGDL);

    SafetyPlugin safetyPlugin;
    ObjectivesPlugin objectivesPlugin;
    ComboPlugin comboPlugin;
    DanaRPlugin danaRPlugin;
    DanaRSPlugin danaRSPlugin;
    InsightPumpPlugin insightPlugin;

    boolean notificationSent = false;

    public ConstraintsCheckerTest() throws JSONException {
    }

    // isLoopInvokationAllowed tests
    @Test
    public void pumpDescriptionShouldLimitLoopInvokation() throws Exception {
        pump.getPumpDescription().isTempBasalCapable = false;

        Constraint<Boolean> c = constraintChecker.isLoopInvokationAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Pump is not temp basal capable"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjectivesShouldLimitLoopInvokation() throws Exception {
        objectivesPlugin.objectives.get(0).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isLoopInvokationAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Objective 1 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void invalidBasalRateOnComboPumpShouldLimitLoopInvokation() throws Exception {
        comboPlugin.setFragmentEnabled(PluginBase.PUMP, true);
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false);

        Constraint<Boolean> c = constraintChecker.isLoopInvokationAllowed();
        Assert.assertEquals(true, c.getReasons().contains("No valid basal rate read from pump"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    // isClosedLoopAllowed tests
    @Test
    public void disabledEngineeringModeShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("closed");
        when(MainApp.isEngineeringModeOrRelease()).thenReturn(false);

        Constraint<Boolean> c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Running dev version. Closed loop is disabled."));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void setOpenLoopInPreferencesShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("open");

        Constraint<Boolean> c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Closed loop mode disabled in preferences"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective4ShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("closed");
        objectivesPlugin.objectives.get(3).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Objective 4 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    // isAutosensModeEnabled tests
    @Test
    public void notStartedObjective6ShouldLimitAutosensMode() throws Exception {
        objectivesPlugin.objectives.get(5).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isAutosensModeEnabled();
        Assert.assertEquals(true, c.getReasons().contains("Objective 6 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    // isAMAModeEnabled tests
    @Test
    public void notEnabledAMAInPreferencesDisablesAMA() throws Exception {
        when(SP.getBoolean("openapsama_useautosens", false)).thenReturn(false);

        Constraint<Boolean> c = constraintChecker.isAMAModeEnabled();
        Assert.assertEquals(true, c.getReasons().contains("AMA disabled in preferences"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective7ShouldLimitAMAMode() throws Exception {
        objectivesPlugin.objectives.get(6).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isAMAModeEnabled();
        Assert.assertEquals(true, c.getReasons().contains("Objective 7 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    // isSMBModeEnabled tests
    @Test
    public void notEnabledSMBInPreferencesDisablesSMB() throws Exception {
        when(SP.getBoolean(R.string.key_use_smb, false)).thenReturn(false);

        Constraint<Boolean> c = constraintChecker.isSMBModeEnabled();
        Assert.assertEquals(true, c.getReasons().contains("SMB disabled in preferences"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective8ShouldLimitSMBMode() throws Exception {
        objectivesPlugin.objectives.get(7).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isSMBModeEnabled();
        Assert.assertEquals(true, c.getReasons().contains("Objective 8 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    // applyBasalConstraints tests
    @Test
    public void basalRateShouldBeLimited() throws Exception {
        // DanaR, RS
        danaRPlugin.setFragmentEnabled(PluginBase.PUMP, true);
        danaRSPlugin.setFragmentEnabled(PluginBase.PUMP, true);
        DanaRPump.getInstance().maxBasal = 0.8d;

        // Insight
        insightPlugin.setFragmentEnabled(PluginBase.PUMP, true);
        StatusTaskRunner.Result result = new StatusTaskRunner.Result();
        result.maximumBasalAmount = 1.1d;
        insightPlugin.setStatusResult(result);


        // No limit by default
        when(SP.getDouble(R.string.key_openapsma_max_basal, 1d)).thenReturn(1d);
        when(SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d)).thenReturn(4d);
        when(SP.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d)).thenReturn(3d);

        // Negative basal not allowed
        Constraint<Double> d = new Constraint<>(-0.5d);
        constraintChecker.applyBasalConstraints(d, profile);
        Assert.assertEquals(0d, d.value());
        Assert.assertEquals("Limiting basal rate to 0,00 U/h because of basal must be positive value", d.getReasons());

        // Apply all limits
        d = new Constraint<>(Constants.REALLYHIGHBASALRATE);
        constraintChecker.applyBasalConstraints(d, profile);
        Assert.assertEquals(0.8d, d.value());
        Assert.assertEquals("Limiting basal rate to 1,00 U/h because of max basal settings in preferences\n" +
                "Limiting basal rate to 4,00 U/h because of max basal multiplier\n" +
                "Limiting basal rate to 3,00 U/h because of max daily basal multiplier\n" +
                "Limiting basal rate to 0,80 U/h because of pump limit\n" +
                "Limiting basal rate to 0,80 U/h because of pump limit\n" +
                "Limiting basal rate to 1,10 U/h because of pump limit", d.getReasons());

    }

    @Before
    public void prepareMock() throws Exception {
        PowerMockito.mockStatic(ConfigBuilderPlugin.class);

        PowerMockito.mockStatic(MainApp.class);
        when(MainApp.instance()).thenReturn(mainApp);
        when(MainApp.getConfigBuilder()).thenReturn(configBuilderPlugin);
        when(MainApp.getConfigBuilder().getActivePump()).thenReturn(pump);

        constraintChecker = new ConstraintChecker(mainApp);

        PowerMockito.mockStatic(FabricPrivacy.class);

        Context context = mock(Context.class);
        when(MainApp.instance().getApplicationContext()).thenReturn(context);

        when(MainApp.bus()).thenReturn(bus);

        when(MainApp.gs(R.string.pumpisnottempbasalcapable)).thenReturn("Pump is not temp basal capable");
        when(MainApp.gs(R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.");
        when(MainApp.gs(R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences");
        when(MainApp.gs(R.string.objectivenotstarted)).thenReturn("Objective %d not started");
        when(MainApp.gs(R.string.novalidbasalrate)).thenReturn("No valid basal rate read from pump");
        when(MainApp.gs(R.string.amadisabledinpreferences)).thenReturn("AMA disabled in preferences");
        when(MainApp.gs(R.string.smbdisabledinpreferences)).thenReturn("SMB disabled in preferences");
        when(MainApp.gs(R.string.limitingbasalratio)).thenReturn("Limiting basal rate to %.2f U/h because of %s");
        when(MainApp.gs(R.string.pumplimit)).thenReturn("pump limit");
        when(MainApp.gs(R.string.basalmustbepositivevalue)).thenReturn("basal must be positive value");
        when(MainApp.gs(R.string.maxbasalinpreferences)).thenReturn("max basal settings in preferences");
        when(MainApp.gs(R.string.maxbasalmultiplier)).thenReturn("max basal multiplier");
        when(MainApp.gs(R.string.maxdailybasalmultiplier)).thenReturn("max daily basal multiplier");

        PowerMockito.mockStatic(SP.class);
        // RS constructor
        when(SP.getString(R.string.key_danars_address, "")).thenReturn("");

        safetyPlugin = SafetyPlugin.getPlugin();
        objectivesPlugin = ObjectivesPlugin.getPlugin();
        comboPlugin = ComboPlugin.getPlugin();
        danaRPlugin = DanaRPlugin.getPlugin();
        danaRSPlugin = DanaRSPlugin.getPlugin();
        insightPlugin = InsightPumpPlugin.getPlugin();
        ArrayList<PluginBase> constraintsPluginsList = new ArrayList<>();
        constraintsPluginsList.add(safetyPlugin);
        constraintsPluginsList.add(objectivesPlugin);
        constraintsPluginsList.add(comboPlugin);
        constraintsPluginsList.add(danaRPlugin);
        constraintsPluginsList.add(danaRSPlugin);
        constraintsPluginsList.add(insightPlugin);
        when(mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class)).thenReturn(constraintsPluginsList);

    }

    class MockedBus extends Bus {
        @Override
        public void post(Object event) {
            notificationSent = true;
        }
    }

}
