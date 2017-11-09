package info.nightscout.androidaps.queue;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandSetProfile extends Command {
    Profile profile;

    CommandSetProfile(Profile profile, Callback callback) {
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
