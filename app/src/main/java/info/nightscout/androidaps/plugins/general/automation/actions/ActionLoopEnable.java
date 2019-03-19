package info.nightscout.androidaps.plugins.general.automation.actions;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.queue.Callback;

public class ActionLoopEnable extends Action {
    @Override
    public int friendlyName() {
        return R.string.enableloop;
    }

    @Override
    void doAction(Callback callback) {
        if (!LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)) {
            LoopPlugin.getPlugin().setPluginEnabled(PluginType.LOOP, true);
            ConfigBuilderPlugin.getPlugin().storeSettings("ActionLoopEnable");
            MainApp.bus().post(new EventRefreshOverview("ActionLoopEnable"));
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
        } else {
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.alreadyenabled)).run();
        }
    }
}
