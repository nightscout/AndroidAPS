package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.T;

public class Objective10 extends Objective {

    public Objective10(HasAndroidInjector injector) {
        super(injector, "auto", R.string.objectives_auto_objective, R.string.objectives_auto_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(28).msecs()));
    }
}
