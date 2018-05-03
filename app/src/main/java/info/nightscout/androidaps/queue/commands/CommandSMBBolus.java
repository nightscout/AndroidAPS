package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissBolusprogressIfRunning;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.T;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandSMBBolus extends Command {
    private static Logger log = LoggerFactory.getLogger(CommandSMBBolus.class);
    DetailedBolusInfo detailedBolusInfo;

    public CommandSMBBolus(DetailedBolusInfo detailedBolusInfo, Callback callback) {
        commandType = CommandType.SMB_BOLUS;
        this.detailedBolusInfo = detailedBolusInfo;
        this.callback = callback;
    }

    @Override
    public void execute() {
        PumpEnactResult r;
        long lastBolusTime = TreatmentsPlugin.getPlugin().getLastBolusTime();
        if (lastBolusTime != 0 && lastBolusTime + T.mins(3).msecs() > DateUtil.now()) {
            log.debug("SMB requsted but still in 3 min interval");
            r = new PumpEnactResult().enacted(false).success(false).comment("SMB requsted but still in 3 min interval");
        } else if (detailedBolusInfo.deliverAt != 0 && detailedBolusInfo.deliverAt + T.mins(1).msecs() > System.currentTimeMillis())
            r = ConfigBuilderPlugin.getActivePump().deliverTreatment(detailedBolusInfo);
        else {
            r = new PumpEnactResult().enacted(false).success(false).comment("SMB request too old");
            log.debug("SMB bolus canceled. delivetAt=" + detailedBolusInfo.deliverAt + " now=" + System.currentTimeMillis());
        }

        if (callback != null)
            callback.result(r).run();
    }

    public String status() {
        return "SMBBOLUS " + DecimalFormatter.to1Decimal(detailedBolusInfo.insulin) + "U";
    }
}
