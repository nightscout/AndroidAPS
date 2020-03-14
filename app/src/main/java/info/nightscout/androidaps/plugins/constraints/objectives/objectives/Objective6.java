package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.utils.T;

public class Objective6 extends Objective {
    @Inject ConstraintChecker constraintChecker;

    public Objective6(HasAndroidInjector injector) {
        super(injector, "maxiob", R.string.objectives_maxiob_objective, R.string.objectives_maxiob_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(1).msecs()));
        tasks.add(new Task(R.string.maxiobset) {
            @Override
            public boolean isCompleted() {
                double maxIOB = constraintChecker.getMaxIOBAllowed().value();
                return maxIOB > 0;
            }
        });
    }
}
