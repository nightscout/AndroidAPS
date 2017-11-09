package info.nightscout.androidaps.queue;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandTempBasalAbsolute extends Command {
    int durationInMinutes;
    double absoluteRate;
    boolean enforceNew;

    CommandTempBasalAbsolute(double absoluteRate, int durationInMinutes, boolean enforceNew, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.absoluteRate = absoluteRate;
        this.durationInMinutes = durationInMinutes;
        this.enforceNew = enforceNew;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().setTempBasalAbsolute(absoluteRate, durationInMinutes, enforceNew);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "TEMPBASAL " + absoluteRate + " U/h " + durationInMinutes + " min";
    }
}
