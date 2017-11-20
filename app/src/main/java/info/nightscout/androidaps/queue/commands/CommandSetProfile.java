package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandSetProfile extends Command {
    Profile profile;

    public CommandSetProfile(Profile profile, Callback callback) {
        commandType = CommandType.BASALPROFILE;
        this.profile = profile;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r = ConfigBuilderPlugin.getActivePump().setNewBasalProfile(profile);
        if (callback != null)
            callback.result(r).run();
    }

    @Override
    public String status() {
        return "SETPROFILE";
    }
}
