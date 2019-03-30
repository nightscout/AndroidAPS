package info.nightscout.androidaps.queue.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 09.11.2017.
 */

public class CommandSMBBolus extends Command {
    private Logger log = LoggerFactory.getLogger(L.PUMPQUEUE);

    private DetailedBolusInfo detailedBolusInfo;

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
            if (L.isEnabled(L.PUMPQUEUE))
                log.debug("SMB requsted but still in 3 min interval");
            r = new PumpEnactResult().enacted(false).success(false).comment("SMB requsted but still in 3 min interval");
        } else if (detailedBolusInfo.deliverAt != 0 && detailedBolusInfo.deliverAt + T.mins(1).msecs() > System.currentTimeMillis()) {
            r = ConfigBuilderPlugin.getPlugin().getActivePump().deliverTreatment(detailedBolusInfo);
        } else {
            r = new PumpEnactResult().enacted(false).success(false).comment("SMB request too old");
            if (L.isEnabled(L.PUMPQUEUE))
                log.debug("SMB bolus canceled. delivetAt: " + DateUtil.dateAndTimeString(detailedBolusInfo.deliverAt));
        }

        if (L.isEnabled(L.PUMPQUEUE))
            log.debug("Result success: " + r.success + " enacted: " + r.enacted);

        if (callback != null)
            callback.result(r).run();
    }

    public String status() {
        return "SMBBOLUS " + DecimalFormatter.to2Decimal(detailedBolusInfo.insulin) + "U";
    }
}
