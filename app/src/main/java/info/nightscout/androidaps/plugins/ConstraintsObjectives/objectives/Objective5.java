package info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class Objective5 extends Objective {

    public Objective5() {
        super(4, R.string.objectives_4_objective, R.string.objectives_4_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(24L * 60L * 60L * 1000L));
        tasks.add(new Task(R.string.maxiobset) {
            @Override
            public boolean isCompleted() {
                double maxIOB = MainApp.getConstraintChecker().getMaxIOBAllowed().value();
                return maxIOB > 0;
            }
        });
    }
}
