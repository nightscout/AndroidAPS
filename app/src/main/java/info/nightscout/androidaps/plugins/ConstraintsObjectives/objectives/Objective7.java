package info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.utils.T;

public class Objective7 extends Objective {

    public Objective7() {
        super(6, R.string.objectives_6_objective, 0);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(28).msecs()));
    }
}
