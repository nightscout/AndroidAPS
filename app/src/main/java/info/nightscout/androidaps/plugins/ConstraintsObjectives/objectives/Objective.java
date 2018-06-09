package info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives;

import android.support.annotation.StringRes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.utils.SP;

public abstract class Objective {

    private int number;
    @StringRes
    private int objective;
    @StringRes
    private int gate;
    private Date startedOn;
    private Date accomplishedOn;
    private List<Task> tasks = new ArrayList<>();

    public Objective(int number, @StringRes int objective, @StringRes int gate) {
        this.number = number;
        this.objective = objective;
        this.gate = gate;
        startedOn = new Date(SP.getLong("Objectives" + number + "started", 0L));
        if (startedOn.getTime() == 0L) startedOn = null;
        accomplishedOn = new Date(SP.getLong("Objectives" + number + "accomplished", 0L));
        if (accomplishedOn.getTime() == 0L) accomplishedOn = null;
        setupTasks(tasks);
        for (Task task : tasks) task.objective = this;
    }

    public boolean isAccomplished() {
        boolean completed = true;
        for (Task task : tasks) {
            if (!task.shouldBeIgnored() && !task.isCompleted()) {
                completed = false;
                break;
            }
        }
        return startedOn != null && (accomplishedOn != null || completed);
    }

    public boolean isStarted() {
        return startedOn != null;
    }

    public Date getStartedOn() {
        return startedOn;
    }

    public int getObjective() {
        return objective;
    }

    public int getGate() {
        return gate;
    }

    public void setStartedOn(Date startedOn) {
        this.startedOn = startedOn;
        SP.putLong("Objectives" + number + "started", startedOn == null ? 0 : startedOn.getTime());
    }

    public void setAccomplishedOn(Date accomplishedOn) {
        this.accomplishedOn = accomplishedOn;
        SP.putLong("Objectives" + number + "accomplished", accomplishedOn == null ? 0 : accomplishedOn.getTime());
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
            super(R.string.time_leftover);
            this.minimumDuration = minimumDuration;
        }

        @Override
        public boolean isCompleted() {
            return getObjective().isStarted() && System.currentTimeMillis() - getObjective().getStartedOn().getTime() >= minimumDuration;
        }

        @Override
        public String getProgress() {
            long timeLeftover = minimumDuration - (System.currentTimeMillis() - getObjective().getStartedOn().getTime());
            int days = (int) (minimumDuration / (24L * 60L * 60L * 1000L));
            int hours = (int) (minimumDuration / (60L * 60L * 1000L));
            int minutes = (int) (minimumDuration / (60L * 1000L));
            if (days > 0) return MainApp.gq(R.plurals.objective_days, days, days);
            else if (hours > 0) return MainApp.gq(R.plurals.objective_hours, hours, hours);
            else if (minutes > 0) return MainApp.gq(R.plurals.objective_minutes, minutes, minutes);
            else if (timeLeftover > 0) return MainApp.gq(R.plurals.objective_minutes, 1, 1);
            else return MainApp.gs(R.string.time_none);
        }
    }

}
