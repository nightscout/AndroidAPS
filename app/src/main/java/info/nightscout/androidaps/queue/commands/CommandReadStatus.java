package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandReadStatus extends Command {
    String reason;

    public CommandReadStatus(String reason, Callback callback) {
        commandType = CommandType.READSTATUS;
        this.reason = reason;
        this.callback = callback;
    }

    @Override
    public void execute() {
        ConfigBuilderPlugin.getActivePump().getPumpStatus();
        if (callback != null)
            callback.result(null).run();
    }

    @Override
    public String status() {
        return "READSTATUS";
    }
}
