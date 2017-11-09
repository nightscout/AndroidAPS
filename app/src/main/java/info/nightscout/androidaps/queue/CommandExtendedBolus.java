package info.nightscout.androidaps.queue;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandExtendedBolus extends Command {
    double insulin;
    int durationInMinutes;

    public CommandExtendedBolus(double insulin, int durationInMinutes, Callback callback) {
        commandType = CommandType.EXTENDEDBOLUS;
        this.insulin = insulin;
        this.durationInMinutes = durationInMinutes;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().setExtendedBolus(insulin, durationInMinutes);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "EXTENDEDBOLUS " + insulin + " U " + durationInMinutes + " min";
    }
}
