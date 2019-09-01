package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.utils.T;

public class Objective6 extends Objective {

    public Objective6() {
        super(ObjectivesPlugin.AUTOSENS_OBJECTIVE, R.string.objectives_autosens_objective, R.string.objectives_autosens_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(7).msecs()));
    }
}
