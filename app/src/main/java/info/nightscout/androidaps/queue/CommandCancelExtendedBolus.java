package info.nightscout.androidaps.queue;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandCancelExtendedBolus extends Command {

    public CommandCancelExtendedBolus(Callback callback) {
        commandType = CommandType.EXTENDEDBOLUS;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().cancelExtendedBolus();
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "CANCEL EXTENDEDBOLUS";
    }
}
