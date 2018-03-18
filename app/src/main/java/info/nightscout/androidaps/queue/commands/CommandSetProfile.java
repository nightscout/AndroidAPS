package info.nightscout.androidaps.queue.commands;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandSetProfile extends Command {
    private Profile profile;

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

        // Send SMS notification if ProfileSwitch is comming from NS
        ProfileSwitch profileSwitch = MainApp.getConfigBuilder().getProfileSwitchFromHistory(System.currentTimeMillis());
        if (r.enacted && profileSwitch.source == Source.NIGHTSCOUT) {
            SmsCommunicatorPlugin smsCommunicatorPlugin = MainApp.getSpecificPlugin(SmsCommunicatorPlugin.class);
            if (smsCommunicatorPlugin != null && smsCommunicatorPlugin.isEnabled(PluginBase.GENERAL)) {
                smsCommunicatorPlugin.sendNotificationToAllNumbers(MainApp.sResources.getString(R.string.profile_set_ok));
            }
        }
    }

    @Override
    public String status() {
        return "SETPROFILE";
    }
}
