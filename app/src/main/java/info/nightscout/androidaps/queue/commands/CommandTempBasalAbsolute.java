package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandTempBasalAbsolute extends Command {
    private static Logger log = LoggerFactory.getLogger(CommandTempBasalAbsolute.class);

    int durationInMinutes;
    double absoluteRate;
    boolean enforceNew;

    public CommandTempBasalAbsolute(double absoluteRate, int durationInMinutes, boolean enforceNew, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.absoluteRate = absoluteRate;
        this.durationInMinutes = durationInMinutes;
        this.enforceNew = enforceNew;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().setTempBasalAbsolute(absoluteRate, durationInMinutes, enforceNew);
        if (Config.logCongigBuilderActions)
            log.debug("setTempBasalAbsolute rate: " + absoluteRate + " durationInMinutes: " + durationInMinutes + " success: " + r.success + " enacted: " + r.enacted);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "TEMPBASAL " + absoluteRate + " U/h " + durationInMinutes + " min";
    }
}
