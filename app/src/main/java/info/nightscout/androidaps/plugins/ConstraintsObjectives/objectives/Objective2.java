package info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;

public class Objective2 extends Objective {

    public static final int MANUAL_ENACTS_NEEDED = 20;

    public Objective2() {
        super(1, R.string.objectives_1_objective, R.string.objectives_1_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(7L * 24L * 60L * 60L * 1000L));
        tasks.add(new Task(R.string.objectives_manualenacts) {
            @Override
            public boolean isCompleted() {
                return ObjectivesPlugin.manualEnacts >= MANUAL_ENACTS_NEEDED;
            }

            @Override
            public String getProgress() {
                if (ObjectivesPlugin.manualEnacts >= MANUAL_ENACTS_NEEDED) return MainApp.gs(R.string.completed_well_done);
                else return ObjectivesPlugin.manualEnacts + " / " + MANUAL_ENACTS_NEEDED;
            }
        });
    }
}
