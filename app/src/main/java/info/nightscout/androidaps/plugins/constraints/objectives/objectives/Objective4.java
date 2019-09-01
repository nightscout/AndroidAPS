package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin;
import info.nightscout.androidaps.utils.T;

public class Objective4 extends Objective {

    public Objective4() {
        super(ObjectivesPlugin.MAXIOB_ZERO_CL_OBJECTIVE, R.string.objectives_maxiobzero_objective, R.string.objectives_maxiobzero_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(5).msecs()));
        tasks.add(new Task(R.string.closedmodeenabled) {
            @Override
            public boolean isCompleted() {
                Constraint<Boolean> closedLoopEnabled = new Constraint<>(true);
                SafetyPlugin.getPlugin().isClosedLoopAllowed(closedLoopEnabled);
                return closedLoopEnabled.value();
            }
        });
    }

    @Override
    public boolean isRevertable() {
        return true;
    }
}
