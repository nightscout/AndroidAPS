package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import androidx.fragment.app.FragmentActivity;

import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class Objective3 extends Objective {
    @Inject SP sp;
    @Inject ObjectivesPlugin objectivesPlugin;
    @Inject ResourceHelper resourceHelper;
    @Inject NSClientPlugin nsClientPlugin;

    private final int MANUAL_ENACTS_NEEDED = 20;

    @Inject
    public Objective3(HasAndroidInjector injector) {
        super(injector, "openloop", R.string.objectives_openloop_objective, R.string.objectives_openloop_gate);
        // disable option for skipping objectives for now
        // hasSpecialInput = true;
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(7).msecs()));
        tasks.add(new Task(R.string.objectives_manualenacts) {
            @Override
            public boolean isCompleted() {
                return sp.getInt(R.string.key_ObjectivesmanualEnacts, 0) >= MANUAL_ENACTS_NEEDED;
            }

            @Override
            public String getProgress() {
                if (sp.getInt(R.string.key_ObjectivesmanualEnacts, 0) >= MANUAL_ENACTS_NEEDED)
                    return resourceHelper.gs(R.string.completed_well_done);
                else
                    return sp.getInt(R.string.key_ObjectivesmanualEnacts, 0) + " / " + MANUAL_ENACTS_NEEDED;
            }
        });
    }

    @Override
    public boolean specialActionEnabled() {
        return NSClientService.isConnected && NSClientService.hasWriteAuth;
    }

    @Override
    public void specialAction(FragmentActivity activity, String input) {
        objectivesPlugin.completeObjectives(activity, input);
    }
}
