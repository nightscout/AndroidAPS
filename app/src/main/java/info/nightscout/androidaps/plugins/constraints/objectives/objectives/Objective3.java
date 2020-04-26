package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import android.app.Activity;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

public class Objective3 extends Objective {

    public final int MANUAL_ENACTS_NEEDED = 20;

    public Objective3() {
        super("openloop", R.string.objectives_openloop_objective, R.string.objectives_openloop_gate);
        hasSpecialInput = true;
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(7).msecs()));
        tasks.add(new Task(R.string.objectives_manualenacts) {
            @Override
            public boolean isCompleted() {
                return SP.getInt(R.string.key_ObjectivesmanualEnacts, 0) >= MANUAL_ENACTS_NEEDED;
            }

            @Override
            public String getProgress() {
                if (SP.getInt(R.string.key_ObjectivesmanualEnacts, 0) >= MANUAL_ENACTS_NEEDED)
                    return MainApp.gs(R.string.completed_well_done);
                else
                    return SP.getInt(R.string.key_ObjectivesmanualEnacts, 0) + " / " + MANUAL_ENACTS_NEEDED;
            }
        });
    }

    @Override
    public boolean specialActionEnabled() {
        return NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth;
    }

    @Override
    public void specialAction(Activity activity, String input) {
        ObjectivesPlugin.INSTANCE.completeObjectives(activity, input);
    }
}
