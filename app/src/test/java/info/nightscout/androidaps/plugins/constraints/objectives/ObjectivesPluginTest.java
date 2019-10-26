package info.nightscout.androidaps.plugins.constraints.objectives;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 23.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, SP.class})
public class ObjectivesPluginTest {

    ObjectivesPlugin objectivesPlugin;

    @Test
    public void notStartedObjectivesShouldLimitLoopInvocation() {
        objectivesPlugin.getObjectives().get(ObjectivesPlugin.INSTANCE.getFIRST_OBJECTIVE()).setStartedOn(0);

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isLoopInvocationAllowed(c);
        Assert.assertEquals("Objectives: Objective 1 not started", c.getReasons());
        Assert.assertEquals(Boolean.FALSE, c.value());
        objectivesPlugin.getObjectives().get(ObjectivesPlugin.INSTANCE.getFIRST_OBJECTIVE()).setStartedOn(DateUtil.now());
    }

    @Test
    public void notStartedObjective6ShouldLimitClosedLoop() {
        objectivesPlugin.getObjectives().get(ObjectivesPlugin.INSTANCE.getMAXIOB_ZERO_CL_OBJECTIVE()).setStartedOn(0);

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isClosedLoopAllowed(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 6 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective8ShouldLimitAutosensMode() {
        objectivesPlugin.getObjectives().get(ObjectivesPlugin.INSTANCE.getAUTOSENS_OBJECTIVE()).setStartedOn(0);

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isAutosensModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 8 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective9ShouldLimitAMAMode() {
        objectivesPlugin.getObjectives().get(ObjectivesPlugin.INSTANCE.getAMA_OBJECTIVE()).setStartedOn(0);

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isAMAModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 9 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Test
    public void notStartedObjective10ShouldLimitSMBMode() {
        objectivesPlugin.getObjectives().get(ObjectivesPlugin.INSTANCE.getSMB_OBJECTIVE()).setStartedOn(0);

        Constraint<Boolean> c = new Constraint<>(true);
        c = objectivesPlugin.isSMBModeEnabled(c);
        Assert.assertEquals(true, c.getReasons().contains("Objective 10 not started"));
        Assert.assertEquals(Boolean.FALSE, c.value());
    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockSP();
        AAPSMocker.mockStrings();

        objectivesPlugin = ObjectivesPlugin.INSTANCE;
    }
}
