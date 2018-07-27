package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandTempBasalAbsolute extends Command {
    private Logger log = LoggerFactory.getLogger(Constants.QUEUE);

    int durationInMinutes;
    double absoluteRate;
    boolean enforceNew;
    Profile profile;

    public CommandTempBasalAbsolute(double absoluteRate, int durationInMinutes, boolean enforceNew, Profile profile, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.absoluteRate = absoluteRate;
        this.durationInMinutes = durationInMinutes;
        this.enforceNew = enforceNew;
        this.profile = profile;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().setTempBasalAbsolute(absoluteRate, durationInMinutes, profile, enforceNew);
        if (Config.logQueue)
            log.debug("Result rate: " + absoluteRate + " durationInMinutes: " + durationInMinutes + " success: " + r.success + " enacted: " + r.enacted);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "TEMPBASAL " + absoluteRate + " U/h " + durationInMinutes + " min";
    }
}
