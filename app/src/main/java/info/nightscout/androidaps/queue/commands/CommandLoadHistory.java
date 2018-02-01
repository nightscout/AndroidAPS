package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.Command;

/**
 * Created by mike on 10.11.2017.
 */

public class CommandLoadHistory extends Command {
    byte type;

    public CommandLoadHistory(byte type, Callback callback) {
        commandType = CommandType.LOADHISTORY;
        this.type = type;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        if (pump instanceof DanaRInterface) {
            DanaRInterface danaPump = (DanaRInterface) pump;
            PumpEnactResult r = danaPump.loadHistory(type);
            if (callback != null)
                callback.result(r).run();
        }
    }

    @Override
    public String status() {
        return "LOADHISTORY " + type;
    }
}
