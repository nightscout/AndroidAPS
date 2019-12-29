package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import android.app.Activity;

import java.util.List;

import javax.inject.Inject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class Objective3 extends Objective {
    @Inject SP sp;
    @Inject ObjectivesPlugin objectivesPlugin;
    @Inject ResourceHelper resourceHelper;

    private final int MANUAL_ENACTS_NEEDED = 20;

    @Inject
    public Objective3() {
        super("openloop", R.string.objectives_openloop_objective, R.string.objectives_openloop_gate);
        MainApp.instance().androidInjector().inject(this); // TODO inject or pass itno constructor once AutomationPlugin is prepared for Dagger
        hasSpecialInput = true;
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
        return NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth;
    }

    @Override
    public void specialAction(Activity activity, String input) {
        objectivesPlugin.completeObjectives(activity, input);
    }
}
