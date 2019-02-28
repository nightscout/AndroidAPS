package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
import info.nightscout.androidaps.queue.Callback;

public class CommandStopPump extends Command {

    public CommandStopPump(Callback callback) {
        commandType = CommandType.STOP_PUMP;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump instanceof LocalInsightPlugin) {
            PumpEnactResult result = ((LocalInsightPlugin) pump).stopPump();
            if (callback != null) callback.result(result).run();
        }
    }

    @Override
    public String status() {
        return "STOPPUMP";
    }
}
