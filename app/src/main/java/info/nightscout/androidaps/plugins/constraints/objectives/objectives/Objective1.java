package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.general.actions.ActionsPlugin;
import info.nightscout.androidaps.utils.SP;

public class Objective1 extends Objective {


    public Objective1() {
        super("usage", R.string.objectives_usage_objective, R.string.objectives_usage_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new Task(R.string.objectives_useprofileswitch) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_objectiveuseprofileswitch, false);
            }
        });
        tasks.add(new Task(R.string.objectives_usedisconnectpump) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_objectiveusedisconnect, false);
            }
        });
        tasks.add(new Task(R.string.objectives_usereconnectpump) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_objectiveusereconnect, false);
            }
        });
        tasks.add(new Task(R.string.objectives_usetemptarget) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_objectiveusetemptarget, false);
            }
        });
        tasks.add(new Task(R.string.objectives_useactions) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_objectiveuseactions, false) && ActionsPlugin.INSTANCE.isEnabled(PluginType.GENERAL) && ActionsPlugin.INSTANCE.isFragmentVisible();
            }
        });
        tasks.add(new Task(R.string.objectives_useloop) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_objectiveuseloop, false);
            }
        });
        tasks.add(new Task(R.string.objectives_usescale) {
            @Override
            public boolean isCompleted() {
                return SP.getBoolean(R.string.key_objectiveusescale, false);
            }
        });
    }
}
