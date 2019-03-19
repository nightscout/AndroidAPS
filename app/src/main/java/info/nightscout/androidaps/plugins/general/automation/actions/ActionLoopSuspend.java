package info.nightscout.androidaps.plugins.general.automation.actions;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.queue.Callback;

public class ActionLoopSuspend extends Action {
    private int minutes;

    @Override
    public int friendlyName() {
        return R.string.suspendloop;
    }

    @Override
    void doAction(Callback callback) {
        if (!LoopPlugin.getPlugin().isSuspended()) {
            LoopPlugin.getPlugin().suspendLoop(minutes);
            MainApp.bus().post(new EventRefreshOverview("ActionLoopSuspend"));
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
        } else {
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.alreadysuspended)).run();
        }
    }
}
