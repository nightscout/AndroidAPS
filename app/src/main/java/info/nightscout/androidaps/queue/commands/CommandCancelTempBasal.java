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

public class CommandCancelTempBasal extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    private boolean enforceNew;

    public CommandCancelTempBasal(boolean enforceNew, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.enforceNew = enforceNew;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getPlugin().getActivePump().cancelTempBasal(enforceNew);
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("Result success: " + r.success + " enacted: " + r.enacted);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "CANCEL TEMPBASAL";
    }
}
