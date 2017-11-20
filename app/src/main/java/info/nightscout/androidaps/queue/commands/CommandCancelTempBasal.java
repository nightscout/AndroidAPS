package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandCancelTempBasal extends Command {
    boolean enforceNew;

    public CommandCancelTempBasal(boolean enforceNew, Callback callback) {
        commandType = CommandType.TEMPBASAL;
        this.enforceNew = enforceNew;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().cancelTempBasal(enforceNew);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "CANCEL TEMPBASAL";
    }
}
