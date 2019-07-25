package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.queue.Callback;

public class ActionLoopResume extends Action {
    @Override
    public int friendlyName() {
        return R.string.resumeloop;
    }

    @Override
    public String shortDescription() {
        return MainApp.gs(R.string.resumeloop);
    }

    @Override
    public void doAction(Callback callback) {
        if (LoopPlugin.getPlugin().isSuspended()) {
            LoopPlugin.getPlugin().suspendTo(0);
            ConfigBuilderPlugin.getPlugin().storeSettings("ActionLoopResume");
            NSUpload.uploadOpenAPSOffline(0);
            RxBus.INSTANCE.send(new EventRefreshOverview("ActionLoopResume"));
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
        } else {
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.notsuspended)).run();
        }
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_replay_24dp);
    }
}
