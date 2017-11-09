package info.nightscout.androidaps.queue;

import android.text.Html;
import android.text.Spanned;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;

/**
 * Created by mike on 08.11.2017.
 */

public class CommandQueue {
    private static Logger log = LoggerFactory.getLogger(CommandQueue.class);

    private LinkedList<Command> queue = new LinkedList<>();
    private Command performing;

    private QueueThread thread = new QueueThread(this);

    private PumpEnactResult executingNowError() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.enacted = false;
        result.comment = MainApp.sResources.getString(R.string.executingrightnow);
        return result;
    }

    public boolean isRunningTempBasal() {
        if (performing != null && performing.commandType == Command.CommandType.TEMPBASAL)
            return true;
        return false;
    }

    public boolean isRunningBolus() {
        if (performing != null && performing.commandType == Command.CommandType.BOLUS)
            return true;
        return false;
    }

    public boolean isRunningExtendedBolus() {
        if (performing != null && performing.commandType == Command.CommandType.EXTENDEDBOLUS)
            return true;
        return false;
    }

    public boolean isRunningProfile() {
        if (performing != null && performing.commandType == Command.CommandType.BASALPROFILE)
            return true;
        return false;
    }

    private synchronized void removeAll(Command.CommandType type) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).commandType == type) {
                queue.remove(i);
            }
        }
    }

    private synchronized void add(Command command) {
        queue.add(command);
    }

    protected synchronized void pickup() {
        performing = queue.poll();
    }

    public void clear() {
        queue.clear();
    }

    public int size() {
        return queue.size();
    }

    public Command performing() {
        return performing;
    }

    public void resetPerforming() {
        performing = null;
    }

    private void notifyAboutNewCommand() {
        if (!thread.isAlive())
            thread.start();
    }

    // returns true if command is queued
    public boolean bolus(DetailedBolusInfo detailedBolusInfo, Callback callback) {
        if (isRunningBolus()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluses
        removeAll(Command.CommandType.BOLUS);

        // add new command to queue
        add(new CommandBolus(detailedBolusInfo, callback));

        notifyAboutNewCommand();

        return true;
    }

    // returns true if command is queued
    public boolean tempBasalAbsolute(double absoluteRate, int durationInMinutes, boolean enforceNew, Callback callback) {
        if (isRunningTempBasal()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluese
        removeAll(Command.CommandType.TEMPBASAL);

        // add new command to queue
        add(new CommandTempBasalAbsolute(absoluteRate, durationInMinutes, enforceNew, callback));

        notifyAboutNewCommand();

        return true;
    }

    // returns true if command is queued
    public boolean tempBasalPercent(int percent, int durationInMinutes, Callback callback) {
        if (isRunningTempBasal()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluese
        removeAll(Command.CommandType.TEMPBASAL);

        // add new command to queue
        add(new CommandTempBasalPercent(percent, durationInMinutes, callback));

        notifyAboutNewCommand();

        return true;
    }

    // returns true if command is queued
    public boolean extendedBolus(double insulin, int durationInMinutes, Callback callback) {
        if (isRunningExtendedBolus()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluese
        removeAll(Command.CommandType.EXTENDEDBOLUS);

        // add new command to queue
        add(new CommandExtendedBolus(insulin, durationInMinutes, callback));

        notifyAboutNewCommand();

        return true;
    }

    // returns true if command is queued
    public boolean cancelTempBasal(boolean enforceNew, Callback callback) {
        if (isRunningTempBasal()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluese
        removeAll(Command.CommandType.TEMPBASAL);

        // add new command to queue
        add(new CommandCancelTempBasal(enforceNew, callback));

        notifyAboutNewCommand();

        return true;
    }

    // returns true if command is queued
    public boolean cancelExtended(Callback callback) {
        if (isRunningExtendedBolus()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluese
        removeAll(Command.CommandType.EXTENDEDBOLUS);

        // add new command to queue
        add(new CommandCancelExtendedBolus(callback));

        notifyAboutNewCommand();

        return true;
    }

    // returns true if command is queued
    public boolean setProfile(Profile profile, Callback callback) {
        if (isRunningProfile()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluese
        removeAll(Command.CommandType.BASALPROFILE);

        // add new command to queue
        add(new CommandSetProfile(profile, callback));

        notifyAboutNewCommand();

        return true;
    }

    Spanned spannedStatus() {
        String s = "";
        if (performing != null) {
            s += "<b>" + performing.status() + "</b><br>";
        }
        for (int i = 0; i < queue.size(); i++) {
            s += queue.get(i).status() + "<br>";
        }
        return Html.fromHtml(s);
    }

}
