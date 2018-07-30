package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.LocalAlertUtils;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandReadStatus extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    private String reason;

    public CommandReadStatus(String reason, Callback callback) {
        commandType = CommandType.READSTATUS;
        this.reason = reason;
        this.callback = callback;
    }

    @Override
    public void execute() {
        ConfigBuilderPlugin.getActivePump().getPumpStatus();
        LocalAlertUtils.notifyPumpStatusRead();
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("CommandReadStatus executed. Reason: " + reason);
        if (callback != null)
            callback.result(null).run();
    }

    @Override
    public String status() {
        return "READSTATUS " + reason;
    }
}
