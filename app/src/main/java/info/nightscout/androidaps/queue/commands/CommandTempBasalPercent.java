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

public class CommandTempBasalPercent extends Command {
    private static Logger log = LoggerFactory.getLogger(CommandTempBasalPercent.class);

    int durationInMinutes;
    int percent;
    boolean enforceNew;

    public CommandTempBasalPercent(int percent, int durationInMinutes, boolean enforceNew, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.percent = percent;
        this.durationInMinutes = durationInMinutes;
        this.enforceNew = enforceNew;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().setTempBasalPercent(percent, durationInMinutes, enforceNew);
        if (Config.logCongigBuilderActions)
            log.debug("setTempBasalPercent percent: " + percent + " durationInMinutes: " + durationInMinutes + " success: " + r.success + " enacted: " + r.enacted);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "TEMPBASAL " + percent + "% " + durationInMinutes + " min";
    }
}
