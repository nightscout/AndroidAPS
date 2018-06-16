package info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;

public class Objective8 extends Objective {

    public Objective8() {
        super(7, R.string.objectives_7_objective, 0);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(28L * 24L * 60L * 60L * 1000L));
    }
}
