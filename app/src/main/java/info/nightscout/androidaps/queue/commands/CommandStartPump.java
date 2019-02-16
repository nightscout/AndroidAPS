package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpInsightLocal.LocalInsightPlugin;
import info.nightscout.androidaps.queue.Callback;

public class CommandStartPump extends Command {

    public CommandStartPump(Callback callback) {
        commandType = CommandType.START_PUMP;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump instanceof LocalInsightPlugin) {
            PumpEnactResult result = ((LocalInsightPlugin) pump).startPump();
            if (callback != null) callback.result(result).run();
        }
    }

    @Override
    public String status() {
        return "STARTPUMP";
    }
}
