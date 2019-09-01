package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

public abstract class Objective {

    String spName;
    @StringRes
    private int objective;
    @StringRes
    private int gate;
    private long startedOn;
    private long accomplishedOn;
    private List<Task> tasks = new ArrayList<>();

    public Objective(String spName, @StringRes int objective, @StringRes int gate) {
        this.spName = spName;
        this.objective = objective;
        this.gate = gate;
        startedOn = SP.getLong("Objectives_" + spName + "_started", 0L);
        accomplishedOn = SP.getLong("Objectives_" + spName + "_accomplished", 0L);
        setupTasks(tasks);
        for (Task task : tasks) task.objective = this;
    }

    public boolean isCompleted() {
        for (Task task : tasks) {
            if (!task.shouldBeIgnored() && !task.isCompleted())
                return false;
        }
        return true;
    }

    public boolean isRevertable() {
        return false;
    }

    public boolean isAccomplished() {
        return accomplishedOn != 0;
    }

    public boolean isStarted() {
        return startedOn != 0;
    }

    public long getStartedOn() {
        return startedOn;
    }

    public int getObjective() {
        return objective;
    }

    public int getGate() {
        return gate;
    }

    public void setStartedOn(long startedOn) {
        this.startedOn = startedOn;
        SP.putLong("Objectives_" + spName + "_started", startedOn);
    }

    public void setAccomplishedOn(long accomplishedOn) {
        this.accomplishedOn = accomplishedOn;
        SP.putLong("Objectives_" + spName + "_accomplished", accomplishedOn);
    }

    public long getAccomplishedOn() {
        return accomplishedOn;
    }

    protected void setupTasks(List<Task> tasks) {

    }

    public List<Task> getTasks() {
        return tasks;
    }

    public abstract class Task {
        @StringRes
        private int task;
        private Objective objective;

        public Task(@StringRes int task) {
            this.task = task;
        }

        public int getTask() {
            return task;
        }

        protected Objective getObjective() {
            return objective;
        }

        public abstract boolean isCompleted();

        public String getProgress() {
            return MainApp.gs(isCompleted() ? R.string.completed_well_done : R.string.not_completed_yet);
        }

        public boolean shouldBeIgnored() {
            return false;
        }
    }

    public class MinimumDurationTask extends Task {

        private long minimumDuration;

        public MinimumDurationTask(long minimumDuration) {
            super(R.string.time_elapsed);
            this.minimumDuration = minimumDuration;
        }

        @Override
        public boolean isCompleted() {
            return getObjective().isStarted() && System.currentTimeMillis() - getObjective().getStartedOn() >= minimumDuration;
        }

        @Override
        public String getProgress() {
            return getDurationText(System.currentTimeMillis() - getObjective().getStartedOn())
                    + " / " + getDurationText(minimumDuration);
        }

        private String getDurationText(long duration) {
            int days = (int) Math.floor((double) duration / T.days(1).msecs());
            int hours = (int) Math.floor((double) duration / T.hours(1).msecs());
            int minutes = (int) Math.floor((double) duration / T.mins(1).msecs());
            if (days > 0) return MainApp.gq(R.plurals.objective_days, days, days);
            else if (hours > 0) return MainApp.gq(R.plurals.objective_hours, hours, hours);
            else return MainApp.gq(R.plurals.objective_minutes, minutes, minutes);
        }
    }

}
