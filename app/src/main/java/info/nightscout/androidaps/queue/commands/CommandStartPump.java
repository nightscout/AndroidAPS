package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
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
