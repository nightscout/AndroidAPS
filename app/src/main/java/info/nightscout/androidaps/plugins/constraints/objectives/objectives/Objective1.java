package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.general.actions.ActionsPlugin;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class Objective1 extends Objective {
    @Inject SP sp;
    @Inject ActionsPlugin actionsPlugin;

    @Inject
    public Objective1(HasAndroidInjector injector) {
        super(injector, "usage", R.string.objectives_usage_objective, R.string.objectives_usage_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new Task(R.string.objectives_useprofileswitch) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_objectiveuseprofileswitch, false);
            }
        });
        tasks.add(new Task(R.string.objectives_usedisconnectpump) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_objectiveusedisconnect, false);
            }
        }.hint(new Hint(R.string.disconnectpump_hint)));
        tasks.add(new Task(R.string.objectives_usereconnectpump) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_objectiveusereconnect, false);
            }
        }.hint(new Hint(R.string.disconnectpump_hint)));
        tasks.add(new Task(R.string.objectives_usetemptarget) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_objectiveusetemptarget, false);
            }
        }.hint(new Hint(R.string.usetemptarget_hint)));
        tasks.add(new Task(R.string.objectives_useactions) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_objectiveuseactions, false) && actionsPlugin.isEnabled(PluginType.GENERAL) && actionsPlugin.isFragmentVisible();
            }
        }.hint(new Hint(R.string.useaction_hint)));
        tasks.add(new Task(R.string.objectives_useloop) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_objectiveuseloop, false);
            }
        }.hint(new Hint(R.string.useaction_hint)));
        tasks.add(new Task(R.string.objectives_usescale) {
            @Override
            public boolean isCompleted() {
                return sp.getBoolean(R.string.key_objectiveusescale, false);
            }
        }.hint(new Hint(R.string.usescale_hint)));
    }
}
