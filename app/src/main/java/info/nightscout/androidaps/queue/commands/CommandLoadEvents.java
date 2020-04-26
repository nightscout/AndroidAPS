package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 10.11.2017.
 */

public class CommandLoadEvents extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    public CommandLoadEvents(Callback callback) {
        commandType = CommandType.LOADEVENTS;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump instanceof DanaRInterface) {
            DanaRInterface danaPump = (DanaRInterface) pump;
            PumpEnactResult r = danaPump.loadEvents();
            if (L.isEnabled(L.PUMPQUEUE))
                log.debug("Result success: " + r.success + " enacted: " + r.enacted);
            if (callback != null)
                callback.result(r).run();
        }
    }

    @Override
    public String status() {
        return "LOADEVENTS";
    }
}
