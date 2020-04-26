package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.T;

public class Objective9 extends Objective {

    public Objective9() {
        super("smb", R.string.objectives_smb_objective, R.string.objectives_smb_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(28).msecs()));
    }
}
