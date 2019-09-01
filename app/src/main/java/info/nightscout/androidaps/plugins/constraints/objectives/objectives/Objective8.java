package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.utils.T;

public class Objective8 extends Objective {

    public Objective8() {
        super(ObjectivesPlugin.SMB_OBJECTIVE, R.string.objectives_smb_objective, R.string.objectives_smb_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(28).msecs()));
    }
}
