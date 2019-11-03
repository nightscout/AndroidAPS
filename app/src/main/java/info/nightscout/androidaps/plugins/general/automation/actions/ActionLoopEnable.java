package info.nightscout.androidaps.plugins.general.automation.actions;

import com.google.common.base.Optional;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

public class ActionLoopEnable extends Action {
    @Override
    public int friendlyName() {
        return R.string.enableloop;
    }

    @Override
    public String shortDescription() {
        return MainApp.gs(R.string.enableloop);
    }

    @Override
    public void doAction(Callback callback) {
        if (!LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)) {
            LoopPlugin.getPlugin().setPluginEnabled(PluginType.LOOP, true);
            ConfigBuilderPlugin.getPlugin().storeSettings("ActionLoopEnable");
            RxBus.INSTANCE.send(new EventRefreshOverview("ActionLoopEnable"));
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.ok)).run();
        } else {
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).comment(R.string.alreadyenabled)).run();
        }
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_play_circle_outline_24dp);
    }
}
