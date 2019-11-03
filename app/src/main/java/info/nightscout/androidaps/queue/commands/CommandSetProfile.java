package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandSetProfile extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    private Profile profile;

    public CommandSetProfile(Profile profile, Callback callback) {
        commandType = CommandType.BASALPROFILE;
        this.profile = profile;
        this.callback = callback;
    }

    @Override
    public void execute() {
        if (ConfigBuilderPlugin.getPlugin().getCommandQueue().isThisProfileSet(profile)) {
            if (L.isEnabled(L.PUMPQUEUE))
                log.debug("Correct profile already set. profile: " + profile.toString());
            if (callback != null)
                callback.result(new PumpEnactResult().success(true).enacted(false)).run();
            return;
        }

        PumpEnactResult r = ConfigBuilderPlugin.getPlugin().getActivePump().setNewBasalProfile(profile);
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("Result success: " + r.success + " enacted: " + r.enacted + " profile: " + profile.toString());
        if (callback != null)
            callback.result(r).run();

        // Send SMS notification if ProfileSwitch is comming from NS
        ProfileSwitch profileSwitch = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(System.currentTimeMillis());
        if (profileSwitch != null && r.enacted && profileSwitch.source == Source.NIGHTSCOUT) {
            SmsCommunicatorPlugin smsCommunicatorPlugin = SmsCommunicatorPlugin.getPlugin();
            if (smsCommunicatorPlugin.isEnabled(PluginType.GENERAL)) {
                smsCommunicatorPlugin.sendNotificationToAllNumbers(MainApp.gs(R.string.profile_set_ok));
            }
        }
    }

    @Override
    public String status() {
        return "SETPROFILE";
    }
}
