package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.T;

public class Objective7 extends Objective {

    public Objective7(HasAndroidInjector injector) {
        super(injector, "autosens", R.string.objectives_autosens_objective, R.string.objectives_autosens_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(7).msecs()));
    }
}
