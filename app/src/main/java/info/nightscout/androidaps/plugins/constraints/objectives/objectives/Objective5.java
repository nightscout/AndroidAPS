package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.T;

public class Objective5 extends Objective {

    public Objective5() {
        super(4, R.string.objectives_4_objective, R.string.objectives_4_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(1).msecs()));
        tasks.add(new Task(R.string.maxiobset) {
            @Override
            public boolean isCompleted() {
                double maxIOB = MainApp.getConstraintChecker().getMaxIOBAllowed().value();
                return maxIOB > 0;
            }
        });
    }
}
