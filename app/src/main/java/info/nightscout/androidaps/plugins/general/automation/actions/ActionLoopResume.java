package info.nightscout.androidaps.plugins.general.automation.actions;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSUpload;
import info.nightscout.androidaps.queue.Callback;

public class ActionLoopResume extends Action {
    @Override
    int friendlyName() {
        return R.string.resumeloop;
    }

    @Override
    void doAction(Callback callback) {
        if (LoopPlugin.getPlugin().isSuspended()) {
            LoopPlugin.getPlugin().suspendTo(0);
            ConfigBuilderPlugin.getPlugin().storeSettings("ActionLoopResume");
            NSUpload.uploadOpenAPSOffline(0);
            MainApp.bus().post(new EventRefreshOverview("ActionLoopResume"));
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
        } else {
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.notsuspended)).run();
        }
    }
}
