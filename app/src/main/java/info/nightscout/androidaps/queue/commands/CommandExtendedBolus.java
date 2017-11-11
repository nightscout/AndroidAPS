package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandExtendedBolus extends Command {
    private double insulin;
    private int durationInMinutes;

    public CommandExtendedBolus(double insulin, int durationInMinutes, Callback callback) {
        commandType = CommandType.EXTENDEDBOLUS;
        this.insulin = insulin;
        this.durationInMinutes = durationInMinutes;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = MainApp.getConfigBuilder().setExtendedBolus(insulin, durationInMinutes);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "EXTENDEDBOLUS " + insulin + " U " + durationInMinutes + " min";
    }
}
