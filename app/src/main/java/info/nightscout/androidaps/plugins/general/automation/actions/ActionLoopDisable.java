package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.queue.Callback;

public class ActionLoopDisable extends Action {
    @Override
    public int friendlyName() {
        return R.string.disableloop;
    }

    @Override
    void doAction(Callback callback) {
        if (LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)) {
            LoopPlugin.getPlugin().setPluginEnabled(PluginType.LOOP, false);
            ConfigBuilderPlugin.getPlugin().storeSettings("ActionLoopDisable");
            ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    MainApp.bus().post(new EventRefreshOverview("ActionLoopDisable"));
                    if (callback != null)
                        callback.result(result).run();
                }
            });
        } else {
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.alreadydisabled)).run();
        }
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_stop_24dp);
    }
}
