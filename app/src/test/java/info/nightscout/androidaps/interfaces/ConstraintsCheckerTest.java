package info.nightscout.androidaps.interfaces;

import com.squareup.otto.Bus;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.androidaps.plugins.PumpCombo.ComboPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.SP;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by mike on 18.03.2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, FabricPrivacy.class, SP.class})
public class ConstraintsCheckerTest {

    PumpInterface pump = new VirtualPumpPlugin();
    ConstraintChecker constraintChecker;

    ConfigBuilderPlugin configBuilderPlugin = mock(ConfigBuilderPlugin.class);
    MainApp mainApp = mock(MainApp.class);
    MockedBus bus = new MockedBus();

    SafetyPlugin safetyPlugin;
    ObjectivesPlugin objectivesPlugin;
    ComboPlugin comboPlugin;

    boolean notificationSent = false;

    // isLoopInvokationAllowed tests
    @Test
    public void pumpDescriptionShouldLimitLoopInvokation() throws Exception {
        pump.getPumpDescription().isTempBasalCapable = false;

        Constraint<Boolean> c = constraintChecker.isLoopInvokationAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Pump is not temp basal capable"));
        Assert.assertEquals(Boolean.FALSE, c.get());
    }

    @Test
    public void notStartedObjectivesShouldLimitLoopInvokation() throws Exception {
        objectivesPlugin.objectives.get(0).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isLoopInvokationAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Objective 1 not started"));
        Assert.assertEquals(Boolean.FALSE, c.get());
    }

    @Test
    public void invalidBasalRateOnComboPumpShouldLimitLoopInvokation() throws Exception {
        comboPlugin.setFragmentEnabled(PluginBase.PUMP, true);
        comboPlugin.setValidBasalRateProfileSelectedOnPump(false);

        Constraint<Boolean> c = constraintChecker.isLoopInvokationAllowed();
        Assert.assertEquals(true, c.getReasons().contains("No valid basal rate read from pump"));
        Assert.assertEquals(Boolean.FALSE, c.get());
    }

    // isClosedLoopAllowed tests
    @Test
    public void disabledEngineeringModeShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("closed");
        when(MainApp.isEngineeringModeOrRelease()).thenReturn(false);

        Constraint<Boolean> c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Running dev version. Closed loop is disabled."));
        Assert.assertEquals(Boolean.FALSE, c.get());
    }

    @Test
    public void setOpenLoopInPreferencesShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("open");

        Constraint<Boolean> c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Closed loop mode disabled in preferences"));
        Assert.assertEquals(Boolean.FALSE, c.get());
    }

    @Test
    public void notStartedObjective4ShouldLimitClosedLoop() throws Exception {
        when(SP.getString("aps_mode", "open")).thenReturn("closed");
        objectivesPlugin.objectives.get(3).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isClosedLoopAllowed();
        Assert.assertEquals(true, c.getReasons().contains("Objective 4 not started"));
        Assert.assertEquals(Boolean.FALSE, c.get());
    }

    // isAutosensModeEnabled tests
    @Test
    public void notStartedObjective6ShouldLimitAutosensMode() throws Exception {
        objectivesPlugin.objectives.get(5).setStarted(new Date(0));

        Constraint<Boolean> c = constraintChecker.isAutosensModeEnabled();
        Assert.assertEquals(true, c.getReasons().contains("Objective 6 not started"));
        Assert.assertEquals(Boolean.FALSE, c.get());
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

        when(MainApp.bus()).thenReturn(bus);

        when(MainApp.gs(R.string.pumpisnottempbasalcapable)).thenReturn("Pump is not temp basal capable");
        when(MainApp.gs(R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.");
        when(MainApp.gs(R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences");
        when(MainApp.gs(R.string.objectivenotstarted)).thenReturn("Objective %d not started");
        when(MainApp.gs(R.string.novalidbasalrate)).thenReturn("No valid basal rate read from pump");

        safetyPlugin = SafetyPlugin.getPlugin();
        objectivesPlugin = ObjectivesPlugin.getPlugin();
        comboPlugin = ComboPlugin.getPlugin();
        ArrayList<PluginBase> constraintsPluginsList = new ArrayList<>();
        constraintsPluginsList.add(safetyPlugin);
        constraintsPluginsList.add(objectivesPlugin);
        constraintsPluginsList.add(comboPlugin);
        when(mainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class)).thenReturn(constraintsPluginsList);

        PowerMockito.mockStatic(SP.class);
    }

    class MockedBus extends Bus {
        @Override
        public void post(Object event) {
            notificationSent = true;
        }
    }

}
