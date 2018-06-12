package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.queue.Callback;

/**
 * Created by mike on 10.11.2017.
 */

public class CommandSetUserSettings extends Command {
    private static Logger log = LoggerFactory.getLogger(CommandSetUserSettings.class);
    public CommandSetUserSettings(Callback callback) {
        commandType = CommandType.SETUSERSETTINGS;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        if (pump instanceof DanaRInterface) {
            DanaRInterface danaPump = (DanaRInterface) pump;
            boolean isDanaRv2 = MainApp.getSpecificPlugin(DanaRv2Plugin.class) != null && MainApp.getSpecificPlugin(DanaRv2Plugin.class).isEnabled(PluginType.PUMP);
            if(isDanaRv2){
                log.debug("MsgSetUserOptions detected for DanaRv2");
            }
            PumpEnactResult r = danaPump.setUserOptions();
            if (callback != null)
                callback.result(r).run();
        }
    }

    @Override
    public String status() {
        return "SETUSERSETTINGS";
    }
}
