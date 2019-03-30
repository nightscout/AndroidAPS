package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.T;

public class Objective6 extends Objective {

    public Objective6() {
        super(5, R.string.objectives_5_objective, R.string.objectives_5_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(7).msecs()));
    }
}
