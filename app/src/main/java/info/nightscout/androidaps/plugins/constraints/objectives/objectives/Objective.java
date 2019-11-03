package info.nightscout.androidaps.plugins.constraints.objectives.objectives;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.util.Linkify;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

public abstract class Objective {

    private String spName;
    @StringRes
    private int objective;
    @StringRes
    private int gate;
    private long startedOn;
    private long accomplishedOn;
    List<Task> tasks = new ArrayList<>();
    public boolean hasSpecialInput = false;

    public Objective(String spName, @StringRes int objective, @StringRes int gate) {
        this.spName = spName;
        this.objective = objective;
        this.gate = gate;
        startedOn = SP.getLong("Objectives_" + spName + "_started", 0L);
        accomplishedOn = SP.getLong("Objectives_" + spName + "_accomplished", 0L);
        if ((accomplishedOn - DateUtil.now()) > T.hours(3).msecs() || (startedOn - DateUtil.now()) > T.hours(3).msecs()) { // more than 3 hours in the future
            startedOn = 0;
            accomplishedOn = 0;
        }
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

    public boolean isCompleted(long trueTime) {
        for (Task task : tasks) {
            if (!task.shouldBeIgnored() && !task.isCompleted(trueTime))
                return false;
        }
        return true;
    }

    public boolean isRevertable() {
        return false;
    }

    public boolean isAccomplished() {
        return accomplishedOn != 0 && accomplishedOn < DateUtil.now();
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

    public void specialAction(Activity activity, String input) {}

    public abstract class Task {
        @StringRes
        private int task;
        private Objective objective;
        ArrayList<Hint> hints = new ArrayList<>();

        public Task(@StringRes int task) {
            this.task = task;
        }

        public @StringRes int getTask() {
            return task;
        }

        protected Objective getObjective() {
            return objective;
        }

        public abstract boolean isCompleted();
        public boolean isCompleted(long trueTime) { return isCompleted(); };

        public String getProgress() {
            return MainApp.gs(isCompleted() ? R.string.completed_well_done : R.string.not_completed_yet);
        }

        Task hint(Hint hint) {
            hints.add(hint);
            return this;
        }

        public ArrayList<Hint> getHints() {
            return hints;
        }

        public boolean shouldBeIgnored() {
            return false;
        }
    }

    public class MinimumDurationTask extends Task {

        private long minimumDuration;

        MinimumDurationTask(long minimumDuration) {
            super(R.string.time_elapsed);
            this.minimumDuration = minimumDuration;
        }

        @Override
        public boolean isCompleted() {
            return getObjective().isStarted() && System.currentTimeMillis() - getObjective().getStartedOn() >= minimumDuration;
        }

        @Override
        public boolean isCompleted(long trueTime) {
            return getObjective().isStarted() && trueTime - getObjective().getStartedOn() >= minimumDuration;
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

    public class ExamTask extends Task {
        @StringRes
        int question;
        ArrayList<Option> options = new ArrayList<>();
        private String spIdentifier;
        private boolean answered;
        private long disabledTo;

        ExamTask(@StringRes int task, @StringRes int question, String spIdentifier) {
            super(task);
            this.question = question;
            this.spIdentifier = spIdentifier;
            answered = SP.getBoolean("ExamTask_" + spIdentifier, false);
            disabledTo = SP.getLong("DisabledTo_" + spIdentifier, 0L);
        }

        public void setDisabledTo(long newState) {
            disabledTo = newState;
            SP.putLong("DisabledTo_" + spIdentifier, disabledTo);
        }

        public long getDisabledTo() {
            return disabledTo;
        }

        public boolean isEnabledAnswer() {
            return disabledTo < DateUtil.now();
        }

        public void setAnswered(boolean newState) {
            answered = newState;
            SP.putBoolean("ExamTask_" + spIdentifier, answered);
        }

        public boolean getAnswered() {
            return answered;
        }

        ExamTask option(Option option) {
            options.add(option);
            return this;
        }

        public @StringRes int getQuestion() {
            return question;
        }

        public List<Objective.Option> getOptions() {
            return options;
        }

        @Override
        public boolean isCompleted() {
            return answered;
        }
    }

    public class Option {
        @StringRes int option;
        boolean isCorrect;

        CheckBox cb; // TODO: change it, this will block releasing memeory

        Option(@StringRes int option, boolean isCorrect) {
            this.option = option;
            this.isCorrect = isCorrect;
        }

        public boolean isCorrect() {
            return isCorrect;
        }

        public CheckBox generate(Context context) {
            cb = new CheckBox(context);
            cb.setText(option);
            return cb;
        }

        public boolean evaluate() {
            boolean selection = cb.isChecked();
            if (selection && isCorrect) return true;
            if (!selection && !isCorrect) return true;
            return false;
        }
    }

    public class Hint {
        @StringRes int hint;

        Hint(@StringRes int hint) {
            this.hint = hint;
        }

        public TextView generate(Context context) {
            TextView textView = new TextView(context);
            textView.setText(hint);
            textView.setAutoLinkMask(Linkify.WEB_URLS);
            textView.setLinksClickable(true);
            textView.setLinkTextColor(Color.YELLOW);
            Linkify.addLinks(textView, Linkify.WEB_URLS);
            return textView;
        }
    }
}
