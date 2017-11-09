package info.nightscout.androidaps.queue;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandTempBasalPercent extends Command {
    int durationInMinutes;
    int percent;

    CommandTempBasalPercent(int percent, int durationInMinutes, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.percent = percent;
        this.durationInMinutes = durationInMinutes;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().setTempBasalPercent(percent, durationInMinutes);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "TEMPBASAL " + percent + "% " + durationInMinutes + " min";
    }
}
