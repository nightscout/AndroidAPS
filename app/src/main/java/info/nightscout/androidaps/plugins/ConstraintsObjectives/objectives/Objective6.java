package info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;

public class Objective6 extends Objective {

    public Objective6() {
        super(5, R.string.objectives_5_objective, R.string.objectives_5_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(7L * 24L * 60L * 60L * 1000L));
    }
}
