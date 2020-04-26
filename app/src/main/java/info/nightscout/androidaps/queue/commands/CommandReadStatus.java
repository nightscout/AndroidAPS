package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.LocalAlertUtils;
import info.nightscout.androidaps.utils.T;

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
        ConfigBuilderPlugin.getPlugin().getActivePump().getPumpStatus();
        LocalAlertUtils.notifyPumpStatusRead();
        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("CommandReadStatus executed. Reason: " + reason);
        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        PumpEnactResult result = new PumpEnactResult().success(false);
        if (pump != null) {
            long lastConnection = pump.lastDataTime();
            if (lastConnection > System.currentTimeMillis() - T.mins(1).msecs())
                result.success(true);
        }
        if (callback != null)
            callback.result(result).run();
    }

    @Override
    public String status() {
        return "READSTATUS " + reason;
    }
}
