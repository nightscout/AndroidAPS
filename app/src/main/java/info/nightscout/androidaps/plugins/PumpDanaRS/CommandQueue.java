package info.nightscout.androidaps.plugins.PumpDanaRS;

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
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 08.11.2017.
 */

public class CommandQueue {
    private static Logger log = LoggerFactory.getLogger(CommandQueue.class);

    enum CommandType {
        BOLUS,
        TEMPBASALPERCENT,
        TEMPBASALABSOLUTE,
        CANCELTEMPBASAL,
        EXTENDEDBOLUS,
        CANCELEXTENDEDBOLUS,
        SETBASALPROFILE
    }

    public class Command {
        CommandType commandType;
        Callback callback;

        int durationInMinutes;

        // Bolus
        DetailedBolusInfo detailedBolusInfo;
        // Temp basal percent
        int percent;
        // Temp basal absolute
        double absoluteRate;
        boolean enforceNew;
        // Extended bolus
        double insulin;
        // Basal profile
        Profile profile;

        public String status() {
            switch (commandType) {
                case BOLUS:
                    return "BOLUS " + DecimalFormatter.to1Decimal(detailedBolusInfo.insulin) + "U";
                case TEMPBASALPERCENT:
                    return "TEMPBASAL " + percent + "% " + durationInMinutes + " min";
                case TEMPBASALABSOLUTE:
                    return "TEMPBASAL " + absoluteRate + " U/h " + durationInMinutes + " min";
                case CANCELTEMPBASAL:
                    return "CANCEL TEMPBASAL";
                case EXTENDEDBOLUS:
                    return "EXTENDEDBOLUS " + insulin + " U " + durationInMinutes + " min";
                case CANCELEXTENDEDBOLUS:
                    return "CANCEL EXTENDEDBOLUS";
                case SETBASALPROFILE:
                    return "SETPROFILE";
                default:
                    return "";
            }
        }
    }

    public class Callback {
        public PumpEnactResult result;
        Runnable runnable;

        public Callback(Runnable runnable) {
            this.runnable = runnable;
        }

        public Callback result(PumpEnactResult result) {
            this.result = result;
            return this;
        }

        public void run() {
            runnable.run();
        }
    }

    private LinkedList<Command> queue = new LinkedList<>();
    private Command performing;

    private PumpEnactResult executingNowError() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.enacted = false;
        result.comment = MainApp.sResources.getString(R.string.executingrightnow);
        return result;
    }

    public boolean isRunningTempBasal() {
        if (performing != null)
            if (performing.commandType == CommandType.TEMPBASALABSOLUTE || performing.commandType == CommandType.TEMPBASALPERCENT || performing.commandType == CommandType.CANCELTEMPBASAL)
            return true;
        return false;
    }

    public boolean isRunningBolus() {
        if (performing != null)
            if (performing.commandType == CommandType.BOLUS)
            return true;
        return false;
    }

    public boolean isRunningExtendedBolus() {
        if (performing != null)
            if (performing.commandType == CommandType.EXTENDEDBOLUS || performing.commandType == CommandType.CANCELEXTENDEDBOLUS)
                return true;
        return false;
    }

    public boolean isRunningProfile() {
        if (performing != null)
            if (performing.commandType == CommandType.SETBASALPROFILE)
                return true;
        return false;
    }

    private synchronized void removeAll(CommandType type) {
        for (int i = 0; i < queue.size(); i++) {
            Command c = queue.get(i);
            switch (type) {
                case TEMPBASALABSOLUTE:
                case TEMPBASALPERCENT:
                case CANCELTEMPBASAL:
                    if (c.commandType == CommandType.TEMPBASALABSOLUTE || c.commandType == CommandType.TEMPBASALPERCENT || c.commandType == CommandType.CANCELTEMPBASAL) {
                        queue.remove(i);
                    }
                    break;
                case BOLUS:
                    if (c.commandType == CommandType.BOLUS) {
                        queue.remove(i);
                    }
                    break;
                case EXTENDEDBOLUS:
                case CANCELEXTENDEDBOLUS:
                    if (c.commandType == CommandType.EXTENDEDBOLUS || c.commandType == CommandType.CANCELEXTENDEDBOLUS) {
                        queue.remove(i);
                    }
                    break;
                case SETBASALPROFILE:
                    if (c.commandType == CommandType.SETBASALPROFILE) {
                        queue.remove(i);
                    }
                    break;
            }
        }
    }

    private synchronized void add(Command command) {
        queue.add(command);
    }

    private synchronized void pickup() {
        performing = queue.poll();
    }

    public void clear() {
        queue.clear();
    }

    private void notifyAboutNewCommand() {

    }

    // returns true if command is queued
    public boolean bolus(DetailedBolusInfo detailedBolusInfo, Callback callback) {
        if (isRunningBolus()) {
            callback.result(executingNowError()).run();
            return false;
        }

        // remove all unfinished boluese
        removeAll(CommandType.BOLUS);

        // add new command to queue
        Command command = new Command();
        command.commandType = CommandType.BOLUS;
        command.detailedBolusInfo = detailedBolusInfo;
        command.callback = callback;
        add(command);

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
        removeAll(CommandType.TEMPBASALABSOLUTE);

        // add new command to queue
        Command command = new Command();
        command.commandType = CommandType.TEMPBASALABSOLUTE;
        command.absoluteRate = absoluteRate;
        command.durationInMinutes = durationInMinutes;
        command.enforceNew = enforceNew;
        command.callback = callback;
        add(command);

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
        removeAll(CommandType.TEMPBASALPERCENT);

        // add new command to queue
        Command command = new Command();
        command.commandType = CommandType.TEMPBASALPERCENT;
        command.percent = percent;
        command.durationInMinutes = durationInMinutes;
        command.callback = callback;
        add(command);

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
        removeAll(CommandType.EXTENDEDBOLUS);

        // add new command to queue
        Command command = new Command();
        command.commandType = CommandType.EXTENDEDBOLUS;
        command.insulin = insulin;
        command.durationInMinutes = durationInMinutes;
        command.callback = callback;
        add(command);

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
        removeAll(CommandType.CANCELTEMPBASAL);

        // add new command to queue
        Command command = new Command();
        command.commandType = CommandType.CANCELTEMPBASAL;
        command.enforceNew = enforceNew;
        command.callback = callback;
        add(command);

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
        removeAll(CommandType.CANCELEXTENDEDBOLUS);

        // add new command to queue
        Command command = new Command();
        command.commandType = CommandType.CANCELEXTENDEDBOLUS;
        command.callback = callback;
        add(command);

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
        removeAll(CommandType.SETBASALPROFILE);

        // add new command to queue
        Command command = new Command();
        command.commandType = CommandType.SETBASALPROFILE;
        command.profile = profile;
        command.callback = callback;
        add(command);

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
