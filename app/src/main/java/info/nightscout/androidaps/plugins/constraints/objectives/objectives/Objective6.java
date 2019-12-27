package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.utils.T;

public class Objective6 extends Objective {

    public Objective6() {
        super("maxiob", R.string.objectives_maxiob_objective, R.string.objectives_maxiob_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(1).msecs()));
        tasks.add(new Task(R.string.maxiobset) {
            @Override
            public boolean isCompleted() {
                double maxIOB = ConstraintChecker.getInstance().getMaxIOBAllowed().value();
                return maxIOB > 0;
            }
        });
    }
}
