package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandExtendedBolus extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

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
        PumpEnactResult r = ConfigBuilderPlugin.getPlugin().getActivePump().setExtendedBolus(insulin, durationInMinutes);
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("Result rate: " + insulin + " durationInMinutes: " + durationInMinutes + " success: " + r.success + " enacted: " + r.enacted);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "EXTENDEDBOLUS " + insulin + " U " + durationInMinutes + " min";
    }
}
