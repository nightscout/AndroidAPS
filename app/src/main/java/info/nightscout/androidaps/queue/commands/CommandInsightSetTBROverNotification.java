package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
import info.nightscout.androidaps.queue.Callback;

public class CommandInsightSetTBROverNotification extends Command {

    private boolean enabled;

    public CommandInsightSetTBROverNotification(Callback callback, boolean enabled) {
        commandType = CommandType.INSIGHT_SET_TBR_OVER_ALARM;
        this.callback = callback;
        this.enabled = enabled;
    }

    @Override
    public void execute() {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump instanceof LocalInsightPlugin) {
            PumpEnactResult result = ((LocalInsightPlugin) pump).setTBROverNotification(enabled);
            if (callback != null) callback.result(result).run();
        }
    }

    @Override
    public String status() {
        return "INSIGHTSETTBROVERNOTIFICATION";
    }
}
