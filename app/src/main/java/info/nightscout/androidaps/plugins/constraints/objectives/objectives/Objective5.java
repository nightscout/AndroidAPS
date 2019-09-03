package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin;
import info.nightscout.androidaps.utils.T;

public class Objective5 extends Objective {

    public Objective5() {
        super("maxiobzero", R.string.objectives_maxiobzero_objective, R.string.objectives_maxiobzero_gate);
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
