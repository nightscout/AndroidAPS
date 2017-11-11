package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandTempBasalPercent extends Command {
    int durationInMinutes;
    int percent;

    public CommandTempBasalPercent(int percent, int durationInMinutes, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.percent = percent;
        this.durationInMinutes = durationInMinutes;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = MainApp.getConfigBuilder().setTempBasalPercent(percent, durationInMinutes);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "TEMPBASAL " + percent + "% " + durationInMinutes + " min";
    }
}
