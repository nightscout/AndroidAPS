package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandTempBasalPercent extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    private int durationInMinutes;
    private int percent;
    private boolean enforceNew;
    private Profile profile;

    public CommandTempBasalPercent(int percent, int durationInMinutes, boolean enforceNew, Profile profile, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.percent = percent;
        this.durationInMinutes = durationInMinutes;
        this.enforceNew = enforceNew;
        this.profile = profile;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getPlugin().getActivePump().setTempBasalPercent(percent, durationInMinutes, profile, enforceNew);
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("Result percent: " + percent + " durationInMinutes: " + durationInMinutes + " success: " + r.success + " enacted: " + r.enacted);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "TEMPBASAL " + percent + "% " + durationInMinutes + " min";
    }
}
