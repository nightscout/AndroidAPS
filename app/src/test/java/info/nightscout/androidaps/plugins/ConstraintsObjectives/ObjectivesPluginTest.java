package info.nightscout.androidaps.plugins.ConstraintsObjectives;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Date;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.SP;

/**
 * Created by mike on 23.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class})
public class ObjectivesPluginTest {

    ObjectivesPlugin objectivesPlugin;

    @Test
    public void notStartedObjectivesShouldLimitLoopInvokation() throws Exception {
        objectivesPlugin.objectives.get(0).setStarted(new Date(0));

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isLoopInvokationAllowed(c);
        Assert.assertEquals("Objectives: Objective 1 not started", c.getReasons());
        Assert.assertEquals(Boolean.FALSE, c.value());
        objectivesPlugin.objectives.get(0).setStarted(new Date());
    }

    @Test
    public void notStartedObjective4ShouldLimitClosedLoop() throws Exception {
        objectivesPlugin.objectives.get(3).setStarted(new Date(0));

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isClosedLoopAllowed(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 4 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective6ShouldLimitAutosensMode() throws Exception {
        objectivesPlugin.objectives.get(5).setStarted(new Date(0));

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isAutosensModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 6 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective7ShouldLimitAMAMode() throws Exception {
        objectivesPlugin.objectives.get(6).setStarted(new Date(0));

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isAMAModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 7 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective8ShouldLimitSMBMode() throws Exception {
        objectivesPlugin.objectives.get(7).setStarted(new Date(0));

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isSMBModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 8 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockBus();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();

        objectivesPlugin = ObjectivesPlugin.getPlugin();
    }
}
